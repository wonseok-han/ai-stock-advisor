package com.nowini.feedback.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class ResendClient {

    private static final Logger log = LoggerFactory.getLogger(ResendClient.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String contactEmail;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ResendClient(
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.contact-email:}") String contactEmail,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.contactEmail = contactEmail;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank()
                && contactEmail != null && !contactEmail.isBlank();
    }

    public void sendFeedbackNotification(String type, String subject, String body,
                                         String senderEmail) {
        if (!isEnabled()) {
            log.warn("resend not configured, skipping feedback email");
            return;
        }

        try {
            String typeLabel = switch (type) {
                case "bug" -> "버그 신고";
                case "question" -> "문의";
                case "suggestion" -> "제안";
                default -> type;
            };

            String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                  <h2 style="color: #10b981;">새로운 피드백이 도착했습니다</h2>
                  <div style="background-color: #f3f4f6; padding: 20px; border-radius: 8px; margin: 20px 0;">
                    <p><strong>유형:</strong> %s</p>
                    <p><strong>이메일:</strong> %s</p>
                    <p><strong>제목:</strong> %s</p>
                  </div>
                  <div style="background-color: #ffffff; padding: 20px; border: 1px solid #e5e7eb; border-radius: 8px;">
                    <h3 style="color: #374151; margin-top: 0;">피드백 내용:</h3>
                    <p style="color: #6b7280; line-height: 1.6; white-space: pre-wrap;">%s</p>
                  </div>
                  <div style="margin-top: 20px; padding: 15px; background-color: #ecfdf5; border-left: 4px solid #10b981; border-radius: 4px;">
                    <p style="margin: 0; color: #065f46; font-size: 14px;">
                      답장하려면 이 이메일에 직접 답장하시면 됩니다.
                    </p>
                  </div>
                </div>
                """.formatted(typeLabel, senderEmail, escapeHtml(subject), escapeHtml(body));

            Map<String, Object> payload = Map.of(
                    "from", "지금이니?! Feedback <onboarding@resend.dev>",
                    "to", new String[]{contactEmail},
                    "reply_to", senderEmail,
                    "subject", "[지금이니?! 피드백] " + typeLabel + ": " + subject,
                    "html", html
            );

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("feedback email sent to {}", contactEmail);
            } else {
                log.warn("resend api returned {} : {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("failed to send feedback email", e);
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
