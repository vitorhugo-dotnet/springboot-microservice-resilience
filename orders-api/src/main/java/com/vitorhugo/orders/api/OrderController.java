package com.vitorhugo.orders.api;

import java.util.List;

import com.vitorhugo.orders.domain.Order;
import com.vitorhugo.orders.domain.OrderStatus;
import com.vitorhugo.orders.pending.PaymentAuthorizationPending;
import com.vitorhugo.orders.pending.PendingAuthorizationQueue;
import com.vitorhugo.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;
    private final PendingAuthorizationQueue pendingQueue;

    public OrderController(OrderService service, PendingAuthorizationQueue pendingQueue) {
        this.service = service;
        this.pendingQueue = pendingQueue;
    }

    /**
     * O codigo de resposta carrega a informacao que importa para o cliente:
     * 201 quando o pedido esta resolvido (pago ou recusado) e 202 quando ficou
     * aceito mas ainda depende de uma autorizacao futura. Nunca 500 por falha
     * do pagamento — degradar nao e erro do cliente.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = service.create(request);
        HttpStatus status = order.status() == OrderStatus.PAYMENT_PENDING
                ? HttpStatus.ACCEPTED
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(@PathVariable String id) {
        return service.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/pending")
    public List<PaymentAuthorizationPending> pending() {
        return pendingQueue.snapshot();
    }
}
