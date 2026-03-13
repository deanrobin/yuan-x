package com.deanrobin.yx.notify;

import com.deanrobin.yx.config.NotifyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LarkNotifier implements Notifier {

    private final NotifyConfig notifyConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public boolean isEnabled() {
        return notifyConfig.getLark().isEnabled()
                && notifyConfig.getLark().getWebhookUrl() != null
                && !notifyConfig.getLark().getWebhookUrl().isBlank();
    }

    @Override
    public void send(NotifyMessage message) {
        try {
            String text = buildText(message);
            Map<String, Object> body = Map.of(
                    "msg_type", "text",
                    "content", Map.of("text", text)
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(notifyConfig.getLark().getWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[Lark] Notified: {}", message.getTweetId());
            } else {
                log.warn("[Lark] Failed: HTTP {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[Lark] Error sending notification: {}", e.getMessage());
        }
    }

    private String buildText(NotifyMessage msg) {
        return String.format(
                "🐦 【X 监控】%s (@%s) 发推了！\n\n%s\n\n⏰ %s\n🔗 %s",
                msg.getDisplayName(),
                msg.getHandle(),
                msg.getContent(),
                msg.getTweetTime(),
                msg.getTweetUrl()
        );
    }
}
