package com.pedrocmoreira.garage.paymentmock.presentation;

import com.pedrocmoreira.garage.paymentmock.application.CheckoutService;
import com.pedrocmoreira.garage.paymentmock.application.PaymentOutcome;
import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;
import com.pedrocmoreira.garage.paymentmock.domain.Preference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** Página de checkout (init_point) que o cliente abre no navegador. */
@RestController
@RequestMapping("/checkout/v1")
@RequiredArgsConstructor
@Tag(name = "Checkout (página do comprador)")
public class CheckoutPageController {
    private static final String HTML = "text/html;charset=UTF-8";
    private final CheckoutService service;

    @GetMapping(value = "/redirect", produces = HTML)
    @Operation(summary = "Página de pagamento da preferência")
    public String checkout(@RequestParam("pref_id") String preferenceId) {
        Preference p = service.preference(preferenceId);
        String base = "/checkout/v1/pay?pref_id=" + esc(preferenceId) + "&outcome=";
        return page("Mercado Pago (simulado)",
                "<p>" + esc(p.title()) + "</p><p class=\"amount\">R$ " + p.getTotalAmount() + "</p>"
                        + "<p class=\"hint\">Escolha o resultado (mesmos nomes dos cartões de teste do Mercado Pago):</p>"
                        + "<a class=\"btn ok\" href=\"" + base + "APRO\">Pagar - aprovado (APRO)</a>"
                        + "<a class=\"btn no\" href=\"" + base + "FUND\">Saldo insuficiente (FUND)</a>"
                        + "<a class=\"btn no\" href=\"" + base + "OTHE\">Recusado (OTHE)</a>"
                        + "<a class=\"btn wait\" href=\"" + base + "CONT\">Pendente (CONT)</a>");
    }

    @GetMapping(value = "/pay", produces = HTML)
    @Operation(summary = "Conclui o pagamento com o resultado escolhido e envia o webhook")
    public String pay(@RequestParam("pref_id") String preferenceId, @RequestParam(defaultValue = "APRO") String outcome) {
        Preference p = service.preference(preferenceId);
        MockPayment payment = service.pay(preferenceId, PaymentOutcome.parse(outcome));
        String back = backUrl(p, payment);
        String link = back == null ? "" : "<a class=\"btn\" href=\"" + esc(back) + "\">Voltar para a loja</a>";
        return page(MockPayment.APPROVED.equals(payment.getStatus()) ? "Pagamento aprovado" : "Pagamento " + payment.getStatus(),
                "<p>Pagamento <strong>" + payment.getId() + "</strong> - " + esc(payment.getStatusDetail()) + "</p>"
                        + "<p class=\"hint\">A notificação (webhook) foi enviada para a loja.</p>" + link);
    }

    static String backUrl(Preference p, MockPayment payment) {
        Map<String, Object> urls = p.getBackUrls();
        String key = switch (payment.getStatus()) {
            case MockPayment.APPROVED -> "success";
            case MockPayment.IN_PROCESS -> "pending";
            default -> "failure";
        };
        Object url = urls == null ? null : urls.get(key);
        if (url == null) return null;
        return UriComponentsBuilder.fromUriString(url.toString())
                .queryParam("collection_id", payment.getId())
                .queryParam("collection_status", payment.getStatus())
                .queryParam("payment_id", payment.getId())
                .queryParam("status", payment.getStatus())
                .queryParam("external_reference", payment.getExternalReference())
                .queryParam("preference_id", p.getId())
                .build().toUriString();
    }

    private static String esc(String v) {
        return v == null ? "" : HtmlUtils.htmlEscape(v, "UTF-8");
    }

    private static String page(String title, String content) {
        return """
                <!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/><title>%s</title>
                <style>
                  body{font-family:-apple-system,'Segoe UI',sans-serif;background:#ededed;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}
                  .card{background:#fff;border-radius:10px;padding:36px;max-width:440px;width:90%%;text-align:center;box-shadow:0 4px 16px rgba(0,0,0,.1)}
                  .logo{color:#009EE3;font-weight:700;font-size:20px;margin-bottom:8px}
                  .amount{font-size:30px;font-weight:700;margin:8px 0}.hint{color:#777;font-size:13px}
                  .btn{display:block;margin:10px 0;padding:12px;border-radius:6px;text-decoration:none;color:#fff;font-weight:600;background:#009EE3}
                  .ok{background:#00A650}.no{background:#F23D4F}.wait{background:#FF7733}
                </style></head><body><div class="card"><div class="logo">mercado pago · sandbox simulado</div>
                <h2>%s</h2>%s</div></body></html>
                """.formatted(esc(title), esc(title), content);
    }
}
