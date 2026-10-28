# Aula 08 — Checkout Security (OWASP Top 10 & Zero Trust)

Módulo Spring Boot **mínimo e compilável** que responde ao desafio da Aula 08:

1. **Spring Security com OAuth2 OIDC + scopes finos** — resource server JWT, `@PreAuthorize`
   com `SCOPE_orders.read` / `SCOPE_orders.write`, Zero Trust (stateless, valida token a cada
   requisição), CORS/CSP/HSTS.
2. **Rate limit com Bucket4j** nos endpoints sensíveis a IDOR (`/orders/**`) — 20 req/min por
   identidade, resposta 429.
3. **Exploração SSRF (RedTeam) + relatório BlueTeam** — vulnerabilidade, correção e evidência.

## Estrutura

```
src/main/java/com/ada/checkout/
  config/SecurityConfig.java        # Hardening central (Zero Trust, headers, CORS, JWT)
  web/OrdersController.java         # /orders/{id} com fix de IDOR/BOLA (API1) + scope (API5)
  web/WebhookController.java        # /webhooks/payment-callback (superfície de SSRF)
  web/RateLimitInterceptor.java     # Rate limit Bucket4j (API4)
  service/SafeUrlValidator.java     # Defesa de SSRF (API7)
  service/OrderService.java         # Dados de laboratório (alice/bob)
src/test/java/com/ada/checkout/
  OrdersSecurityTest.java           # IDOR, scopes, 401/403/404, admin
  SsrfDefenseTest.java              # Bloqueio de metadata/loopback/privado/esquema
redteam/                            # Scripts de exploração (uso local autorizado)
blueteam/RELATORIO-BLUETEAM.md      # Relatório + evidencias/
```

## Como rodar

> Requer JDK 21. Se não houver `mvn` no PATH, use o Maven do cache do wrapper
> (`~/.m2/wrapper/dists/apache-maven-*/.../bin/mvn`) ou gere o wrapper com
> `mvn -N wrapper:wrapper`.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"

# 1) Testes (prova dos controles)
mvn test

# 2) Subir a aplicação
mvn spring-boot:run        # ou: java -jar target/checkout-security-1.0.0.jar
```

## Reproduzir os ataques (RedTeam) — somente contra a sua instância local

```bash
cd redteam
chmod +x *.sh

# token da alice (dono dos pedidos 1001/1002)
export TOKEN=$(./gen_token.sh orders.read alice)
./idor_probe.sh            # IDOR (404) + rate limit (429)

export TOKEN=$(./gen_token.sh orders.write alice)
./ssrf_exploit.sh          # SSRF -> 400 ssrf_blocked
```

## Observações de produção

- **IdP real:** trocar o decoder HS256 (laboratório) por Keycloak OIDC via
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (RS256 + rotação de chaves).
- **Rate limit distribuído:** Bucket4j + Redis/Hazelcast para compartilhar o limite entre
  réplicas no cenário de 5.000 TPS.
- **Esteira de CVE:** Dependabot + Trivy + SAST/DAST bloqueando merge/deploy em achado crítico.
