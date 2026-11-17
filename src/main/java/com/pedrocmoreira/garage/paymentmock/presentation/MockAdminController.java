package com.pedrocmoreira.garage.paymentmock.presentation;

import com.pedrocmoreira.garage.paymentmock.application.CheckoutService;
import com.pedrocmoreira.garage.paymentmock.application.PaymentOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Endpoints auxiliares do simulador (automação de testes, Postman e demonstração). */
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@Tag(name = "Simulador", description = "Não existem no Mercado Pago real - apoio a testes/demonstração")
public class MockAdminController {
    private final CheckoutService service;

    public record PayRequest(String outcome) {}

    @PostMapping("/checkout/{preferenceId}/pay")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Paga a preferência via API (outcome: APRO, FUND, OTHE, CONT ou approved/rejected/pending)")
    public Map<String, Object> pay(@PathVariable String preferenceId, @RequestBody(required = false) PayRequest request) {
        return service.pay(preferenceId, PaymentOutcome.parse(request == null ? null : request.outcome())).toJson();
    }

    @GetMapping("/payments")
    @Operation(summary = "Lista pagamentos e entregas de webhook")
    public List<Map<String, Object>> payments() {
        return service.payments().stream().map(p -> {
            Map<String, Object> json = p.toJson();
            json.put("notifications", p.getNotifications());
            return json;
        }).toList();
    }

    @PostMapping("/payments/{id}/notify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenvia o webhook de um pagamento")
    public void resend(@PathVariable Long id) {
        service.resendNotification(id);
    }
}
