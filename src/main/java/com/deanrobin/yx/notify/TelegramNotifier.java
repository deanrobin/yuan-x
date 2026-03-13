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
public class TelegramNotifier implements Notifier {

    private final NotifyConfig notifyConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public boolean isEnabled() {
        return notifyConfig.getTelegram().isEnabled()
                && notifyConfig.getTelegram().getBotToken() != null
                && !notifyConfig.getTelegram().getBotToken().isBlank();
    }

    @Override
    public void send(NotifyMessage message) {
        try {
            String text = buildText(message);
            String url = "https://api.telegram.org/bot" + notifyConfig.getTelegram().getBotToken() + "/sendMessage";

            Map<String, Object> body = Map.of(
                    "chat_id", notifyConfig.getTelegram().getChatId(),
                    "text", text,
                    "disable_web_page_preview", false
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[Telegram] Notified: {}", message.getTweetId());
            } else {
                log.warn("[Telegram] Failed: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("⚠️ [Telegram] 发送异常: {}", e.getMessage());
        }
    }

    private String buildText(NotifyMessage msg) {
        return String.format(
                "🐦 【X 监控】%s (@%s) 发推了！\n\n%s\n\n⏰ %s\n🔗 %s",
                msg.getDisplayName(), msg.getHandle(),
                msg.getContent(), msg.getTweetTime(), msg.getTweetUrl()
        );
    }
}
