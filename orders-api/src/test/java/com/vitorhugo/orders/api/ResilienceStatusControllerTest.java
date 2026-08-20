package com.vitorhugo.orders.api;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResilienceStatusControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CircuitBreakerRegistry registry;

    @AfterEach
    void reset() {
        registry.circuitBreaker("payments").reset();
    }

    @Test
    void deveReportarEstadoDoCircuito() throws Exception {
        registry.circuitBreaker("payments").reset();

        mockMvc.perform(get("/resilience/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakerState").value("CLOSED"))
                .andExpect(jsonPath("$.bulkheadMaxConcurrentCalls").value(8))
                .andExpect(jsonPath("$.pendingQueueSize").isNumber());
    }

    @Test
    void deveRefletirCircuitoAberto() throws Exception {
        registry.circuitBreaker("payments").transitionToOpenState();

        mockMvc.perform(get("/resilience/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakerState").value("OPEN"));
    }

    @Test
    void actuatorDeveExporSaudeDoCircuitBreaker() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.circuitBreakers").exists());
    }
}
