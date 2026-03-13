package com.deanrobin.yx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "x")
public class XConfig {

    private Api api = new Api();
    private List<AccountConfig> accounts;

    @Data
    public static class Api {
        private String bearerToken;
        private int pollIntervalSeconds = 120;
    }

    @Data
    public static class AccountConfig {
        private String handle;
        private String name;
        private boolean enabled = true;
    }
}
