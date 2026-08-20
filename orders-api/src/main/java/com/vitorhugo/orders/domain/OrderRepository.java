package com.vitorhugo.orders.domain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

/** Armazenamento em memoria. Persistencia real esta fora do escopo do projeto. */
@Repository
public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}
