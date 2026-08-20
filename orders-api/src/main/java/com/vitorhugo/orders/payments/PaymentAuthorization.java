package com.vitorhugo.orders.payments;

/**
 * Resultado de uma tentativa de autorizacao, ja traduzido para o vocabulario do
 * orders-api.
 *
 * <p>{@link Declined} e {@link Unavailable} sao coisas diferentes de proposito:
 * uma recusa significa que o pagamento esta saudavel e respondeu "nao"; uma
 * indisponibilidade significa que nem deu para perguntar. Confundir os dois faz
 * o circuit breaker abrir por motivo errado.
 */
public sealed interface PaymentAuthorization {

    /** O pagamento autorizou. */
    record Authorized(String authorizationId) implements PaymentAuthorization {
    }

    /** O pagamento respondeu recusando (ex.: saldo insuficiente). */
    record Declined(String reason) implements PaymentAuthorization {
    }

    /** Nao foi possivel obter resposta: timeout, erro, circuito aberto ou bulkhead cheio. */
    record Unavailable(String reason) implements PaymentAuthorization {
    }
}
