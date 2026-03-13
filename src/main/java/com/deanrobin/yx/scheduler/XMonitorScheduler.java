package com.deanrobin.yx.scheduler;

import com.deanrobin.yx.model.MonitoredAccount;
import com.deanrobin.yx.model.TweetRecord;
import com.deanrobin.yx.notify.NotifyMessage;
import com.deanrobin.yx.repository.MonitoredAccountRepository;
import com.deanrobin.yx.repository.TweetRecordRepository;
import com.deanrobin.yx.service.NotifyDispatcher;
import com.deanrobin.yx.service.XApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class XMonitorScheduler {

    private final MonitoredAccountRepository accountRepository;
    private final TweetRecordRepository tweetRecordRepository;
    private final XApiService xApiService;
    private final NotifyDispatcher notifyDispatcher;

    /**
     * 定时轮询，间隔通过配置控制（默认 120 秒）
     */
    @Scheduled(fixedDelayString = "${x.api.poll-interval-seconds:120}000")
    public void poll() {
        List<MonitoredAccount> accounts = accountRepository.findByEnabledTrue();
        log.info("Polling {} monitored accounts...", accounts.size());

        for (MonitoredAccount account : accounts) {
            try {
                processAccount(account);
            } catch (Exception e) {
                log.error("Error processing account @{}: {}", account.getHandle(), e.getMessage());
            }
        }
    }

    private void processAccount(MonitoredAccount account) {
        // 确保有 userId
        if (account.getXUserId() == null || account.getXUserId().isBlank()) {
            String userId = xApiService.getUserId(account.getHandle());
            if (userId == null) {
                log.warn("Cannot resolve userId for @{}", account.getHandle());
                return;
            }
            account.setXUserId(userId);
            accountRepository.save(account);
        }

        // 拉取新推文
        List<XApiService.TweetDto> tweets = xApiService.getLatestTweets(
                account.getXUserId(), account.getLastTweetId()
        );

        if (tweets.isEmpty()) {
            log.debug("No new tweets for @{}", account.getHandle());
            return;
        }

        log.info("Found {} new tweet(s) from @{}", tweets.size(), account.getHandle());

        // 从旧到新处理（API 返回最新在前，反转处理）
        for (int i = tweets.size() - 1; i >= 0; i--) {
            XApiService.TweetDto tweet = tweets.get(i);
            if (tweetRecordRepository.existsByTweetId(tweet.getId())) {
                continue;
            }

            // 存储推文记录
            TweetRecord record = new TweetRecord();
            record.setTweetId(tweet.getId());
            record.setHandle(account.getHandle());
            record.setContent(tweet.getText());
            record.setTweetUrl("https://x.com/" + account.getHandle() + "/status/" + tweet.getId());
            record.setTweetTime(LocalDateTime.now());
            record.setNotified(false);

            // 发送通知
            NotifyMessage message = NotifyMessage.builder()
                    .tweetId(tweet.getId())
                    .handle(account.getHandle())
                    .displayName(account.getName() != null ? account.getName() : account.getHandle())
                    .content(tweet.getText())
                    .tweetUrl(record.getTweetUrl())
                    .tweetTime(tweet.getCreatedAt())
                    .build();

            notifyDispatcher.dispatch(message);
            record.setNotified(true);
            tweetRecordRepository.save(record);
        }

        // 更新最新 tweet_id
        account.setLastTweetId(tweets.get(0).getId());
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }
}
