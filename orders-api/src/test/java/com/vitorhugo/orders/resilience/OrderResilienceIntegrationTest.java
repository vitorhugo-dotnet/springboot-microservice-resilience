package com.vitorhugo.orders.resilience;

import java.time.Duration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O contrato visto por quem chama o orders-api. Prova a promessa central do
 * projeto: uma dependencia quebrada nunca vira 500 generico nem request preso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderResilienceIntegrationTest {

    private static final String RESPOSTA_OK = """
            {"authorizationId":"auth-1","orderId":"o-1","amount":10.00,"status":"AUTHORIZED"}
            """;

    private static final String CORPO = """
            {"customerId":"c-1","amount":10.00}
            """;

    static WireMockServer wireMock;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CircuitBreakerRegistry registry;

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
        registry.circuitBreaker("payments").reset();
    }

    private MvcResult criarPedido() throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                .contentType(APPLICATION_JSON)
                .content(CORPO)).andReturn();
    }

    @Test
    void modoSuccessDeveCriarPedidoPago() throws Exception {
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson(RESPOSTA_OK)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.authorizationId").value("auth-1"));
    }

    @Test
    void modoSlowDeveCriarPedidoPendenteSemPrenderORequest() throws Exception {
        wireMock.stubFor(post("/payments/authorize")
                .willReturn(okJson(RESPOSTA_OK).withFixedDelay(5000)));

        long inicio = System.nanoTime();
        MvcResult resultado = criarPedido();
        long decorridoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(202);
        assertThat(resultado.getResponse().getContentAsString()).contains("PAYMENT_PENDING");
        // O pagamento demora 5s; o request nao pode esperar por isso.
        assertThat(decorridoMs).isLessThan(5000);

        mockMvc.perform(get("/orders/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reason").value("timeout na chamada ao pagamento"));
    }

    @Test
    void modoFailDeveCriarPedidoPendenteEmVezDe500() throws Exception {
        wireMock.stubFor(post("/payments/authorize").willReturn(aResponse().withStatus(500)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));
    }

    @Test
    void modoDeclinedDeveRejeitarSemEnfileirar() throws Exception {
        wireMock.stubFor(post("/payments/authorize")
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"reason":"saldo insuficiente"}
                                """)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("saldo insuficiente"));

        mockMvc.perform(get("/orders/pending"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pedidoCriadoDeveSerConsultavelDepois() throws Exception {
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson(RESPOSTA_OK)));

        String corpo = criarPedido().getResponse().getContentAsString();
        String id = corpo.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }
}
