package com.deanrobin.yx.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class XApiService {

    private final BearerTokenProvider bearerTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 根据 handle 获取 X user_id
     */
    public String getUserId(String handle) {
        try {
            String url = "https://api.twitter.com/2/users/by/username/" + handle;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("data").path("id").asText(null);
            } else {
                log.warn("⚠️ 获取 userId 失败 @{}: HTTP {}", handle, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("⚠️ 获取 userId 异常 @{}: {}", handle, e.getMessage());
        }
        return null;
    }

    /**
     * 获取用户最新推文列表（最多 10 条），sinceId 用于增量拉取
     */
    public List<TweetDto> getLatestTweets(String userId, String sinceId) {
        List<TweetDto> tweets = new ArrayList<>();
        try {
            StringBuilder url = new StringBuilder(
                    "https://api.twitter.com/2/users/" + userId + "/tweets"
                    + "?max_results=10&tweet.fields=created_at,text&exclude=retweets,replies"
            );
            if (sinceId != null && !sinceId.isBlank()) {
                url.append("&since_id=").append(sinceId);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");
                if (data.isArray()) {
                    for (JsonNode tweet : data) {
                        TweetDto dto = new TweetDto();
                        dto.setId(tweet.path("id").asText());
                        dto.setText(tweet.path("text").asText());
                        dto.setCreatedAt(tweet.path("created_at").asText());
                        tweets.add(dto);
                    }
                }
            } else if (response.statusCode() == 429) {
                log.warn("⚠️ X API 限流 userId={}", userId);
            } else {
                log.warn("⚠️ X API 错误 userId={}: HTTP {}", userId, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("⚠️ 拉取推文异常 userId={}: {}", userId, e.getMessage());
        }
        return tweets;
    }

    /**
     * Tweet 数据传输对象
     */
    @lombok.Data
    public static class TweetDto {
        private String id;
        private String text;
        private String createdAt;
    }
}
