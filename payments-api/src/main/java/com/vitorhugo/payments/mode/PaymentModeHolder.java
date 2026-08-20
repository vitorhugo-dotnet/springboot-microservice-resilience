package com.vitorhugo.payments.mode;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/** Guarda o modo de falha ativo. Estado em memoria, trocado em runtime. */
@Component
public class PaymentModeHolder {

    private final AtomicReference<PaymentMode> current = new AtomicReference<>(PaymentMode.SUCCESS);

    public PaymentMode current() {
        return current.get();
    }

    public void set(PaymentMode mode) {
        current.set(mode);
    }
}
