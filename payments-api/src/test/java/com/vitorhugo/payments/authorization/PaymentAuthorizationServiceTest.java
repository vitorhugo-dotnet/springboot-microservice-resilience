package com.vitorhugo.payments.authorization;

import java.math.BigDecimal;
import java.time.Duration;

import com.vitorhugo.payments.mode.PaymentMode;
import com.vitorhugo.payments.mode.PaymentModeHolder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAuthorizationServiceTest {

    private final PaymentModeHolder holder = new PaymentModeHolder();

    private PaymentAuthorizationService service(double randomValue) {
        return new PaymentAuthorizationService(holder, Duration.ofMillis(50), 0.5, () -> randomValue);
    }

    private AuthorizationRequest request() {
        return new AuthorizationRequest("order-1", new BigDecimal("10.00"));
    }

    @Test
    void successDeveAutorizar() {
        holder.set(PaymentMode.SUCCESS);

        AuthorizationResponse response = service(0.0).authorize(request());

        assertThat(response.status()).isEqualTo("AUTHORIZED");
        assertThat(response.orderId()).isEqualTo("order-1");
        assertThat(response.authorizationId()).isNotBlank();
    }

    @Test
    void slowDeveDemorarMasAutorizar() {
        holder.set(PaymentMode.SLOW);

        long inicio = System.nanoTime();
        AuthorizationResponse response = service(0.0).authorize(request());
        long decorridoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(response.status()).isEqualTo("AUTHORIZED");
        assertThat(decorridoMs).isGreaterThanOrEqualTo(50);
    }

    @Test
    void failDeveSempreFalhar() {
        holder.set(PaymentMode.FAIL);

        assertThatThrownBy(() -> service(0.99).authorize(request()))
                .isInstanceOf(PaymentFailureException.class);
    }

    @Test
    void flakyDeveFalharAbaixoDoLimite() {
        holder.set(PaymentMode.FLAKY);

        assertThatThrownBy(() -> service(0.1).authorize(request()))
                .isInstanceOf(PaymentFailureException.class);
    }

    @Test
    void flakyDeveAutorizarAcimaDoLimite() {
        holder.set(PaymentMode.FLAKY);

        assertThat(service(0.9).authorize(request()).status()).isEqualTo("AUTHORIZED");
    }
}
