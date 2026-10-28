Aula 8 – Segurança: OWASP Top 10 & Zero Trust
Chegou a hora de blindar as APIs criadas até aqui. Ataques evoluem rapidamente; apenas código seguro por design e defesa em profundidade evita incidentes custosos. Nesta aula cobriremos a mitigação prática dos OWASP Top 10 usando Spring Security 6, JWT/OIDC e o princípio Zero Trust.

Objetivo da Aula: ensinar desenvolvedores a detectar, corrigir e prevenir vulnerabilidades críticas, implementando hardening de endpoints e pipeline contínuo de análise de CVEs.

Problema Gerador
Como proteger APIs contra os 10 maiores vetores de ataque da OWASP?

Tópicos Principais
Nesta seção, abordaremos as principais estratégias de segurança para APIs modernas. Começando com autenticação via Spring Security e Keycloak, passando pelos controles OWASP Top 10, até um pipeline robusto de gestão de vulnerabilidades com monitoramento contínuo.

1. Spring Security 6 & Keycloak OIDC
Configuração minimalista com spring-boot-starter-oauth2-resource-server + JWT.
Transforma o serviço em resource server com poucas linhas; valida assinatura e expiração do token, dispensando sessão e simplificando escala horizontal.
Integração SSO com Keycloak; scopes e granularidade de permissões (hasAuthority('SCOPE_orders.read')).
Delegar autenticação ao IdP elimina gestão de senhas; scopes finos aplicam menor privilégio e tokens curtos minimizam impacto de revogação.
Hardening: CORS restritivo, headers Content-Security-Policy, X-Frame-Options.
Cabeçalhos e CORS bloqueiam XSS, clickjacking e requisições indevidas; política centralizada facilita conformidade com requisitos de segurança e auditoria.
Zero Trust: validar token em cada requisição; não confiar em redes internas.
Cada chamada é autenticada e autorizada isoladamente; modelo presume rede hostil, limitando movimento lateral caso invasor obtenha acesso parcial.
2. OWASP Top 10 ↔ Controles de Código
Mapear A01‑Broken Access Control até A10‑Server‑Side Request Forgery às contramedidas Spring (method security, CSRF, rate‑limit, input validation). Crie checklist que vincula cada risco a controles Spring; automatize testes de autorização, validação de entrada e políticas CSRF nos pipelines, garantindo cobertura contínua e rastreável.
Secure Development Lifecycle: SAST (CodeQL), DAST (OWASP ZAP), secret scanning no CI. Integre SAST, DAST e verificação de segredos a cada pull request; falhas críticas bloqueiam merge, promovendo correção antecipada e cultura de segurança contínua.
Principles: least privilege, fail‑secure defaults, secure by default. Aplique menor privilégio e padrões seguros: configure permissões explicitamente, desative funções desnecessárias e valide dados na entrada, reforçando o modelo secure‑by‑design em todo o stack.
3. CVE Triage & Patching Pipeline
Monitorar CVEs com Dependabot + Trivy; pull request automático com patch.
Dependabot monitora dependências e abre PRs de atualização, enquanto Trivy escaneia imagens e SBOM por CVEs; automação reduz MTTR e bloqueia deploy de versões vulneráveis.
Política de SemVer e feature toggle para correções rápidas sem big‑bang deploy.
Versões SemVer claras e toggles isolam patches críticos em branches de manutenção, permitindo ativação gradual de correções sem grandes releases ou impacto em usuários.
RedTeam vs BlueTeam lab: explorar CVE prática (Log4Shell) e aplicar patch + WAF rule.
Exercício coloca time de ataque explorando Log4Shell e time de defesa identificando, corrigindo e blindando via WAF, treinando resposta a incidentes e validação dos controles.

Estudo de Caso
Lab Exploit Controlado & Hardening

RedTeam executa ataque SQLi e JWT tampering contra endpoint /orders.
BlueTeam analisa logs JSON + trace, detecta payload malicioso, aplica method security (@PreAuthorize).
Integra Keycloak como RP; valida audience e expiration.
Patch aplicado, redeploy em CI com Trivy scan; mostrar diff no dashboard.
Desafio
Configurar Spring Security com OAuth2 OIDC + scopes finos.
Implementar rate‑limit (Bucket4j) nos endpoints vulneráveis A07‑IDOR.
Injetar exploit SSRF via RedTeam script e entregar relatório BlueTeam: vulnerabilidade, correção, evidência.
Conclusão
API segura nasce de Zero Trust, secure by design e processo contínuo de atualização. Ao mapear o OWASP Top 10 em controles Spring, integrar OIDC e monitorar CVEs, reduzem‑se janelas de ataque e o custo de correção tardia.
