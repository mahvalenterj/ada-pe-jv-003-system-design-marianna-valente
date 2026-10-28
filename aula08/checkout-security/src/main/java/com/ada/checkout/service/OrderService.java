package com.ada.checkout.service;

import com.ada.checkout.model.Order;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório em memória para o laboratório. Semeia pedidos de dois donos distintos
 * ({@code alice} e {@code bob}) para exercitar o cenário IDOR/BOLA entre usuários.
 */
@Service
public class OrderService {

    private final Map<String, Order> store = new ConcurrentHashMap<>();

    public OrderService() {
        store.put("1001", new Order("1001", "alice", "Fone Bluetooth", 19900, "PAID"));
        store.put("1002", new Order("1002", "alice", "Capa de celular", 4900, "PENDING"));
        store.put("2001", new Order("2001", "bob", "Teclado mecânico", 35000, "PAID"));
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
