# Relatório BlueTeam — Aula 08 (Checkout Security)

**Serviço:** `checkout-security` (Spring Boot 3.5.16, Spring Security 6, Java 21)
**Data:** 2026-10-28
**Autora:** Marianna Valente
**Escopo:** laboratório controlado local (`http://localhost:8080`). Exploração executada apenas contra a própria instância.

Este relatório segue o formato pedido no desafio: para cada achado — **vulnerabilidade → correção → evidência**.

---

## Resumo executivo

| # | Risco OWASP | Vulnerabilidade | Status |
|---|-------------|-----------------|--------|
| 1 | API1 — BOLA / IDOR | Leitura de pedido de outro usuário por manipulação do `{id}` | ✅ Corrigido |
| 2 | API5 — Broken Function Level Authz | Acesso sem scope fino exigido | ✅ Corrigido |
| 3 | API2 — Broken Authentication | Aceite de token adulterado (JWT tampering) | ✅ Mitigado (assinatura validada) |
| 4 | API4 — Unrestricted Resource Consumption | Enumeração de IDs em alta velocidade | ✅ Mitigado (rate limit) |
| 5 | API7 — SSRF | Callback de pagamento buscando URL interna | ✅ Corrigido |

Todas as evidências brutas estão em [`evidencias/`](evidencias/).

---

## Achado 1 — IDOR / BOLA (OWASP API1)

**Vulnerabilidade.** O endpoint `GET /orders/{id}` identificava o pedido apenas pelo ID da
URL. Um token válido de qualquer usuário conseguia ler o pedido de outro trocando o `{id}`
(ex.: `alice` lendo o pedido `2001` do `bob`). O ID é um identificador direto de objeto e
não havia verificação de propriedade.

**Correção.** Autorização em nível de objeto em
[`OrdersController.java`](../src/main/java/com/ada/checkout/web/OrdersController.java):
compara-se o `sub` do JWT com `order.ownerSubject()`; quem não é dono (nem tem
`orders.admin`) recebe **404** — e não 403 — para não vazar a existência de IDs alheios.

**Evidência** (`evidencias/idor-scopes.txt`):
```
# 1) alice lê o PRÓPRIO pedido 1001 ............ HTTP 200
# 2) IDOR: alice tenta ler o 2001 do bob ....... HTTP 404  (bloqueado, sem vazar)
```

---

## Achado 2 — Autorização de função por scope fino (OWASP API5)

**Vulnerabilidade.** Sem controle por scope, qualquer token autenticado alcançaria a função
de leitura de pedidos, violando o menor privilégio.

**Correção.** `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('SCOPE_orders.read')")`
no método. O resource server converte o claim `scope` do JWT em authorities `SCOPE_*`.

**Evidência** (`evidencias/idor-scopes.txt`):
```
# 3) sem token ............................... HTTP 401
# 4) token sem scope orders.read ............. HTTP 403
```

---

## Achado 3 — JWT tampering (OWASP API2)

**Vulnerabilidade.** Se a assinatura do token não for verificada, um atacante altera claims
(ex.: `sub` ou `scope`) e se passa por outro usuário/privilégio.

**Correção.** Resource server OAuth2 com validação de assinatura (HS256 no laboratório;
**RS256 via `jwk-set-uri` do Keycloak em produção**) e expiração. Token adulterado falha na
verificação. Zero Trust: a validação ocorre a cada requisição, sem confiar na rede interna.

**Evidência** (`evidencias/idor-scopes.txt`):
```
# 5) assinatura adulterada (1 char trocado) ... HTTP 401
```

---

## Achado 4 — Consumo irrestrito / enumeração (OWASP API4)

**Vulnerabilidade.** Mesmo com IDOR corrigido, um atacante poderia varrer a faixa de IDs em
alta velocidade para mapear volume de pedidos e sondar o 404/200.

**Correção.** Rate limit por identidade com **Bucket4j**
([`RateLimitInterceptor.java`](../src/main/java/com/ada/checkout/web/RateLimitInterceptor.java)):
20 req/min por `sub` (IP como fallback), aplicado a `/orders/**`. Excedente recebe **429**
com `Retry-After`. Em produção, usar backend distribuído (Redis/Hazelcast) para compartilhar
o limite entre réplicas.

**Evidência** (`evidencias/rate-limit.txt`):
```
req #20 -> HTTP 200
req #21 -> HTTP 429   <-- limite atingido
...
req #25 -> HTTP 429
```

---

## Achado 5 — SSRF no callback de pagamento (OWASP API7 / A10)

**Vulnerabilidade.** `POST /webhooks/payment-callback` recebe uma `callbackUrl` do cliente.
Sem validação, o servidor poderia ser induzido a acessar recursos internos — com destaque
para o endpoint de metadados da nuvem (`169.254.169.254`), que vaza credenciais temporárias.

**Correção.** [`SafeUrlValidator.java`](../src/main/java/com/ada/checkout/service/SafeUrlValidator.java),
defesa em profundidade:
1. apenas esquema `https`;
2. allowlist opcional de host;
3. resolução de **todos** os IPs do host e rejeição de loopback, link-local (inclui metadata),
   privado/site-local, any-local, multicast e ULA IPv6.

Destino proibido → **400** `{"error":"ssrf_blocked"}`, com o motivo registrado para auditoria.
Em produção, revalidar o IP no momento da conexão (ou pinar o resolver) e proibir redirects
para IPs internos, fechando a janela de DNS rebinding (TOCTOU).

**Evidência** (`evidencias/ssrf.txt`):
```
https://169.254.169.254/latest/meta-data/... -> HTTP 400 ssrf_blocked (metadata cloud)
https://127.0.0.1/actuator/env ............... -> HTTP 400 ssrf_blocked (loopback)
https://10.0.0.1/admin ....................... -> HTTP 400 ssrf_blocked (rede privada)
http://169.254.169.254/latest/meta-data/ ..... -> HTTP 400 ssrf_blocked (esquema != https)
https://93.184.216.34/webhook-publico ........ -> HTTP 202 callback_registered (público: ok)
```

---

## Hardening transversal (OWASP A05 — Security Misconfiguration)

Em [`SecurityConfig.java`](../src/main/java/com/ada/checkout/config/SecurityConfig.java):
sessão **stateless**, `anyRequest().authenticated()` (fail-secure), **CORS** com allowlist
(nunca `*`), headers **HSTS**, **X-Frame-Options: DENY** e **CSP** restritiva; mensagens de
erro sem stacktrace (`server.error.include-*: never`).

## Próximos passos recomendados

- Trocar o decoder HS256 do laboratório por **Keycloak OIDC (`jwk-set-uri`, RS256)**.
- Rate limit distribuído (Bucket4j + Redis) para o cenário multi-réplica de 5.000 TPS.
- Esteira: SAST (CodeQL), DAST (OWASP ZAP), secret scanning e **Trivy/Dependabot** para CVEs,
  bloqueando merge/deploy em achado crítico.
