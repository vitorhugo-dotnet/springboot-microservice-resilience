package com.vitorhugo.orders.service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import com.vitorhugo.orders.api.CreateOrderRequest;
import com.vitorhugo.orders.domain.Order;
import com.vitorhugo.orders.domain.OrderRepository;
import com.vitorhugo.orders.domain.OrderStatus;
import com.vitorhugo.orders.payments.PaymentAuthorization;
import com.vitorhugo.orders.payments.PaymentAuthorizationRequest;
import com.vitorhugo.orders.payments.PaymentsClient;
import com.vitorhugo.orders.pending.PendingAuthorizationQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceTest {

    private final OrderRepository repository = new OrderRepository();
    private final PendingAuthorizationQueue queue = new PendingAuthorizationQueue();

    private OrderService service(PaymentAuthorization resultado) {
        PaymentsClient client = new PaymentsClient(null, null) {
            @Override
            public CompletableFuture<PaymentAuthorization> authorize(PaymentAuthorizationRequest request) {
                return CompletableFuture.completedFuture(resultado);
            }
        };
        return new OrderService(client, repository, queue);
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest("c-1", new BigDecimal("99.90"));
    }

    @Test
    void devePagarQuandoAutorizado() {
        Order order = service(new PaymentAuthorization.Authorized("auth-1")).create(request());

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.authorizationId()).isEqualTo("auth-1");
        assertThat(queue.size()).isZero();
    }

    @Test
    void deveCriarPendenteEEnfileirarQuandoIndisponivel() {
        Order order = service(new PaymentAuthorization.Unavailable("timeout na chamada ao pagamento"))
                .create(request());

        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.reason()).isEqualTo("timeout na chamada ao pagamento");
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.snapshot().getFirst().orderId()).isEqualTo(order.id());
    }

    @Test
    void deveRejeitarSemEnfileirarQuandoRecusado() {
        Order order = service(new PaymentAuthorization.Declined("saldo insuficiente")).create(request());

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.reason()).isEqualTo("saldo insuficiente");
        // Recusa nao volta para a fila: reprocessar algo que ja foi negado nao muda o resultado.
        assertThat(queue.size()).isZero();
    }

    @Test
    void devePersistirOPedidoNosTresCaminhos() {
        Order pago = service(new PaymentAuthorization.Authorized("auth-1")).create(request());
        Order pendente = service(new PaymentAuthorization.Unavailable("circuito aberto")).create(request());
        Order recusado = service(new PaymentAuthorization.Declined("saldo insuficiente")).create(request());

        assertThat(repository.findById(pago.id())).isPresent();
        assertThat(repository.findById(pendente.id())).isPresent();
        assertThat(repository.findById(recusado.id())).isPresent();
    }

    @Test
    void deveDevolverVazioParaPedidoInexistente() {
        assertThat(service(new PaymentAuthorization.Authorized("auth-1")).findById("nao-existe")).isEmpty();
    }
}
