package com.vitorhugo.orders.api;

import java.math.BigDecimal;

import com.vitorhugo.orders.domain.Order;

public record OrderResponse(
        String id,
        String status,
        BigDecimal amount,
        String authorizationId,
        String reason) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.status().name(),
                order.amount(),
                order.authorizationId(),
                order.reason());
    }
}
