package com.pedrocmoreira.garage.paymentmock.application;

import com.pedrocmoreira.garage.paymentmock.domain.MockApiException;
import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;
import com.pedrocmoreira.garage.paymentmock.domain.MockStore;
import com.pedrocmoreira.garage.paymentmock.domain.Preference;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Regras do simulador: criar preferência, pagar no checkout, consultar e estornar. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final MockStore store;
    private final WebhookDispatcher webhookDispatcher;
    private final MeterRegistry meterRegistry;

    @Value("${mock.public-base-url}")
    private String publicBaseUrl;

    @SuppressWarnings("unchecked")
    public Preference createPreference(Map<String, Object> body) {
        Object rawItems = body.get("items");
        if (!(rawItems instanceof List<?> list) || list.isEmpty()) {
            throw MockApiException.badRequest("items is required");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            Object price = item.get("unit_price");
            Object qty = item.getOrDefault("quantity", 1);
            if (price == null) {
                throw MockApiException.badRequest("items.unit_price is required");
            }
            total = total.add(new BigDecimal(price.toString()).multiply(new BigDecimal(qty.toString())));
        }
        String id = "123456789-" + UUID.randomUUID();
        Preference preference = Preference.builder()
                .id(id)
                .externalReference((String) body.get("external_reference"))
                .notificationUrl((String) body.get("notification_url"))
                .backUrls((Map<String, Object>) body.getOrDefault("back_urls", Map.of()))
                .payer((Map<String, Object>) body.getOrDefault("payer", Map.of()))
                .items(items)
                .totalAmount(total)
                .initPoint(publicBaseUrl + "/checkout/v1/redirect?pref_id=" + id)
                .dateCreated(OffsetDateTime.now())
                .build();
        meterRegistry.counter("mock.mercadopago.preferences.created").increment();
        log.info("Preferência {} criada (external_reference={}, total={})", id, preference.getExternalReference(), total);
        return store.save(preference);
    }

    public Preference preference(String id) {
        return store.preference(id).orElseThrow(() -> MockApiException.notFound("Preference " + id));
    }

    /** Simula o comprador concluindo o checkout. Dispara o webhook "payment.created". */
    public MockPayment pay(String preferenceId, PaymentOutcome outcome) {
        Preference preference = preference(preferenceId);
        MockPayment payment = store.save(new MockPayment(store.nextId(), preference, outcome.status(), outcome.detail()));
        meterRegistry.counter("mock.mercadopago.payments", "status", payment.getStatus()).increment();
        log.info("Pagamento {} {} ({}) para preferência {}", payment.getId(), payment.getStatus(), payment.getStatusDetail(), preferenceId);
        webhookDispatcher.dispatch(payment, "payment.created");
        return payment;
    }

    public MockPayment payment(Long id) {
        return store.payment(id).orElseThrow(() -> MockApiException.notFound("Payment " + id));
    }

    public List<MockPayment> payments() {
        return store.payments();
    }

    /** POST /v1/payments/{id}/refunds — estorno total, idempotente por X-Idempotency-Key. */
    public Map<String, Object> refund(Long paymentId, String idempotencyKey) {
        return store.idempotent(idempotencyKey, () -> {
            MockPayment payment = payment(paymentId);
            Map<String, Object> refund = payment.refund(store.nextId());
            meterRegistry.counter("mock.mercadopago.refunds").increment();
            log.info("Pagamento {} estornado", paymentId);
            webhookDispatcher.dispatch(payment, "payment.updated");
            return refund;
        });
    }

    public void resendNotification(Long paymentId) {
        webhookDispatcher.dispatch(payment(paymentId), "payment.updated");
    }
}
