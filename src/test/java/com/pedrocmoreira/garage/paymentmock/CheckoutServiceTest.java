package com.pedrocmoreira.garage.paymentmock;

import com.pedrocmoreira.garage.paymentmock.application.CheckoutService;
import com.pedrocmoreira.garage.paymentmock.application.PaymentOutcome;
import com.pedrocmoreira.garage.paymentmock.domain.MockApiException;
import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;
import com.pedrocmoreira.garage.paymentmock.domain.MockStore;
import com.pedrocmoreira.garage.paymentmock.domain.Preference;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookDispatcher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutServiceTest {
    private WebhookDispatcher dispatcher;
    private CheckoutService service;

    static Map<String, Object> preferenceBody() {
        return Map.of(
                "external_reference", "5",
                "notification_url", "http://billing/webhooks/mercadopago",
                "back_urls", Map.of("success", "http://billing/ok", "failure", "http://billing/fail", "pending", "http://billing/pending"),
                "items", List.of(Map.of("id", "OS-1", "title", "Ordem de serviço OS-1", "quantity", 2, "unit_price", 151.0)));
    }

    @BeforeEach
    void setUp() {
        dispatcher = mock(WebhookDispatcher.class);
        service = new CheckoutService(new MockStore(), dispatcher, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8084");
    }

    @Test
    void createPreferenceComputesTotalAndInitPoint() {
        Preference p = service.createPreference(preferenceBody());
        assertThat(p.getTotalAmount()).isEqualByComparingTo("302.0");
        assertThat(p.getInitPoint()).isEqualTo("http://localhost:8084/checkout/v1/redirect?pref_id=" + p.getId());
        assertThat(p.toJson()).containsEntry("external_reference", "5").containsKeys("sandbox_init_point", "date_created");
        assertThat(p.title()).isEqualTo("Ordem de serviço OS-1");
        assertThat(service.preference(p.getId())).isSameAs(p);
    }

    @Test
    void createPreferenceValidatesItems() {
        assertThatThrownBy(() -> service.createPreference(Map.of())).isInstanceOf(MockApiException.class);
        assertThatThrownBy(() -> service.createPreference(Map.of("items", List.of(Map.of("title", "x")))))
                .hasMessageContaining("unit_price");
        assertThatThrownBy(() -> service.preference("x")).isInstanceOf(MockApiException.class)
                .extracting("status").isEqualTo(404);
    }

    @Test
    void payApprovedSendsWebhookAndCanBeRefundedOnce() {
        Preference p = service.createPreference(preferenceBody());
        MockPayment payment = service.pay(p.getId(), PaymentOutcome.APRO);

        assertThat(payment.getStatus()).isEqualTo("approved");
        assertThat(payment.getDateApproved()).isNotNull();
        assertThat(payment.toJson()).containsEntry("external_reference", "5").containsEntry("status_detail", "accredited");
        verify(dispatcher).dispatch(payment, "payment.created");
        assertThat(service.payment(payment.getId())).isSameAs(payment);
        assertThat(service.payments()).containsExactly(payment);

        Map<String, Object> refund = service.refund(payment.getId(), "key-1");
        assertThat(refund).containsEntry("status", "approved");
        assertThat(service.refund(payment.getId(), "key-1")).isSameAs(refund); // idempotente
        assertThat(payment.getStatus()).isEqualTo("refunded");
        verify(dispatcher).dispatch(payment, "payment.updated");

        assertThatThrownBy(() -> service.refund(payment.getId(), null)).hasMessageContaining("cannot be refunded");
    }

    @Test
    void rejectedPaymentAndResend() {
        Preference p = service.createPreference(preferenceBody());
        MockPayment payment = service.pay(p.getId(), PaymentOutcome.FUND);
        assertThat(payment.getStatus()).isEqualTo("rejected");
        assertThat(payment.getStatusDetail()).isEqualTo("cc_rejected_insufficient_amount");
        assertThat(payment.getDateApproved()).isNull();
        service.resendNotification(payment.getId());
        verify(dispatcher).dispatch(payment, "payment.updated");
        assertThatThrownBy(() -> service.payment(1L)).isInstanceOf(MockApiException.class);
    }

    @Test
    void outcomeParsing() {
        assertThat(PaymentOutcome.parse(null)).isEqualTo(PaymentOutcome.APRO);
        assertThat(PaymentOutcome.parse("othe")).isEqualTo(PaymentOutcome.OTHE);
        assertThat(PaymentOutcome.parse("approved")).isEqualTo(PaymentOutcome.APRO);
        assertThat(PaymentOutcome.parse("rejected")).isEqualTo(PaymentOutcome.FUND);
        assertThat(PaymentOutcome.parse("pending")).isEqualTo(PaymentOutcome.CONT);
        assertThat(PaymentOutcome.parse("in_process").status()).isEqualTo("in_process");
        assertThatThrownBy(() -> PaymentOutcome.parse("xyz")).isInstanceOf(MockApiException.class);
    }
}
