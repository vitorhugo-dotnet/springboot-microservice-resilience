package com.vitorhugo.orders.api;

/**
 * Retrato instantaneo das politicas. Serve para acompanhar uma demonstracao sem
 * precisar garimpar o /actuator/prometheus.
 */
public record ResilienceStatusResponse(
        String circuitBreakerState,
        float failureRatePercentage,
        int bufferedCalls,
        int successfulCalls,
        int failedCalls,
        long notPermittedCalls,
        int bulkheadAvailableConcurrentCalls,
        int bulkheadMaxConcurrentCalls,
        int pendingQueueSize) {
}
