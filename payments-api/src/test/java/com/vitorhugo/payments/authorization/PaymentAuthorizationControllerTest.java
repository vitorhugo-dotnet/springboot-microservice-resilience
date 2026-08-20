package com.vitorhugo.payments.authorization;

import com.vitorhugo.payments.mode.PaymentMode;
import com.vitorhugo.payments.mode.PaymentModeHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "payments.slow-delay=50ms")
class PaymentAuthorizationControllerTest {

    private static final String CORPO = """
            {"orderId":"order-1","amount":10.00}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PaymentModeHolder holder;

    @BeforeEach
    void reset() {
        holder.set(PaymentMode.SUCCESS);
    }

    @Test
    void deveAutorizarNoModoSuccess() throws Exception {
        mockMvc.perform(post("/payments/authorize").contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.authorizationId").isNotEmpty());
    }

    @Test
    void deveResponder500NoModoFail() throws Exception {
        holder.set(PaymentMode.FAIL);

        mockMvc.perform(post("/payments/authorize").contentType(APPLICATION_JSON).content(CORPO))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void deveRejeitarPayloadInvalido() throws Exception {
        mockMvc.perform(post("/payments/authorize")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"orderId":"","amount":-5}
                                """))
                .andExpect(status().isBadRequest());
    }
}
