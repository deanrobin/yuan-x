package com.deanrobin.yx.scheduler;

import com.deanrobin.yx.model.MonitoredAccount;
import com.deanrobin.yx.model.TweetRecord;
import com.deanrobin.yx.notify.NotifyMessage;
import com.deanrobin.yx.repository.MonitoredAccountRepository;
import com.deanrobin.yx.repository.TweetRecordRepository;
import com.deanrobin.yx.service.NotifyDispatcher;
import com.deanrobin.yx.service.TwitterApiIoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推文轮询调度器
 * - lastTweetId == null（首次）：静默快照最新 ID，不发通知
 * - lastTweetId 已有：只拉 sinceId 之后的新推文，有新的才通知
 * - 重启安全：lastTweetId 持久化在 DB，重启后不会重复通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TweetPollScheduler {

    private final MonitoredAccountRepository accountRepository;
    private final TweetRecordRepository tweetRecordRepository;
    private final TwitterApiIoService twitterApiIoService;
    private final NotifyDispatcher notifyDispatcher;

    @Scheduled(fixedDelayString = "${x.api.poll-interval-seconds:900}000")
    public void poll() {
        List<MonitoredAccount> accounts = accountRepository.findByEnabledTrue();
        if (accounts.isEmpty()) return;

        log.info(">>> [推文轮询] 开始，共 {} 个账户", accounts.size());
        for (MonitoredAccount account : accounts) {
            try {
                Thread.sleep(6000); // 账户间间隔 6s（twitterapi.io 限流约 5s/次）
                processAccount(account);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("⚠️ [推文轮询] 处理 @{} 异常，5s 后重试: {}", account.getHandle(), e.getMessage());
                try {
                    Thread.sleep(5000);
                    processAccount(account);
                } catch (InterruptedException ie2) {
                    Thread.currentThread().interrupt();
                } catch (Exception retryEx) {
                    log.warn("⚠️ [推文轮询] @{} 重试仍失败（已跳过本轮）: {}", account.getHandle(), retryEx.getMessage());
                }
            }
        }
    }

    private void processAccount(MonitoredAccount account) {
        boolean isFirstRun = (account.getLastTweetId() == null || account.getLastTweetId().isBlank());

        List<TwitterApiIoService.TweetDto> tweets = twitterApiIoService.getLatestTweets(
                account.getHandle(), account.getLastTweetId()
        );

        if (tweets.isEmpty()) {
            log.debug("[推文轮询] @{} 无新推文", account.getHandle());
            return;
        }

        // 首次运行：静默快照，只记录最新 ID，不发通知
        if (isFirstRun) {
            String latestId = tweets.get(0).getId();
            account.setLastTweetId(latestId);
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);
            log.info("📸 [推文轮询] @{} 首次快照，记录最新推文 ID: {}，不发通知", account.getHandle(), latestId);
            return;
        }

        // 正常轮询：从旧到新处理，只通知真正的新推文
        log.info("🐦 @{} 发现 {} 条新推文", account.getHandle(), tweets.size());
        for (int i = tweets.size() - 1; i >= 0; i--) {
            TwitterApiIoService.TweetDto tweet = tweets.get(i);
            if (tweetRecordRepository.existsByTweetId(tweet.getId())) continue;

            LocalDateTime detectedAt = LocalDateTime.now();
            String tweetUrl = (tweet.getTweetUrl() != null && !tweet.getTweetUrl().isBlank())
                    ? tweet.getTweetUrl()
                    : "https://x.com/" + account.getHandle() + "/status/" + tweet.getId();

            TweetRecord record = new TweetRecord();
            record.setTweetId(tweet.getId());
            record.setHandle(account.getHandle());
            record.setContent(tweet.getText());
            record.setTweetUrl(tweetUrl);
            record.setTweetTime(LocalDateTime.now());
            record.setDetectedAt(detectedAt);

            NotifyMessage message = NotifyMessage.builder()
                    .tweetId(tweet.getId())
                    .handle(account.getHandle())
                    .displayName(account.getName() != null ? account.getName() : account.getHandle())
                    .content(tweet.getText())
                    .tweetUrl(tweetUrl)
                    .tweetTime(tweet.getCreatedAt())
                    .build();

            boolean sent = notifyDispatcher.dispatch(message);
            record.setNotified(sent);
            if (sent) record.setNotifiedAt(LocalDateTime.now());
            tweetRecordRepository.save(record);
        }

        account.setLastTweetId(tweets.get(0).getId());
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }
}
