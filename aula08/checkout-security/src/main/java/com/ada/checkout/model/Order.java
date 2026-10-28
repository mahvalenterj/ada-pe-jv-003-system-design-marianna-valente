package com.ada.checkout.model;

/**
 * Pedido do Checkout. {@code ownerSubject} liga o recurso ao {@code sub} do dono,
 * permitindo a verificação de autorização em nível de objeto (OWASP API1 - BOLA/IDOR).
 */
public record Order(String id, String ownerSubject, String item, long amountCents, String status) {
}
