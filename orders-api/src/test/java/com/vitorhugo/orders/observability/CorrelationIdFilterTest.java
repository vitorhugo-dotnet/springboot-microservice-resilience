package com.vitorhugo.orders.observability;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorrelationIdFilterTest {

    static WireMockServer wireMock;

    @Autowired
    MockMvc mockMvc;

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

    @Test
    void deveEcoarOCorrelationIdRecebido() throws Exception {
        mockMvc.perform(get("/resilience/status").header("X-Correlation-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
    }

    @Test
    void deveGerarCorrelationIdQuandoAusente() throws Exception {
        mockMvc.perform(get("/resilience/status"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void devePropagarOCorrelationIdParaOPagamento() throws Exception {
        wireMock.resetAll();
        wireMock.stubFor(post("/payments/authorize").willReturn(okJson("""
                {"authorizationId":"auth-1","orderId":"o-1","amount":10.00,"status":"AUTHORIZED"}
                """)));

        mockMvc.perform(MockMvcRequestBuilders.post("/orders")
                        .header("X-Correlation-Id", "rastro-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"customerId":"c-1","amount":10.00}
                                """))
                .andExpect(status().isCreated());

        // A chamada ao pagamento acontece numa thread do pool, nao na thread do
        // request: se o MDC nao for propagado, este header nao chega la.
        wireMock.verify(postRequestedFor(urlEqualTo("/payments/authorize"))
                .withHeader("X-Correlation-Id", equalTo("rastro-1")));
    }
}
