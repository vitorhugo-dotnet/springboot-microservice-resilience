package com.vitorhugo.orders.domain;

/**
 * Situacao de um pedido.
 *
 * <p>A distincao que importa aqui e entre {@link #PAYMENT_PENDING} e
 * {@link #REJECTED}: o primeiro significa "nao consegui falar com o pagamento",
 * o segundo significa "o pagamento respondeu e disse nao". Sao problemas
 * diferentes e so o primeiro merece ser reprocessado depois.
 */
public enum OrderStatus {

    /** Pagamento autorizado. */
    PAID,

    /** Pagamento indisponivel; autorizacao enfileirada para depois. */
    PAYMENT_PENDING,

    /** Pagamento respondeu recusando. */
    REJECTED
}
