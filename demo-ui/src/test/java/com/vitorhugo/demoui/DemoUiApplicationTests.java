package com.vitorhugo.demoui;

import com.vitorhugo.payments.mode.PaymentMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoUiApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void exposesPaymentModesFromPaymentsApi() {
        assertThat(DemoUiApplication.paymentModes()).containsExactly(PaymentMode.values());
    }
}
