package com.deanrobin.yx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "x")
public class XConfig {

    private Api api = new Api();

    @Data
    public static class Api {
        private String apiKey;
        private String apiSecret;
        private String bearerToken;
        private int pollIntervalSeconds = 900;
    }
}
