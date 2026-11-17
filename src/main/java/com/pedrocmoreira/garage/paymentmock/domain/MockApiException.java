package com.pedrocmoreira.garage.paymentmock.domain;

import lombok.Getter;

/** Erro no formato da API do Mercado Pago: {"message","error","status","cause"}. */
@Getter
public class MockApiException extends RuntimeException {
    private final int status;
    private final String error;

    public MockApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static MockApiException notFound(String what) {
        return new MockApiException(404, "not_found", what + " not found");
    }

    public static MockApiException badRequest(String message) {
        return new MockApiException(400, "bad_request", message);
    }
}
