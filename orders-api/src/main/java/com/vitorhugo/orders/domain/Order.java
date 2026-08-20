package com.vitorhugo.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(
        String id,
        String customerId,
        BigDecimal amount,
        OrderStatus status,
        String authorizationId,
        String reason,
        Instant createdAt) {

    public Order withStatus(OrderStatus novoStatus, String authorizationId, String reason) {
        return new Order(id, customerId, amount, novoStatus, authorizationId, reason, createdAt);
    }
}
