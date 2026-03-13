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
 * 推文轮询调度器（Free 版方案）
 * 每 15 分钟轮询一次，2 账户约 5,760 次/月，在 Free 版 10,000 次限额内
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
                processAccount(account);
            } catch (Exception e) {
                log.warn("⚠️ [推文轮询] 处理 @{} 异常（已跳过）: {}", account.getHandle(), e.getMessage());
            }
        }
    }

    private void processAccount(MonitoredAccount account) {
        List<TwitterApiIoService.TweetDto> tweets = twitterApiIoService.getLatestTweets(
                account.getHandle(), account.getLastTweetId()
        );

        if (tweets.isEmpty()) {
            log.debug("[推文轮询] @{} 无新推文", account.getHandle());
            return;
        }

        log.info("🐦 @{} 发现 {} 条新推文", account.getHandle(), tweets.size());

        // 从旧到新处理
        for (int i = tweets.size() - 1; i >= 0; i--) {
            TwitterApiIoService.TweetDto tweet = tweets.get(i);
            if (tweetRecordRepository.existsByTweetId(tweet.getId())) continue;

            LocalDateTime detectedAt = LocalDateTime.now();
            String tweetUrl = tweet.getTweetUrl() != null && !tweet.getTweetUrl().isBlank()
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
