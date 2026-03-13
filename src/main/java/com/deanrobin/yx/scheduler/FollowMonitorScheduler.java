package com.deanrobin.yx.scheduler;

import com.deanrobin.yx.model.FollowRecord;
import com.deanrobin.yx.model.FollowWatcher;
import com.deanrobin.yx.notify.NotifyMessage;
import com.deanrobin.yx.repository.FollowRecordRepository;
import com.deanrobin.yx.repository.FollowWatcherRepository;
import com.deanrobin.yx.service.NotifyDispatcher;
import com.deanrobin.yx.service.XApiService;
import com.deanrobin.yx.service.XFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 每分钟轮询监控账户的关注列表，检测新增关注和取消关注事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowMonitorScheduler {

    private final FollowWatcherRepository watcherRepository;
    private final FollowRecordRepository followRecordRepository;
    private final XFollowService xFollowService;
    private final XApiService xApiService;
    private final NotifyDispatcher notifyDispatcher;

    private static final int RATE_LIMIT_WARN_THRESHOLD = 15;

    @Scheduled(fixedDelay = 60_000) // 每 1 分钟
    public void checkFollowChanges() {
        List<FollowWatcher> watchers = watcherRepository.findByEnabledTrue();
        if (watchers.isEmpty()) return;

        // 超过 15 个账户时发警告（X API 限制：15次/15分钟）
        if (watchers.size() > RATE_LIMIT_WARN_THRESHOLD) {
            log.warn("⚠️ Follow watcher count ({}) exceeds X API rate limit (15/15min). Consider increasing poll interval.", watchers.size());
            NotifyMessage warn = NotifyMessage.builder()
                    .tweetId("sys-warn-follow-ratelimit-" + System.currentTimeMillis())
                    .handle("system")
                    .displayName("系统提示")
                    .content(String.format(
                            "⚠️ 当前关注监控账户数量为 %d 个，超过 X API 频率限制（15次/15分钟）。\n建议将轮询间隔从 1 分钟调整为 %d 分钟以上，避免触发限流。\n可修改 FollowMonitorScheduler 中的 fixedDelay 参数。",
                            watchers.size(),
                            (watchers.size() / 15) + 1
                    ))
                    .tweetUrl("")
                    .tweetTime(LocalDateTime.now().toString())
                    .build();
            notifyDispatcher.dispatch(warn);
        }

        log.debug("Checking follow changes for {} watcher(s)...", watchers.size());

        for (FollowWatcher watcher : watchers) {
            try {
                processWatcher(watcher);
            } catch (Exception e) {
                log.error("Error checking follow for @{}: {}", watcher.getHandle(), e.getMessage());
            }
        }
    }

    private void processWatcher(FollowWatcher watcher) {
        // 确保有 userId
        if (watcher.getXUserId() == null || watcher.getXUserId().isBlank()) {
            String userId = xApiService.getUserId(watcher.getHandle());
            if (userId == null) {
                log.warn("Cannot resolve userId for @{}", watcher.getHandle());
                return;
            }
            watcher.setXUserId(userId);
            watcherRepository.save(watcher);
        }

        // 从 X API 拉取当前关注列表
        List<XFollowService.FollowingUser> currentFollowing = xFollowService.getFollowing(watcher.getXUserId());
        Map<String, XFollowService.FollowingUser> currentMap = currentFollowing.stream()
                .collect(Collectors.toMap(
                        u -> u.getUsername().toLowerCase(),
                        u -> u,
                        (a, b) -> a
                ));

        // 从 DB 拉取已记录的关注列表
        List<FollowRecord> dbRecords = followRecordRepository.findByWatcherHandleAndStatus(
                watcher.getHandle(), "active"
        );
        Map<String, FollowRecord> dbMap = dbRecords.stream()
                .collect(Collectors.toMap(
                        r -> r.getFollowingHandle().toLowerCase(),
                        r -> r,
                        (a, b) -> a
                ));

        // 首次初始化：DB 为空时直接存快照，不发通知
        if (dbMap.isEmpty() && !currentMap.isEmpty()) {
            log.info("Initializing follow snapshot for @{}: {} following", watcher.getHandle(), currentMap.size());
            saveSnapshot(watcher.getHandle(), currentMap);
            updateLastChecked(watcher);
            return;
        }

        // 检测新增关注
        for (Map.Entry<String, XFollowService.FollowingUser> entry : currentMap.entrySet()) {
            if (!dbMap.containsKey(entry.getKey())) {
                handleNewFollow(watcher, entry.getValue());
            }
        }

        // 检测取消关注
        for (Map.Entry<String, FollowRecord> entry : dbMap.entrySet()) {
            if (!currentMap.containsKey(entry.getKey())) {
                handleUnfollow(watcher, entry.getValue());
            }
        }

        updateLastChecked(watcher);
    }

    private void handleNewFollow(FollowWatcher watcher, XFollowService.FollowingUser followed) {
        log.info("🟢 @{} followed @{}", watcher.getHandle(), followed.getUsername());

        // 存记录
        FollowRecord record = followRecordRepository
                .findByWatcherHandleAndFollowingHandle(watcher.getHandle(), followed.getUsername())
                .orElse(new FollowRecord());
        record.setWatcherHandle(watcher.getHandle());
        record.setFollowingHandle(followed.getUsername());
        record.setFollowingName(followed.getName());
        record.setFollowingUserId(followed.getId());
        record.setStatus("active");
        record.setFollowedAt(LocalDateTime.now());
        record.setUnfollowedAt(null);
        record.setUpdatedAt(LocalDateTime.now());
        followRecordRepository.save(record);

        // 发通知
        String tweetId = "follow-" + watcher.getHandle() + "-" + followed.getUsername();
        NotifyMessage message = NotifyMessage.builder()
                .tweetId(tweetId)
                .handle(watcher.getHandle())
                .displayName(watcher.getName() != null ? watcher.getName() : watcher.getHandle())
                .content(String.format("➕ 新增关注了 %s (@%s)\n🔗 https://x.com/%s",
                        followed.getName(), followed.getUsername(), followed.getUsername()))
                .tweetUrl("https://x.com/" + watcher.getHandle() + "/following")
                .tweetTime(LocalDateTime.now().toString())
                .build();
        notifyDispatcher.dispatch(message);
    }

    private void handleUnfollow(FollowWatcher watcher, FollowRecord record) {
        log.info("🔴 @{} unfollowed @{}", watcher.getHandle(), record.getFollowingHandle());

        // 更新记录状态
        record.setStatus("unfollowed");
        record.setUnfollowedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        followRecordRepository.save(record);

        // 发通知
        String tweetId = "unfollow-" + watcher.getHandle() + "-" + record.getFollowingHandle();
        NotifyMessage message = NotifyMessage.builder()
                .tweetId(tweetId)
                .handle(watcher.getHandle())
                .displayName(watcher.getName() != null ? watcher.getName() : watcher.getHandle())
                .content(String.format("➖ 取消关注了 %s (@%s)\n🔗 https://x.com/%s",
                        record.getFollowingName(), record.getFollowingHandle(), record.getFollowingHandle()))
                .tweetUrl("https://x.com/" + watcher.getHandle() + "/following")
                .tweetTime(LocalDateTime.now().toString())
                .build();
        notifyDispatcher.dispatch(message);
    }

    private void saveSnapshot(String watcherHandle, Map<String, XFollowService.FollowingUser> currentMap) {
        for (XFollowService.FollowingUser user : currentMap.values()) {
            FollowRecord record = new FollowRecord();
            record.setWatcherHandle(watcherHandle);
            record.setFollowingHandle(user.getUsername());
            record.setFollowingName(user.getName());
            record.setFollowingUserId(user.getId());
            record.setStatus("active");
            record.setFollowedAt(LocalDateTime.now());
            followRecordRepository.save(record);
        }
    }

    private void updateLastChecked(FollowWatcher watcher) {
        watcher.setLastCheckedAt(LocalDateTime.now());
        watcherRepository.save(watcher);
    }
}
