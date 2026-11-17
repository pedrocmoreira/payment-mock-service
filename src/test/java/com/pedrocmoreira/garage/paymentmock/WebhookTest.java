package com.pedrocmoreira.garage.paymentmock;

import com.pedrocmoreira.garage.paymentmock.application.PaymentOutcome;
import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;
import com.pedrocmoreira.garage.paymentmock.domain.Preference;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookDispatcher;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookNotifier;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookSigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebhookTest {

    static MockPayment payment(String notificationUrl) {
        Preference p = Preference.builder().id("pref-1").externalReference("5").notificationUrl(notificationUrl)
                .backUrls(Map.of()).items(List.of()).payer(Map.of()).totalAmount(new BigDecimal("10"))
                .initPoint("x").dateCreated(OffsetDateTime.now()).build();
        return new MockPayment(1300000001L, p, PaymentOutcome.APRO.status(), PaymentOutcome.APRO.detail());
    }

    @Test
    void signerMatchesMercadoPagoManifest() throws Exception {
        WebhookSigner signer = new WebhookSigner("segredo");
        String header = signer.sign("ABC", "req-1", 1700000000L);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("segredo".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal("id:abc;request-id:req-1;ts:1700000000;".getBytes(StandardCharsets.UTF_8)));
        assertThat(header).isEqualTo("ts=1700000000,v1=" + expected);
        assertThat(new WebhookSigner("").isEnabled()).isFalse();
    }

    @Test
    void notifierPostsSignedNotification() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebhookNotifier notifier = new WebhookNotifier(builder.build(), new WebhookSigner("segredo"), 3, 0);
        server.expect(requestTo("http://billing:8082/webhooks/mercadopago?data.id=1300000001&type=payment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-signature", org.hamcrest.Matchers.startsWith("ts=")))
                .andExpect(jsonPath("$.type").value("payment"))
                .andExpect(jsonPath("$.action").value("payment.created"))
                .andExpect(jsonPath("$.data.id").value("1300000001"))
                .andRespond(withSuccess());

        MockPayment payment = payment("http://billing:8082/webhooks/mercadopago");
        assertThat(notifier.notify(payment, "payment.created")).isTrue();
        assertThat(payment.getNotifications()).hasSize(1).first().extracting("httpStatus").isEqualTo(200);
        server.verify();
    }

    @Test
    void notifierRetriesAndGivesUp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WebhookNotifier notifier = new WebhookNotifier(builder.build(), new WebhookSigner(""), 2, 0);
        server.expect(times(2), requestTo("http://billing/hook?x=1&data.id=1300000001&type=payment")).andRespond(withServerError());

        MockPayment payment = payment("http://billing/hook?x=1");
        assertThat(notifier.notify(payment, "payment.created")).isFalse();
        assertThat(payment.getNotifications()).hasSize(2).allSatisfy(d -> assertThat(d.error()).isNotNull());
        server.verify();
    }

    @Test
    void notifierSkipsWithoutUrlAndDispatcherDelegates() {
        WebhookNotifier notifier = new WebhookNotifier(RestClient.create(), new WebhookSigner(""), 1, 0);
        assertThat(notifier.notify(payment(null), "payment.created")).isFalse();
        assertThat(notifier.notify(payment(" "), "payment.created")).isFalse();

        WebhookNotifier mockNotifier = mock(WebhookNotifier.class);
        MockPayment p = payment("http://x");
        new WebhookDispatcher(mockNotifier).dispatch(p, "payment.created");
        verify(mockNotifier).notify(p, "payment.created");
    }
}
