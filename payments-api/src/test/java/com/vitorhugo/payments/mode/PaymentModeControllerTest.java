package com.vitorhugo.payments.mode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentModeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PaymentModeHolder holder;

    @BeforeEach
    void reset() {
        holder.set(PaymentMode.SUCCESS);
    }

    @Test
    void deveComecarEmSuccess() throws Exception {
        mockMvc.perform(get("/payments/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SUCCESS"));
    }

    @Test
    void deveTrocarDeModo() throws Exception {
        mockMvc.perform(post("/payments/mode/slow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SLOW"));

        assertThat(holder.current()).isEqualTo(PaymentMode.SLOW);
    }

    @Test
    void deveAceitarTodosOsModosDocumentados() throws Exception {
        for (PaymentMode mode : PaymentMode.values()) {
            mockMvc.perform(post("/payments/mode/" + mode.name().toLowerCase()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mode").value(mode.name()));
        }
    }

    @Test
    void deveRejeitarModoDesconhecido() throws Exception {
        mockMvc.perform(post("/payments/mode/explode"))
                .andExpect(status().isBadRequest());

        assertThat(holder.current()).isEqualTo(PaymentMode.SUCCESS);
    }
}
