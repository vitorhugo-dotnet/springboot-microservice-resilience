package com.vitorhugo.payments.authorization;

/**
 * Recusa de negocio. Vira 422 na borda HTTP — deliberadamente diferente do 500
 * de {@link PaymentFailureException}, porque quem chama precisa distinguir
 * "recusado" de "quebrado".
 */
public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
