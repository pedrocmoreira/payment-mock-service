package com.pedrocmoreira.garage.paymentmock.infrastructure.config;

import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookNotifier;
import com.pedrocmoreira.garage.paymentmock.infrastructure.webhook.WebhookSigner;
import com.pedrocmoreira.garage.paymentmock.presentation.AccessTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MockConfig implements WebMvcConfigurer {
    @Value("${mock.access-token}")
    private String accessToken;

    @Bean
    WebhookNotifier webhookNotifier(RestClient.Builder builder,
                                    @Value("${mock.webhook.secret:}") String secret,
                                    @Value("${mock.webhook.max-attempts:3}") int maxAttempts,
                                    @Value("${mock.webhook.backoff-ms:1000}") long backoffMs) {
        return new WebhookNotifier(builder.build(), new WebhookSigner(secret), maxAttempts, backoffMs);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessTokenInterceptor(accessToken))
                .addPathPatterns("/checkout/preferences/**", "/v1/**");
    }
}
