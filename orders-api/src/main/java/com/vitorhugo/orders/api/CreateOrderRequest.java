package com.vitorhugo.orders.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotNull @Positive BigDecimal amount) {
}
