package com.vitorhugo.orders.api;

import com.vitorhugo.orders.pending.PendingAuthorizationQueue;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resilience")
public class ResilienceStatusController {

    private static final String INSTANCIA = "payments";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final PendingAuthorizationQueue pendingQueue;

    public ResilienceStatusController(CircuitBreakerRegistry circuitBreakerRegistry,
                                      BulkheadRegistry bulkheadRegistry,
                                      PendingAuthorizationQueue pendingQueue) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.pendingQueue = pendingQueue;
    }

    @GetMapping("/status")
    public ResilienceStatusResponse status() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCIA);
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        Bulkhead.Metrics bulkhead = bulkheadRegistry.bulkhead(INSTANCIA).getMetrics();

        return new ResilienceStatusResponse(
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfBufferedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfNotPermittedCalls(),
                bulkhead.getAvailableConcurrentCalls(),
                bulkhead.getMaxAllowedConcurrentCalls(),
                pendingQueue.size());
    }
}
