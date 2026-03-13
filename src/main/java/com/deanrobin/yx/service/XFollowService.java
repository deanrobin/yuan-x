package com.deanrobin.yx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用 X API 获取用户的关注列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XFollowService {

    private final BearerTokenProvider bearerTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 获取用户的完整关注列表（自动翻页，最多 1000 条）
     */
    public List<FollowingUser> getFollowing(String userId) {
        List<FollowingUser> result = new ArrayList<>();
        String paginationToken = null;

        do {
            try {
                StringBuilder url = new StringBuilder(
                        "https://api.twitter.com/2/users/" + userId
                        + "/following?max_results=1000&user.fields=username,name"
                );
                if (paginationToken != null) {
                    url.append("&pagination_token=").append(paginationToken);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url.toString()))
                        .header("Authorization", "Bearer " + bearerTokenProvider.getBearerToken())
                        .GET().build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode data = root.path("data");
                    if (data.isArray()) {
                        for (JsonNode user : data) {
                            FollowingUser fu = new FollowingUser();
                            fu.setId(user.path("id").asText());
                            fu.setUsername(user.path("username").asText());
                            fu.setName(user.path("name").asText());
                            result.add(fu);
                        }
                    }
                    // 翻页 token
                    JsonNode meta = root.path("meta");
                    paginationToken = meta.has("next_token") ? meta.path("next_token").asText(null) : null;

                } else if (response.statusCode() == 429) {
                    log.warn("⚠️ [关注列表] X API 限流 userId={}", userId);
                    break;
                } else {
                    log.warn("⚠️ [关注列表] HTTP {} userId={}", response.statusCode(), userId);
                    break;
                }
            } catch (Exception e) {
                log.warn("⚠️ [关注列表] 请求异常 userId={}: {}", userId, e.getMessage());
                break;
            }
        } while (paginationToken != null);

        return result;
    }

    @Data
    public static class FollowingUser {
        private String id;
        private String username;
        private String name;
    }
}
