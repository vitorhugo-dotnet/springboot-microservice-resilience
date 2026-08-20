package com.vitorhugo.orders.payments;

import java.math.BigDecimal;
import java.time.Duration;

import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PaymentsClientTest {

    static final String RESPOSTA_OK = """
            {"authorizationId":"auth-1","orderId":"o-1","amount":10.00,"status":"AUTHORIZED"}
            """;

    static WireMockServer wireMock;

    @Autowired
    PaymentsClient client;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

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
        circuitBreakerRegistry.circuitBreaker("payments").reset();
    }

    private PaymentAuthorization authorize() {
        return client.authorize(new PaymentAuthorizationRequest("o-1", new BigDecimal("10.00"))).join();
    }

    @Test
    void deveAutorizarQuandoPagamentoResponde200() {
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson(RESPOSTA_OK)));

        PaymentAuthorization resultado = authorize();

        assertThat(resultado).isInstanceOf(PaymentAuthorization.Authorized.class);
        assertThat(((PaymentAuthorization.Authorized) resultado).authorizationId()).isEqualTo("auth-1");
    }

    @Test
    void deveDevolverUnavailableQuandoRespostaPassaDoTimeout() {
        wireMock.stubFor(post("/payments/authorize")
                .willReturn(okJson(RESPOSTA_OK).withFixedDelay(5000)));

        long inicio = System.nanoTime();
        PaymentAuthorization resultado = authorize();
        long decorridoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(resultado).isInstanceOf(PaymentAuthorization.Unavailable.class);
        assertThat(decorridoMs).isLessThan(5000);
    }

    @Test
    void recusaComCorpoIlegivelAindaDeveSerRecusa() {
        wireMock.stubFor(post("/payments/authorize")
                .willReturn(aResponse().withStatus(422).withBody("saldo insuficiente")));

        assertThat(authorize()).isInstanceOf(PaymentAuthorization.Declined.class);
    }

    @Test
    void deveRetentarAntesDeDesistir() {
        wireMock.stubFor(post("/payments/authorize").willReturn(serverError()));

        assertThat(authorize()).isInstanceOf(PaymentAuthorization.Unavailable.class);

        wireMock.verify(2, postRequestedFor(urlEqualTo("/payments/authorize")));
    }

    @Test
    void naoDeveTratarRecusaLegitimaComoIndisponibilidade() {
        wireMock.stubFor(post("/payments/authorize")
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"reason":"saldo insuficiente"}
                                """)));

        PaymentAuthorization resultado = authorize();

        assertThat(resultado).isInstanceOf(PaymentAuthorization.Declined.class);
        assertThat(((PaymentAuthorization.Declined) resultado).reason()).isEqualTo("saldo insuficiente");
        // Recusa nao e falha da dependencia: nao retenta e nao conta para o circuito.
        wireMock.verify(1, postRequestedFor(urlEqualTo("/payments/authorize")));
        assertThat(circuitBreakerRegistry.circuitBreaker("payments").getMetrics().getNumberOfFailedCalls())
                .isZero();
    }
}
