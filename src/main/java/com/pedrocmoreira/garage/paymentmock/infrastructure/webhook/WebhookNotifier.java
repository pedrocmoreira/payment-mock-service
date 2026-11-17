package com.pedrocmoreira.garage.paymentmock.infrastructure.webhook;

import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Envia a notificação de pagamento para a notification_url da preferência, no mesmo formato do Mercado Pago:
 * POST {notification_url}?data.id={id}&type=payment com corpo {"action","type","data":{"id"}} e headers
 * x-signature / x-request-id. Faz novas tentativas com backoff em caso de falha.
 */
@Slf4j
public class WebhookNotifier {
    private final RestClient client;
    private final WebhookSigner signer;
    private final int maxAttempts;
    private final long backoffMs;

    public WebhookNotifier(RestClient client, WebhookSigner signer, int maxAttempts, long backoffMs) {
        this.client = client;
        this.signer = signer;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public boolean notify(MockPayment payment, String action) {
        String url = payment.getNotificationUrl();
        if (url == null || url.isBlank()) {
            log.info("Pagamento {} sem notification_url - webhook não enviado", payment.getId());
            return false;
        }
        String dataId = String.valueOf(payment.getId());
        String target = url + (url.contains("?") ? "&" : "?") + "data.id=" + dataId + "&type=payment";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", System.currentTimeMillis());
        body.put("live_mode", false);
        body.put("type", "payment");
        body.put("date_created", OffsetDateTime.now().toString());
        body.put("user_id", 123456789);
        body.put("api_version", "v1");
        body.put("action", action);
        body.put("data", Map.of("id", dataId));

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String requestId = UUID.randomUUID().toString();
            try {
                var request = client.post().uri(target).header("x-request-id", requestId).body(body);
                if (signer.isEnabled()) {
                    request = request.header("x-signature", signer.sign(dataId, requestId, Instant.now().getEpochSecond()));
                }
                int status = request.retrieve().toBodilessEntity().getStatusCode().value();
                payment.addNotification(new MockPayment.WebhookDelivery(action, attempt, status, null, OffsetDateTime.now()));
                log.info("Webhook {} do pagamento {} entregue (HTTP {}, tentativa {})", action, payment.getId(), status, attempt);
                return true;
            } catch (Exception e) {
                payment.addNotification(new MockPayment.WebhookDelivery(action, attempt, null, e.getMessage(), OffsetDateTime.now()));
                log.warn("Falha no webhook {} do pagamento {} (tentativa {}/{}): {}", action, payment.getId(), attempt, maxAttempts, e.getMessage());
                sleep(backoffMs * attempt);
            }
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
