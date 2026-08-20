package com.vitorhugo.orders.resilience;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.vitorhugo.orders.payments.PaymentAuthorization;
import com.vitorhugo.orders.payments.PaymentAuthorizationRequest;
import com.vitorhugo.orders.payments.PaymentsClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os criterios de aceite do circuit breaker, um por teste.
 *
 * <p>As transicoes de estado sao disparadas pelo registry em vez de esperar o
 * relogio: o que precisa ser verificado e a reacao do nosso codigo a cada
 * estado, nao o timer do Resilience4j.
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerLifecycleTest {

    private static final String RESPOSTA_OK = """
            {"authorizationId":"auth-1","orderId":"o-1","amount":10.00,"status":"AUTHORIZED"}
            """;

    static WireMockServer wireMock;

    @Autowired
    PaymentsClient client;

    @Autowired
    CircuitBreakerRegistry registry;

    @Autowired
    BulkheadRegistry bulkheadRegistry;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("payments.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        breaker().reset();
        aguardarBulkheadLivre();
    }

    /**
     * O permit do bulkhead so e devolvido quando a chamada real termina — nao
     * quando o TimeLimiter desiste dela. Depois do teste de saturacao existem
     * chamadas ainda rodando ate o read timeout, e sem esperar por elas o teste
     * seguinte comeca com o semaforo cheio.
     */
    private void aguardarBulkheadLivre() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("payments");
        int total = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();

        while (bulkhead.getMetrics().getAvailableConcurrentCalls() < total) {
            if (System.nanoTime() > limite) {
                throw new IllegalStateException("bulkhead nao liberou os permits a tempo");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private CircuitBreaker breaker() {
        return registry.circuitBreaker("payments");
    }

    private PaymentAuthorization autorizar() {
        return client.authorize(new PaymentAuthorizationRequest("o-1", new BigDecimal("10.00"))).join();
    }

    @Test
    void deveAbrirOCircuitoAposFalhasSeguidas() {
        wireMock.stubFor(post("/payments/authorize").willReturn(serverError()));

        for (int i = 0; i < 10; i++) {
            autorizar();
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void naoDeveChamarPagamentoComCircuitoAberto() {
        breaker().transitionToOpenState();
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson(RESPOSTA_OK)));

        PaymentAuthorization resultado = autorizar();

        assertThat(resultado).isInstanceOf(PaymentAuthorization.Unavailable.class);
        assertThat(((PaymentAuthorization.Unavailable) resultado).reason())
                .contains("circuito aberto");
        // O ponto todo do circuit breaker: a chamada nem sai.
        wireMock.verify(0, postRequestedFor(urlEqualTo("/payments/authorize")));
    }

    @Test
    void halfOpenDevePermitirChamadasDeTeste() {
        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson(RESPOSTA_OK)));

        assertThat(autorizar()).isInstanceOf(PaymentAuthorization.Authorized.class);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/payments/authorize")));
    }

    @Test
    void deveVoltarParaClosedDepoisDaRecuperacao() {
        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson(RESPOSTA_OK)));

        // permitted-number-of-calls-in-half-open-state = 3
        for (int i = 0; i < 3; i++) {
            assertThat(autorizar()).isInstanceOf(PaymentAuthorization.Authorized.class);
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void deveVoltarParaOpenSeAFalhaPersistirNoHalfOpen() {
        breaker().transitionToOpenState();
        breaker().transitionToHalfOpenState();
        wireMock.stubFor(post("/payments/authorize").willReturn(serverError()));

        for (int i = 0; i < 3; i++) {
            autorizar();
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void bulkheadDeveRejeitarChamadasAlemDoLimiteSimultaneo() {
        // Resposta lenta o bastante para que as chamadas se sobreponham e o
        // semaforo do bulkhead (max-concurrent-calls = 8) chegue ao limite.
        wireMock.stubFor(post("/payments/authorize")
                .willReturn(okJson(RESPOSTA_OK).withFixedDelay(5000)));

        List<CompletableFuture<PaymentAuthorization>> chamadas = IntStream.range(0, 12)
                .mapToObj(i -> client.authorize(new PaymentAuthorizationRequest("o-" + i, new BigDecimal("10.00"))))
                .toList();

        long rejeitadasPeloBulkhead = chamadas.stream()
                .map(CompletableFuture::join)
                .filter(PaymentAuthorization.Unavailable.class::isInstance)
                .map(PaymentAuthorization.Unavailable.class::cast)
                .filter(u -> u.reason().contains("bulkhead cheio"))
                .count();

        assertThat(rejeitadasPeloBulkhead).isGreaterThan(0);
    }
}
