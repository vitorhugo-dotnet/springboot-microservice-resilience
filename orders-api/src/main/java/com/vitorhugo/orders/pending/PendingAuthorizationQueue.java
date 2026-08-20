package com.vitorhugo.orders.pending;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fila das autorizacoes que o fallback nao conseguiu fazer na hora.
 *
 * <p>Em memoria de proposito: um broker real (RabbitMQ, Kafka, outbox no banco)
 * e o que producao pediria, e some no restart — mas o ponto do projeto e a
 * politica de resiliencia, nao a infraestrutura de mensageria. O consumidor que
 * drena esta fila tambem ficou de fora; o que importa aqui e que o fallback
 * deixe o trabalho registrado em vez de perder o pedido.
 */
@Component
public class PendingAuthorizationQueue {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationQueue.class);

    private final Queue<PaymentAuthorizationPending> fila = new ConcurrentLinkedQueue<>();

    public void enqueue(PaymentAuthorizationPending pending) {
        fila.add(pending);
        log.info("autorizacao enfileirada para o pedido {} (motivo: {}); fila com {} itens",
                pending.orderId(), pending.reason(), fila.size());
    }

    public List<PaymentAuthorizationPending> snapshot() {
        return List.copyOf(fila);
    }

    public int size() {
        return fila.size();
    }
}
