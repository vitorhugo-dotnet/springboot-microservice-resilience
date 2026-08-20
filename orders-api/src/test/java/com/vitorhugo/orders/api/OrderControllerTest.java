package com.vitorhugo.orders.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.vitorhugo.orders.domain.Order;
import com.vitorhugo.orders.domain.OrderStatus;
import com.vitorhugo.orders.pending.PaymentAuthorizationPending;
import com.vitorhugo.orders.pending.PendingAuthorizationQueue;
import com.vitorhugo.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String CORPO = """
            {"customerId":"c-1","amount":99.90}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderService service;

    @MockitoBean
    PendingAuthorizationQueue queue;

    private Order pedido(OrderStatus status, String authorizationId, String reason) {
        return new Order("o-1", "c-1", new BigDecimal("99.90"), status, authorizationId, reason, Instant.now());
    }

    @Test
    void deveResponder201QuandoPago() throws Exception {
        given(service.create(any())).willReturn(pedido(OrderStatus.PAID, "auth-1", null));

        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.authorizationId").value("auth-1"));
    }

    @Test
    void deveResponder202QuandoPagamentoPendente() throws Exception {
        given(service.create(any()))
                .willReturn(pedido(OrderStatus.PAYMENT_PENDING, null, "circuito aberto"));

        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.reason").value("circuito aberto"));
    }

    @Test
    void deveResponder201QuandoRecusado() throws Exception {
        given(service.create(any()))
                .willReturn(pedido(OrderStatus.REJECTED, null, "saldo insuficiente"));

        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void deveResponder400ParaValorInvalido() throws Exception {
        mockMvc.perform(post("/orders").contentType(APPLICATION_JSON).content("""
                        {"customerId":"","amount":-5}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveResponder404ParaPedidoDesconhecido() throws Exception {
        given(service.findById("nao-existe")).willReturn(Optional.empty());

        mockMvc.perform(get("/orders/nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveResponder200ParaPedidoConhecido() throws Exception {
        given(service.findById("o-1")).willReturn(Optional.of(pedido(OrderStatus.PAID, "auth-1", null)));

        mockMvc.perform(get("/orders/o-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o-1"));
    }

    @Test
    void deveListarAFilaDePendentes() throws Exception {
        given(queue.snapshot()).willReturn(List.of(new PaymentAuthorizationPending(
                "o-1", new BigDecimal("99.90"), "timeout", Instant.now())));

        mockMvc.perform(get("/orders/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value("o-1"));
    }
}
