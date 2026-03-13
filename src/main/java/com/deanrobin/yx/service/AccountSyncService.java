package com.deanrobin.yx.service;

import com.deanrobin.yx.config.XConfig;
import com.deanrobin.yx.model.MonitoredAccount;
import com.deanrobin.yx.repository.MonitoredAccountRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSyncService {

    private final XConfig xConfig;
    private final MonitoredAccountRepository accountRepository;

    /**
     * 启动时将配置文件中的账户同步到数据库
     */
    @PostConstruct
    public void syncAccountsFromConfig() {
        if (xConfig.getAccounts() == null) return;
        for (XConfig.AccountConfig cfg : xConfig.getAccounts()) {
            accountRepository.findByHandle(cfg.getHandle()).ifPresentOrElse(
                    existing -> {
                        existing.setEnabled(cfg.isEnabled());
                        existing.setName(cfg.getName());
                        existing.setUpdatedAt(LocalDateTime.now());
                        accountRepository.save(existing);
                    },
                    () -> {
                        MonitoredAccount account = new MonitoredAccount();
                        account.setHandle(cfg.getHandle());
                        account.setName(cfg.getName());
                        account.setEnabled(cfg.isEnabled());
                        accountRepository.save(account);
                        log.info("Added monitored account: @{}", cfg.getHandle());
                    }
            );
        }
    }
}
