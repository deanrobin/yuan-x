package com.deanrobin.yx.service;

import com.deanrobin.yx.model.TweetRecord;
import com.deanrobin.yx.notify.NotifyMessage;
import com.deanrobin.yx.repository.MonitoredAccountRepository;
import com.deanrobin.yx.repository.TweetRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 连接 X Filtered Stream，实时监听推文事件。
 * 自动重连，指数退避策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XStreamService {

    private final BearerTokenProvider bearerTokenProvider;
    private final XStreamRuleService ruleService;
    private final TweetRecordRepository tweetRecordRepository;
    private final MonitoredAccountRepository accountRepository;
    private final NotifyDispatcher notifyDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STREAM_URL =
            "https://api.twitter.com/2/tweets/search/stream?tweet.fields=created_at,author_id,text&expansions=author_id&user.fields=username,name";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private Thread streamThread;
    private int retryDelay = 5; // 初始重试延迟（秒）

    @PostConstruct
    public void start() {
        executor.submit(() -> {
            try {
                log.info(">>> 同步 Stream 过滤规则...");
                ruleService.syncRulesFromDb();
            } catch (Exception e) {
                log.error("❌ Stream 规则同步失败: {}", e.getMessage());
            }
            connect();
        });
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
        if (streamThread != null) streamThread.interrupt();
        log.info("X Stream stopped.");
    }

    private void connect() {
        running.set(true);
        streamThread = Thread.currentThread();

        while (running.get()) {
            log.info("Connecting to X Filtered Stream...");
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(STREAM_URL))
                        .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<java.io.InputStream> response = client.send(
                        request, HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() == 200) {
                    log.info("✅ X Stream 连接成功，实时监听中...");
                    retryDelay = 5;
                    readStream(response.body());
                } else if (response.statusCode() == 429) {
                    log.warn("⚠️ X Stream 触发限流，等待 60s 后重试");
                    sleep(60);
                } else {
                    log.error("❌ X Stream 连接失败: HTTP {}", response.statusCode());
                    backoffAndRetry();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("❌ X Stream 异常: {}", e.getMessage());
                backoffAndRetry();
            }
        }
    }

    private void readStream(java.io.InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    // 心跳包（空行），忽略
                    continue;
                }
                handleEvent(line);
            }
        } catch (Exception e) {
            if (running.get()) {
                log.warn("⚠️ X Stream 断开: {}，准备重连...", e.getMessage());
            }
        }
    }

    private void handleEvent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return;

            String tweetId = data.path("id").asText();
            String text = data.path("text").asText();
            String createdAt = data.path("created_at").asText();
            String authorId = data.path("author_id").asText();

            // 从 includes.users 里找 username
            String handle = authorId;
            String displayName = authorId;
            JsonNode users = root.path("includes").path("users");
            if (users.isArray()) {
                for (JsonNode user : users) {
                    if (authorId.equals(user.path("id").asText())) {
                        handle = user.path("username").asText();
                        displayName = user.path("name").asText();
                        break;
                    }
                }
            }

            // 去重
            if (tweetRecordRepository.existsByTweetId(tweetId)) {
                return;
            }

            log.info("🐦 新推文 @{}: {}", handle, text.substring(0, Math.min(60, text.length())));

            String tweetUrl = "https://x.com/" + handle + "/status/" + tweetId;

            // 存记录
            TweetRecord record = new TweetRecord();
            record.setTweetId(tweetId);
            record.setHandle(handle);
            record.setContent(text);
            record.setTweetUrl(tweetUrl);
            record.setTweetTime(LocalDateTime.now());

            // 发通知
            NotifyMessage message = NotifyMessage.builder()
                    .tweetId(tweetId)
                    .handle(handle)
                    .displayName(displayName)
                    .content(text)
                    .tweetUrl(tweetUrl)
                    .tweetTime(createdAt)
                    .build();

            notifyDispatcher.dispatch(message);
            record.setNotified(true);
            tweetRecordRepository.save(record);

            // 更新账户最新 tweet_id
            accountRepository.findByHandle(handle).ifPresent(account -> {
                account.setLastTweetId(tweetId);
                account.setUpdatedAt(LocalDateTime.now());
                accountRepository.save(account);
            });

        } catch (Exception e) {
            log.error("Failed to handle stream event: {}", e.getMessage());
        }
    }

    private void backoffAndRetry() {
        log.info("Retrying in {}s...", retryDelay);
        sleep(retryDelay);
        retryDelay = Math.min(retryDelay * 2, 300); // 最长等 5 分钟
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
