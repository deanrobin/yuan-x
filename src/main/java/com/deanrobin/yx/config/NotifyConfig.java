package com.deanrobin.yx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notify")
public class NotifyConfig {

    private Lark lark = new Lark();
    private Telegram telegram = new Telegram();
    private Qq qq = new Qq();

    @Data
    public static class Lark {
        private boolean enabled = true;
        private String webhookUrl;
    }

    @Data
    public static class Telegram {
        private boolean enabled = false;
        private String botToken;
        private String chatId;
    }

    @Data
    public static class Qq {
        private boolean enabled = false;
        private String webhookUrl;
    }
}
