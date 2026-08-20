package com.vitorhugo.orders.observability;

/** Nome do header e da chave de MDC usados para correlacionar logs. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }
}
