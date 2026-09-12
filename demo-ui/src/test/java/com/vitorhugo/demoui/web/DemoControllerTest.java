package com.vitorhugo.demoui.web;

import com.vitorhugo.demoui.client.ApiCallResult;
import com.vitorhugo.demoui.client.OrdersApiClient;
import com.vitorhugo.demoui.client.PaymentsApiClient;
import com.vitorhugo.payments.mode.PaymentMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = DemoController.class, properties = "spring.thymeleaf.enabled=false")
class DemoControllerTest {

    private static final ApiCallResult CURRENT_MODE = new ApiCallResult(
            "GET", "http://payments/payments/mode", 200, "{\"mode\":\"SUCCESS\"}", "Modo atual consultado.", true);
    private static final ApiCallResult MODE_UPDATED = new ApiCallResult(
            "POST", "http://payments/payments/mode/fail", 200, "{\"mode\":\"FAIL\"}", "Modo alterado.", true);
    private static final ApiCallResult ORDER_CREATED = new ApiCallResult(
            "POST", "http://orders/orders", 201, "{\"id\":\"o-1\"}", "Pedido criado.", true);
    private static final ApiCallResult RESILIENCE_STATUS = new ApiCallResult(
            "GET", "http://orders/resilience/status", 200, "{}", "Status atualizado.", true);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentsApiClient paymentsApiClient;

    @MockitoBean
    private OrdersApiClient ordersApiClient;

    @Test
    void homeDisplaysPaymentModesCurrentModeAndEmptyOrderForm() throws Exception {
        when(paymentsApiClient.currentMode()).thenReturn(CURRENT_MODE);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("modes", (Object) PaymentMode.values()))
                .andExpect(model().attribute("currentModeResult", CURRENT_MODE))
                .andExpect(model().attributeExists("orderForm"));
    }

    @Test
    void invalidOrderRetainsValidationErrorsWithoutCallingOrdersApi() throws Exception {
        when(paymentsApiClient.currentMode()).thenReturn(CURRENT_MODE);

        mockMvc.perform(post("/orders")
                        .param("customerId", "")
                        .param("amount", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "customerId", "amount"));

        verifyNoInteractions(ordersApiClient);
    }

    @Test
    void modeCommandRedirectsWithItsApiResult() throws Exception {
        when(paymentsApiClient.switchMode(PaymentMode.FAIL)).thenReturn(MODE_UPDATED);

        mockMvc.perform(post("/modes/fail"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("modeResult", MODE_UPDATED));

        verify(paymentsApiClient).switchMode(PaymentMode.FAIL);
    }

    @Test
    void orderCommandRedirectsWithItsApiResult() throws Exception {
        when(ordersApiClient.createOrder("customer-1", new BigDecimal("10.50"))).thenReturn(ORDER_CREATED);

        mockMvc.perform(post("/orders")
                        .param("customerId", "customer-1")
                        .param("amount", "10.50"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("orderResult", ORDER_CREATED));

        verify(ordersApiClient).createOrder("customer-1", new BigDecimal("10.50"));
    }

    @Test
    void resilienceRefreshRedirectsWithItsApiResult() throws Exception {
        when(ordersApiClient.resilienceStatus()).thenReturn(RESILIENCE_STATUS);

        mockMvc.perform(post("/resilience/refresh"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("resilienceResult", RESILIENCE_STATUS));

        verify(ordersApiClient).resilienceStatus();
    }

    @Test
    void invalidModeRendersAnAlertInsteadOfReturningServerError() throws Exception {
        when(paymentsApiClient.currentMode()).thenReturn(CURRENT_MODE);

        mockMvc.perform(post("/modes/unknown"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("modeResult"));
    }
}
