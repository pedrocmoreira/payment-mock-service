package com.pedrocmoreira.garage.paymentmock.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pagamento no formato de GET /v1/payments/{id}. */
@Getter
@Setter
public class MockPayment {
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String IN_PROCESS = "in_process";
    public static final String REFUNDED = "refunded";

    private final Long id;
    private final String preferenceId;
    private final String externalReference;
    private final String notificationUrl;
    private final BigDecimal transactionAmount;
    private final OffsetDateTime dateCreated;
    private String status;
    private String statusDetail;
    private OffsetDateTime dateApproved;
    private OffsetDateTime dateLastUpdated;
    private BigDecimal amountRefunded = BigDecimal.ZERO;
    private final List<Map<String, Object>> refunds = new ArrayList<>();
    private final List<WebhookDelivery> notifications = new ArrayList<>();

    public MockPayment(Long id, Preference preference, String status, String statusDetail) {
        this.id = id;
        this.preferenceId = preference.getId();
        this.externalReference = preference.getExternalReference();
        this.notificationUrl = preference.getNotificationUrl();
        this.transactionAmount = preference.getTotalAmount();
        this.dateCreated = OffsetDateTime.now();
        this.dateLastUpdated = dateCreated;
        this.status = status;
        this.statusDetail = statusDetail;
        if (APPROVED.equals(status)) {
            this.dateApproved = dateCreated;
        }
    }

    public record WebhookDelivery(String action, int attempt, Integer httpStatus, String error, OffsetDateTime at) {}

    public synchronized Map<String, Object> refund(Long refundId) {
        if (!APPROVED.equals(status)) {
            throw MockApiException.badRequest("Payment " + id + " cannot be refunded (status " + status + ")");
        }
        Map<String, Object> refund = new LinkedHashMap<>();
        refund.put("id", refundId);
        refund.put("payment_id", id);
        refund.put("amount", transactionAmount);
        refund.put("status", "approved");
        refund.put("date_created", OffsetDateTime.now().toString());
        refunds.add(refund);
        amountRefunded = transactionAmount;
        status = REFUNDED;
        statusDetail = REFUNDED;
        dateLastUpdated = OffsetDateTime.now();
        return refund;
    }

    public synchronized void addNotification(WebhookDelivery delivery) {
        notifications.add(delivery);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("status", status);
        json.put("status_detail", statusDetail);
        json.put("external_reference", externalReference);
        json.put("transaction_amount", transactionAmount);
        json.put("transaction_amount_refunded", amountRefunded);
        json.put("currency_id", "BRL");
        json.put("payment_method_id", "master");
        json.put("payment_type_id", "credit_card");
        json.put("live_mode", false);
        json.put("date_created", dateCreated.toString());
        json.put("date_approved", dateApproved == null ? null : dateApproved.toString());
        json.put("date_last_updated", dateLastUpdated.toString());
        json.put("metadata", Map.of("preference_id", preferenceId));
        json.put("refunds", refunds);
        return json;
    }
}
