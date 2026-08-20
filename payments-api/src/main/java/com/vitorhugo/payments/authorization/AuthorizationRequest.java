package com.vitorhugo.payments.authorization;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AuthorizationRequest(
        @NotBlank String orderId,
        @NotNull @Positive BigDecimal amount) {
}
