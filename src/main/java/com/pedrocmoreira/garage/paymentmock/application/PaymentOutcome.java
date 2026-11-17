package com.pedrocmoreira.garage.paymentmock.application;

import com.pedrocmoreira.garage.paymentmock.domain.MockApiException;
import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;

/**
 * Resultados simulados, inspirados nos nomes de titular dos cartões de teste do Mercado Pago
 * (APRO = aprovado, OTHE = recusado, CONT = pendente).
 */
public enum PaymentOutcome {
    APRO(MockPayment.APPROVED, "accredited"),
    OTHE(MockPayment.REJECTED, "cc_rejected_other_reason"),
    FUND(MockPayment.REJECTED, "cc_rejected_insufficient_amount"),
    CONT(MockPayment.IN_PROCESS, "pending_contingency");

    private final String status;
    private final String detail;

    PaymentOutcome(String status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public String status() { return status; }
    public String detail() { return detail; }

    /** Aceita o código (APRO/OTHE/FUND/CONT) ou o status (approved/rejected/in_process). */
    public static PaymentOutcome parse(String value) {
        if (value == null || value.isBlank()) return APRO;
        String v = value.trim();
        for (PaymentOutcome o : values()) {
            if (o.name().equalsIgnoreCase(v)) return o;
        }
        return switch (v.toLowerCase()) {
            case "approved" -> APRO;
            case "rejected" -> FUND;
            case "in_process", "pending" -> CONT;
            default -> throw MockApiException.badRequest("Resultado de pagamento inválido: " + value);
        };
    }
}
