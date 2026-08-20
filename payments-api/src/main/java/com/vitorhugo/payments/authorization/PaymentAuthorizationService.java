package com.vitorhugo.payments.authorization;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import com.vitorhugo.payments.mode.PaymentMode;
import com.vitorhugo.payments.mode.PaymentModeHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Autoriza o pagamento aplicando o comportamento do modo ativo.
 *
 * <p>O atraso do modo SLOW e a taxa de falha do modo FLAKY sao injetados, e a
 * fonte de aleatoriedade tambem — sem isso nao da para testar FLAKY de forma
 * deterministica.
 */
@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);

    private final PaymentModeHolder holder;
    private final Duration slowDelay;
    private final double flakyFailureRate;
    private final DoubleSupplier random;

    @Autowired
    public PaymentAuthorizationService(
            PaymentModeHolder holder,
            @Value("${payments.slow-delay}") Duration slowDelay,
            @Value("${payments.flaky-failure-rate}") double flakyFailureRate) {
        this(holder, slowDelay, flakyFailureRate, () -> ThreadLocalRandom.current().nextDouble());
    }

    PaymentAuthorizationService(
            PaymentModeHolder holder,
            Duration slowDelay,
            double flakyFailureRate,
            DoubleSupplier random) {
        this.holder = holder;
        this.slowDelay = slowDelay;
        this.flakyFailureRate = flakyFailureRate;
        this.random = random;
    }

    public AuthorizationResponse authorize(AuthorizationRequest request) {
        PaymentMode mode = holder.current();
        log.info("autorizando pedido {} no modo {}", request.orderId(), mode);

        switch (mode) {
            case SUCCESS -> {
                // sem efeito colateral: responde na hora
            }
            case SLOW -> sleep(slowDelay);
            case FAIL -> throw new PaymentFailureException(
                    "provedor de pagamento indisponivel (modo FAIL)");
            case FLAKY -> {
                if (random.getAsDouble() < flakyFailureRate) {
                    throw new PaymentFailureException(
                            "provedor de pagamento instavel (modo FLAKY)");
                }
            }
        }

        return new AuthorizationResponse(
                "auth-" + UUID.randomUUID(),
                request.orderId(),
                request.amount(),
                "AUTHORIZED");
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentFailureException("autorizacao interrompida");
        }
    }
}
