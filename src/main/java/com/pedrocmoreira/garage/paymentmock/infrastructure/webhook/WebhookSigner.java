package com.pedrocmoreira.garage.paymentmock.infrastructure.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Gera o header x-signature igual ao Mercado Pago:
 * {@code ts=<epoch>,v1=HMAC_SHA256(secret, "id:<data.id>;request-id:<x-request-id>;ts:<ts>;")}.
 */
public class WebhookSigner {
    private final String secret;

    public WebhookSigner(String secret) {
        this.secret = secret;
    }

    public boolean isEnabled() {
        return secret != null && !secret.isBlank();
    }

    public String sign(String dataId, String requestId, long ts) {
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar webhook", e);
        }
    }
}
