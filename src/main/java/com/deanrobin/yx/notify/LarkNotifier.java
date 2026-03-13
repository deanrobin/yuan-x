package com.deanrobin.yx.notify;

import com.deanrobin.yx.config.NotifyConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
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
            long timestamp = System.currentTimeMillis() / 1000;

            Map<String, Object> body = new HashMap<>();
            body.put("msg_type", "text");
            body.put("content", Map.of("text", text));

            // 签名校验（如果配置了 signSecret）
            String signSecret = notifyConfig.getLark().getSignSecret();
            if (signSecret != null && !signSecret.isBlank()) {
                String sign = generateSign(timestamp, signSecret);
                body.put("timestamp", String.valueOf(timestamp));
                body.put("sign", sign);
            }

            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(notifyConfig.getLark().getWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("📨 [Lark] 消息发送成功 | @{} | {}", message.getHandle(), message.getTweetId());
            } else {
                log.warn("⚠️ [Lark] 发送失败: HTTP {} | {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("⚠️ [Lark] 发送异常: {}", e.getMessage());
        }
    }

    /**
     * 飞书签名算法：HMAC-SHA256(timestamp + "\n" + secret) → Base64
     */
    private String generateSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(), "HmacSHA256"));
        byte[] signBytes = mac.doFinal(new byte[0]);
        return Base64.getEncoder().encodeToString(signBytes);
    }

    private static final DateTimeFormatter API_TIME_FMT =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss xx yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 将 twitterapi.io 返回的时间字符串（UTC）转换为上海时间显示
     * 输入示例：Fri Mar 13 07:48:59 +0000 2026
     * 输出示例：2026-03-13 15:48:59 (UTC+8)
     */
    private String formatTweetTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) return "";
        try {
            ZonedDateTime utc = ZonedDateTime.parse(rawTime.trim(), API_TIME_FMT);
            return utc.withZoneSameInstant(SHANGHAI).format(DISPLAY_FMT) + " (UTC+8)";
        } catch (Exception e) {
            return rawTime; // 解析失败原样返回
        }
    }

    private String buildText(NotifyMessage msg) {
        return String.format(
                "【X消息】%s (@%s) 发推了！\n\n%s\n\n⏰ %s\n🔗 %s",
                msg.getDisplayName(),
                msg.getHandle(),
                msg.getContent(),
                formatTweetTime(msg.getTweetTime()),
                msg.getTweetUrl()
        );
    }
}
