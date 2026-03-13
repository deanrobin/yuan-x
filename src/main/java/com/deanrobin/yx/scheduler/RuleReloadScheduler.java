package com.deanrobin.yx.scheduler;

import com.deanrobin.yx.service.XStreamRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每 5 分钟从数据库重新加载监控账户，检测变化后更新 X Stream 过滤规则。
 * X Stream 长连接保持不变，规则更新后立即生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleReloadScheduler {

    private final XStreamRuleService ruleService;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // 每 5 分钟
    public void reloadRules() {
        log.debug("Checking for account list changes...");
        try {
            boolean updated = ruleService.syncRulesFromDb();
            if (updated) {
                log.info("✅ Stream rules updated from DB.");
            }
        } catch (Exception e) {
            log.error("Failed to reload stream rules: {}", e.getMessage());
        }
    }
}
