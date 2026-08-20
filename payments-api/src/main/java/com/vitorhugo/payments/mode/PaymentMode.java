package com.vitorhugo.payments.mode;

/**
 * Comportamento que o servico de pagamento simula a cada chamada.
 *
 * <p>Trocar o modo em runtime e o que permite exercitar as politicas de
 * resiliencia do orders-api sem precisar quebrar nada de verdade.
 */
public enum PaymentMode {

    /** Responde 200 imediatamente. */
    SUCCESS,

    /** Responde 200, mas so depois do atraso configurado. */
    SLOW,

    /** Responde 500 em toda chamada. */
    FAIL,

    /** Responde 500 numa fracao das chamadas. */
    FLAKY,

    /**
     * Responde 422: o provedor esta saudavel e recusou o pagamento.
     *
     * <p>Nao faz parte do roteiro classico de falhas, mas existe para provar a
     * diferenca entre "a dependencia quebrou" e "a dependencia disse nao" — so a
     * primeira deve abrir o circuito.
     */
    DECLINED
}
