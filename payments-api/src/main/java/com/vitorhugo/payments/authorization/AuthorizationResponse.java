package com.vitorhugo.payments.authorization;

import java.math.BigDecimal;

public record AuthorizationResponse(
        String authorizationId,
        String orderId,
        BigDecimal amount,
        String status) {
}
