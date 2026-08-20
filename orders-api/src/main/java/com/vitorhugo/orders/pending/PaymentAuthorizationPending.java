package com.vitorhugo.orders.pending;

import java.math.BigDecimal;
import java.time.Instant;

/** Autorizacao que precisa ser tentada de novo quando o pagamento voltar. */
public record PaymentAuthorizationPending(
        String orderId,
        BigDecimal amount,
        String reason,
        Instant enqueuedAt) {
}
