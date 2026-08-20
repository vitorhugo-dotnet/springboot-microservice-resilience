package com.vitorhugo.orders.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.vitorhugo.orders.api.CreateOrderRequest;
import com.vitorhugo.orders.domain.Order;
import com.vitorhugo.orders.domain.OrderRepository;
import com.vitorhugo.orders.domain.OrderStatus;
import com.vitorhugo.orders.payments.PaymentAuthorization;
import com.vitorhugo.orders.payments.PaymentAuthorizationRequest;
import com.vitorhugo.orders.payments.PaymentsClient;
import com.vitorhugo.orders.pending.PaymentAuthorizationPending;
import com.vitorhugo.orders.pending.PendingAuthorizationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Traduz o resultado da autorizacao em uma decisao de negocio.
 *
 * <p>Repare no que este servico NAO faz: ele nao sabe o que e timeout, retry ou
 * circuito aberto. Tudo isso ja foi resolvido pelo {@link PaymentsClient} e
 * chega aqui como um dos tres resultados possiveis. E por isso que a regra de
 * negocio continua legivel mesmo com cinco politicas de resiliencia no caminho.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final PaymentsClient paymentsClient;
    private final OrderRepository repository;
    private final PendingAuthorizationQueue pendingQueue;

    public OrderService(PaymentsClient paymentsClient,
                        OrderRepository repository,
                        PendingAuthorizationQueue pendingQueue) {
        this.paymentsClient = paymentsClient;
        this.repository = repository;
        this.pendingQueue = pendingQueue;
    }

    public Order create(CreateOrderRequest request) {
        Order novo = new Order(
                UUID.randomUUID().toString(),
                request.customerId(),
                request.amount(),
                OrderStatus.PAYMENT_PENDING,
                null,
                null,
                Instant.now());

        PaymentAuthorization resultado = paymentsClient
                .authorize(new PaymentAuthorizationRequest(novo.id(), novo.amount()))
                .join();

        Order decidido = decidir(novo, resultado);
        log.info("pedido {} criado com status {}", decidido.id(), decidido.status());
        return repository.save(decidido);
    }

    private Order decidir(Order pedido, PaymentAuthorization resultado) {
        return switch (resultado) {
            case PaymentAuthorization.Authorized autorizado ->
                    pedido.withStatus(OrderStatus.PAID, autorizado.authorizationId(), null);

            case PaymentAuthorization.Declined recusado ->
                    pedido.withStatus(OrderStatus.REJECTED, null, recusado.reason());

            case PaymentAuthorization.Unavailable indisponivel -> {
                pendingQueue.enqueue(new PaymentAuthorizationPending(
                        pedido.id(), pedido.amount(), indisponivel.reason(), Instant.now()));
                yield pedido.withStatus(OrderStatus.PAYMENT_PENDING, null, indisponivel.reason());
            }
        };
    }

    public Optional<Order> findById(String id) {
        return repository.findById(id);
    }
}
