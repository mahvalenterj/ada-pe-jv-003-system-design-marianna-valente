package com.ada.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidência automatizada dos controles A01 (BOLA/IDOR) e A05 (autorização por scope fino).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrdersSecurityTest {

    @Autowired
    MockMvc mvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(
            String sub, String... scopes) {
        org.springframework.security.oauth2.jwt.Jwt token =
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("lab-token")
                        .header("alg", "none")
                        .subject(sub)
                        .claim("scope", List.of(scopes))
                        .build();
        return jwt()
                .jwt(token)
                .authorities(java.util.Arrays.stream(scopes)
                        .map(s -> (org.springframework.security.core.GrantedAuthority)
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + s))
                        .toList());
    }

    @Test
    void semToken_retorna401() throws Exception {
        mvc.perform(get("/orders/1001")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemScope_retorna403() throws Exception {
        mvc.perform(get("/orders/1001").with(as("alice", "profile")))
           .andExpect(status().isForbidden());
    }

    @Test
    void donoAcessaProprioPedido_retorna200() throws Exception {
        mvc.perform(get("/orders/1001").with(as("alice", "orders.read")))
           .andExpect(status().isOk());
    }

    @Test
    void idor_acessoCruzadoNegado_retorna404() throws Exception {
        // alice tenta ler pedido do bob (2001) -> tratado como inexistente (não vaza ID alheio)
        mvc.perform(get("/orders/2001").with(as("alice", "orders.read")))
           .andExpect(status().isNotFound());
    }

    @Test
    void admin_acessaQualquerPedido_retorna200() throws Exception {
        mvc.perform(get("/orders/2001").with(as("auditor", "orders.read", "orders.admin")))
           .andExpect(status().isOk());
    }
}
