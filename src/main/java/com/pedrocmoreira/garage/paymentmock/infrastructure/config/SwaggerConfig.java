package com.pedrocmoreira.garage.paymentmock.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("Payment Mock Service - simulador do Mercado Pago")
                .description("Imita os endpoints do Mercado Pago usados pelo Billing: preferências, pagamentos, estornos e webhooks assinados")
                .version("1.0.0"));
    }
}
