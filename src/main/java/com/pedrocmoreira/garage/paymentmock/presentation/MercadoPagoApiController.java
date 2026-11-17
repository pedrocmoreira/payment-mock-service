package com.pedrocmoreira.garage.paymentmock.presentation;

import com.pedrocmoreira.garage.paymentmock.application.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Mesmos caminhos e formatos da API pública do Mercado Pago (subconjunto usado pelo Billing). */
@RestController
@RequiredArgsConstructor
@Tag(name = "API Mercado Pago (simulada)", description = "Requer Authorization: Bearer {MOCK_ACCESS_TOKEN}")
public class MercadoPagoApiController {
    private final CheckoutService service;

    @PostMapping("/checkout/preferences")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar preferência (Checkout Pro)")
    public Map<String, Object> createPreference(@RequestBody Map<String, Object> body) {
        return service.createPreference(body).toJson();
    }

    @GetMapping("/checkout/preferences/{id}")
    @Operation(summary = "Consultar preferência")
    public Map<String, Object> getPreference(@PathVariable String id) {
        return service.preference(id).toJson();
    }

    @GetMapping("/v1/payments/{id}")
    @Operation(summary = "Consultar pagamento")
    public Map<String, Object> getPayment(@PathVariable Long id) {
        return service.payment(id).toJson();
    }

    @PostMapping("/v1/payments/{id}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Estornar pagamento (total)")
    public Map<String, Object> refund(@PathVariable Long id,
                                      @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return service.refund(id, idempotencyKey);
    }
}
