package com.ada.checkout.web;

import com.ada.checkout.model.Order;
import com.ada.checkout.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Endpoint de pedidos com a correção de IDOR/BOLA (OWASP API1).
 *
 * <p>Dois níveis de controle:
 * <ol>
 *   <li><b>Function-level (API5):</b> {@code @PreAuthorize("hasAuthority('SCOPE_orders.read')")}
 *       — só tokens com o scope fino chegam ao método.</li>
 *   <li><b>Object-level (API1):</b> comparação do {@code sub} do token com o dono do
 *       pedido. Ter o scope não basta; é preciso ser o dono (ou ter {@code orders.admin}).</li>
 * </ol>
 *
 * <p>Importante: retornamos <b>404</b> (e não 403) quando o recurso existe mas não
 * pertence ao chamador, para não vazar a existência de IDs de outros usuários
 * (defesa contra enumeração).
 */
@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final OrderService orderService;

    public OrdersController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_orders.read')")
    public ResponseEntity<Order> getOrder(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        Order order = orderService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));

        String caller = jwt.getSubject();
        boolean isAdmin = jwt.getClaimAsStringList("scope") != null
                && jwt.getClaimAsStringList("scope").contains("orders.admin");

        if (!isAdmin && !order.ownerSubject().equals(caller)) {
            // Recurso de outro dono: trate como inexistente para o chamador.
            throw new ResponseStatusException(NOT_FOUND, "order not found");
        }
        return ResponseEntity.ok(order);
    }
}
