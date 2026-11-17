package com.pedrocmoreira.garage.paymentmock.domain;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preferência de pagamento (Checkout Pro). */
@Getter
@Builder
public class Preference {
    private final String id;
    private final String externalReference;
    private final String notificationUrl;
    private final Map<String, Object> backUrls;
    private final List<Map<String, Object>> items;
    private final Map<String, Object> payer;
    private final BigDecimal totalAmount;
    private final String initPoint;
    private final OffsetDateTime dateCreated;

    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("collector_id", 123456789);
        json.put("external_reference", externalReference);
        json.put("notification_url", notificationUrl);
        json.put("back_urls", backUrls);
        json.put("items", items);
        json.put("payer", payer);
        json.put("init_point", initPoint);
        json.put("sandbox_init_point", initPoint);
        json.put("date_created", dateCreated.toString());
        return json;
    }

    public String title() {
        return items.isEmpty() ? "Pagamento" : String.valueOf(items.get(0).get("title"));
    }
}
