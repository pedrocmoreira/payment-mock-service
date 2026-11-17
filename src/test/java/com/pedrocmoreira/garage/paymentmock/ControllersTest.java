package com.pedrocmoreira.garage.paymentmock;

import com.pedrocmoreira.garage.paymentmock.application.CheckoutService;
import com.pedrocmoreira.garage.paymentmock.domain.MockStore;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookDispatcher;
import com.pedrocmoreira.garage.paymentmock.presentation.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllersTest {
    private static final String AUTH = "Bearer TEST-token";
    private static final String PREF_BODY = """
            {"external_reference":"5","notification_url":"http://billing/webhooks/mercadopago",
             "back_urls":{"success":"http://billing/ret","failure":"http://billing/ret"},
             "items":[{"id":"OS-1","title":"Ordem de serviço OS-1","quantity":1,"currency_id":"BRL","unit_price":302.0}]}
            """;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CheckoutService service = new CheckoutService(new MockStore(), mock(WebhookDispatcher.class), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8084");
        mvc = MockMvcBuilders.standaloneSetup(new MercadoPagoApiController(service), new CheckoutPageController(service),
                        new MockAdminController(service))
                .setControllerAdvice(new ApiErrorHandler())
                .addMappedInterceptors(new String[]{"/checkout/preferences/**", "/v1/**"}, new AccessTokenInterceptor("TEST-token"))
                .build();
    }

    private String createPreference() throws Exception {
        String json = mvc.perform(post("/checkout/preferences").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(PREF_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.init_point").value(containsString("/checkout/v1/redirect?pref_id=")))
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll("(?s)^\\{\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void apiRequiresAccessToken() throws Exception {
        mvc.perform(post("/checkout/preferences").contentType(MediaType.APPLICATION_JSON).content(PREF_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        mvc.perform(get("/v1/payments/1").header("Authorization", "Bearer errado")).andExpect(status().isUnauthorized());
    }

    @Test
    void fullCheckoutFlowThroughHttp() throws Exception {
        String prefId = createPreference();
        mvc.perform(get("/checkout/preferences/" + prefId).header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.external_reference").value("5"));

        mvc.perform(get("/checkout/v1/redirect").param("pref_id", prefId))
                .andExpect(status().isOk()).andExpect(content().string(containsString("APRO")));
        mvc.perform(get("/checkout/v1/pay").param("pref_id", prefId).param("outcome", "APRO"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pagamento aprovado")))
                .andExpect(content().string(containsString("collection_status=approved")));

        String payments = mvc.perform(get("/api/mock/payments")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("approved"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = payments.replaceAll("(?s)^\\[\\{\"id\":(\\d+).*", "$1");

        mvc.perform(get("/v1/payments/" + paymentId).header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transaction_amount").value(302.0));
        mvc.perform(post("/v1/payments/" + paymentId + "/refunds").header("Authorization", AUTH).header("X-Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(get("/v1/payments/" + paymentId).header("Authorization", AUTH)).andExpect(jsonPath("$.status").value("refunded"));
        mvc.perform(post("/api/mock/payments/" + paymentId + "/notify")).andExpect(status().isAccepted());
    }

    @Test
    void adminPayAndOtherOutcomes() throws Exception {
        String prefId = createPreference();
        mvc.perform(post("/api/mock/checkout/" + prefId + "/pay").contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"rejected\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status_detail").value("cc_rejected_insufficient_amount"));
        mvc.perform(post("/api/mock/checkout/" + prefId + "/pay")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(get("/checkout/v1/pay").param("pref_id", prefId).param("outcome", "CONT"))
                .andExpect(content().string(containsString("in_process")));
        mvc.perform(get("/checkout/v1/pay").param("pref_id", prefId).param("outcome", "OTHE"))
                .andExpect(content().string(containsString("rejected")));
        mvc.perform(post("/api/mock/checkout/" + prefId + "/pay").contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"???\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/checkout/v1/redirect").param("pref_id", "nao-existe")).andExpect(status().isNotFound());
        mvc.perform(get("/v1/payments/999").header("Authorization", AUTH)).andExpect(status().isNotFound());
    }

    @Test
    void checkoutWithoutBackUrls() throws Exception {
        String json = mvc.perform(post("/checkout/preferences").header("Authorization", AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"title\":\"X\",\"unit_price\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        String prefId = json.replaceAll("(?s)^\\{\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/checkout/v1/pay").param("pref_id", prefId))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Voltar"))));
    }
}
