package com.deanrobin.yx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "twitterapiio")
public class TwitterApiIoConfig {
    private String apiKey;
}
