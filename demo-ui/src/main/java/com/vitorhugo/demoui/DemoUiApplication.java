package com.vitorhugo.demoui;

import com.vitorhugo.payments.mode.PaymentMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class DemoUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoUiApplication.class, args);
    }

    public static List<PaymentMode> paymentModes() {
        return List.of(PaymentMode.values());
    }
}
