package com.deanrobin.yx.service;

import com.deanrobin.yx.config.TwitterApiIoConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * twitterapi.io 推文拉取服务
 * 文档：https://docs.twitterapi.io
 * 计费：$0.15/1K tweets，最低 15 credits/次
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwitterApiIoService {

    private final TwitterApiIoConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();

    private static final String BASE_URL = "https://api.twitterapi.io";

    /**
     * 获取用户最新推文（按时间倒序，最多 20 条）
     * 使用 sinceId 增量拉取，避免重复
     */
    public List<TweetDto> getLatestTweets(String userName, String sinceId) {
        List<TweetDto> result = new ArrayList<>();
        try {
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/twitter/user/last_tweets?userName=")
                    .append(URLEncoder.encode(userName, StandardCharsets.UTF_8));

            if (sinceId != null && !sinceId.isBlank()) {
                url.append("&sinceId=").append(sinceId);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("X-API-Key", config.getApiKey())
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                // 响应结构：{ status, data: { tweets: [...] } }，按时间倒序（最新在前）
                JsonNode tweets = root.path("data").path("tweets");
                if (tweets.isArray()) {
                    for (JsonNode tweet : tweets) {
                        String tweetId = tweet.path("id").asText();
                        // sinceId 参数在 twitterapi.io 不生效，在此手动过滤旧推文
                        if (sinceId != null && !sinceId.isBlank()) {
                            // 推文 ID 是雪花 ID，数值越大越新；跳过 ID ≤ sinceId 的推文
                            try {
                                if (Long.parseUnsignedLong(tweetId) <= Long.parseUnsignedLong(sinceId)) {
                                    break; // 剩余的更旧，直接停止
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                        TweetDto dto = new TweetDto();
                        dto.setId(tweetId);
                        dto.setText(tweet.path("text").asText());
                        dto.setCreatedAt(tweet.path("createdAt").asText());
                        dto.setTweetUrl(tweet.path("url").asText());
                        result.add(dto);
                    }
                }
                log.debug("[twitterapi.io] @{} 过滤后新推文 {} 条（sinceId={}）", userName, result.size(), sinceId);
            } else if (response.statusCode() == 429) {
                log.warn("⚠️ [twitterapi.io] 限流 @{}", userName);
            } else {
                log.warn("⚠️ [twitterapi.io] HTTP {} @{}: {}", response.statusCode(), userName, response.body());
            }
        } catch (Exception e) {
            log.warn("⚠️ [twitterapi.io] 请求异常 @{}（{}）: {}", userName, e.getClass().getSimpleName(), e.getMessage());
        }
        return result;
    }

    @Data
    public static class TweetDto {
        private String id;
        private String text;
        private String createdAt;
        private String tweetUrl;
    }
}
