package com.pedrocmoreira.garage.paymentmock.domain;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Armazenamento em memória, como um sandbox de provedor externo: o estado do "Mercado Pago"
 * não pertence ao domínio da oficina e é descartável (o Billing persiste o que importa).
 */
@Component
public class MockStore {
    private final Map<String, Preference> preferences = new ConcurrentHashMap<>();
    private final Map<Long, MockPayment> payments = new ConcurrentHashMap<>();
    private final Map<String, Object> idempotency = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1_300_000_000L);

    public long nextId() {
        return sequence.incrementAndGet();
    }

    public Preference save(Preference preference) {
        preferences.put(preference.getId(), preference);
        return preference;
    }

    public MockPayment save(MockPayment payment) {
        payments.put(payment.getId(), payment);
        return payment;
    }

    public Optional<Preference> preference(String id) {
        return Optional.ofNullable(preferences.get(id));
    }

    public Optional<MockPayment> payment(Long id) {
        return Optional.ofNullable(payments.get(id));
    }

    public List<MockPayment> payments() {
        List<MockPayment> list = new ArrayList<>(payments.values());
        list.sort(Comparator.comparing(MockPayment::getId).reversed());
        return list;
    }

    /** Respostas já devolvidas para um X-Idempotency-Key (mesma semântica da API real). */
    @SuppressWarnings("unchecked")
    public <T> T idempotent(String key, java.util.function.Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        return (T) idempotency.computeIfAbsent(key, k -> action.get());
    }
}
