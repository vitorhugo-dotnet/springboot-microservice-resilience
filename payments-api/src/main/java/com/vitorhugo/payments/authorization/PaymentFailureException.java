package com.vitorhugo.payments.authorization;

/** Falha simulada do provedor de pagamento. Vira 500 na borda HTTP. */
public class PaymentFailureException extends RuntimeException {

    public PaymentFailureException(String message) {
        super(message);
    }
}
