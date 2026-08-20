package com.vitorhugo.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryTest {

    private final OrderRepository repository = new OrderRepository();

    private Order pedido() {
        return new Order("o-1", "c-1", new BigDecimal("10.00"),
                OrderStatus.PAYMENT_PENDING, null, null, Instant.now());
    }

    @Test
    void deveSalvarERecuperar() {
        Order order = pedido();

        repository.save(order);

        assertThat(repository.findById("o-1")).contains(order);
    }

    @Test
    void deveDevolverVazioParaIdDesconhecido() {
        assertThat(repository.findById("nao-existe")).isEmpty();
    }

    @Test
    void withStatusDeveDevolverCopiaSemMutarOOriginal() {
        Order original = pedido();

        Order pago = original.withStatus(OrderStatus.PAID, "auth-1", null);

        assertThat(pago.status()).isEqualTo(OrderStatus.PAID);
        assertThat(pago.authorizationId()).isEqualTo("auth-1");
        assertThat(pago.id()).isEqualTo(original.id());
        assertThat(original.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }
}
