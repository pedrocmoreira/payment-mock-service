package com.pedrocmoreira.garage.paymentmock.infrastructure.webhook;

import com.pedrocmoreira.garage.paymentmock.domain.MockPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Envio assíncrono: o comprador vê a confirmação do checkout sem esperar o webhook (como no provedor real). */
@Component
@RequiredArgsConstructor
public class WebhookDispatcher {
    private final WebhookNotifier notifier;

    @Async
    public void dispatch(MockPayment payment, String action) {
        notifier.notify(payment, action);
    }
}
