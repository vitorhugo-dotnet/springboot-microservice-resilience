package com.vitorhugo.orders.payments;

import java.math.BigDecimal;

public record PaymentAuthorizationRequest(String orderId, BigDecimal amount) {
}
