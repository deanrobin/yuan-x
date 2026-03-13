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
        try {
            boolean updated = ruleService.syncRulesFromDb();
            if (updated) {
                log.info("🔄 [规则刷新] Stream 规则已更新");
            } else {
                log.debug("[规则刷新] 账户列表无变化，跳过");
            }
        } catch (Exception e) {
            log.error("❌ [规则刷新] 失败: {}", e.getMessage());
        }
    }
}
