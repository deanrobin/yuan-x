package com.deanrobin.yx.service;

import com.deanrobin.yx.config.XConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * 启动时用 API Key + Secret 换取 Bearer Token，缓存到 JVM 内存。
 * 敏感信息不落盘，不写入任何文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BearerTokenProvider {

    private final XConfig xConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Getter
    private String bearerToken;

    @PostConstruct
    public void init() {
        // 优先：直接配置的 Bearer Token（application-local.yml 中 x.api.bearer-token）
        String configToken = xConfig.getApi().getBearerToken();
        if (configToken != null && !configToken.isBlank()) {
            this.bearerToken = configToken;
            log.info("✅ Bearer Token 已从配置文件加载到 JVM 内存");
            return;
        }
        // 降级：用 API Key+Secret 换取（需 Basic 及以上权限）
        log.info(">>> bearer-token 未配置，尝试用 Key/Secret 换取...");
        this.bearerToken = fetchBearerToken();
        if (this.bearerToken != null) {
            log.info("✅ Bearer Token 获取成功，已加载到 JVM 内存");
        } else {
            log.error("❌ Bearer Token 获取失败，请检查 X_BEARER_TOKEN / X_API_KEY / X_API_SECRET 环境变量");
        }
    }

    private String fetchBearerToken() {
        try {
            String apiKey = xConfig.getApi().getApiKey();
            String apiSecret = xConfig.getApi().getApiSecret();

            String credentials = Base64.getEncoder().encodeToString(
                    (apiKey + ":" + apiSecret).getBytes()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twitter.com/oauth2/token"))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("access_token").asText(null);
            } else {
                log.error("❌ Bearer Token 请求失败: HTTP {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("❌ Bearer Token 请求异常: {}", e.getMessage());
        }
        return null;
    }
}
