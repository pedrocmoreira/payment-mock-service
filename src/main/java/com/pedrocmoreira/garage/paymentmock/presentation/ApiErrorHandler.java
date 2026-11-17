package com.pedrocmoreira.garage.paymentmock.presentation;

import com.pedrocmoreira.garage.paymentmock.domain.MockApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(MockApiException.class)
    public ResponseEntity<Map<String, Object>> handle(MockApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("error", ex.getError());
        body.put("status", ex.getStatus());
        body.put("cause", List.of());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
