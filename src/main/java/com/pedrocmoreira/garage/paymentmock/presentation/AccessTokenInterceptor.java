package com.pedrocmoreira.garage.paymentmock.presentation;

import com.pedrocmoreira.garage.paymentmock.domain.MockApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** Exige "Authorization: Bearer {access_token}" nas rotas de API, como o Mercado Pago. */
public class AccessTokenInterceptor implements HandlerInterceptor {
    private final String accessToken;

    public AccessTokenInterceptor(String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.equals("Bearer " + accessToken)) {
            throw new MockApiException(401, "unauthorized", "invalid access token");
        }
        return true;
    }
}
