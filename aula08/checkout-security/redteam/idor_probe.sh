#!/usr/bin/env bash
#
# RedTeam — Prova de IDOR/BOLA (OWASP API1) e de rate limit (API4) em /orders/{id}.
# USO AUTORIZADO SOMENTE: apenas contra a sua instância local de laboratório.
#
# Pré-requisito: token da "alice" com scope orders.read:
#   TOKEN=$(./redteam/gen_token.sh orders.read alice)
#
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:?defina TOKEN (alice, scope orders.read)}"

auth=(-H "Authorization: Bearer ${TOKEN}")

echo "== [RedTeam] 1) Acesso ao próprio pedido (1001 = alice) — esperado 200 =="
curl -s -o /dev/null -w "[HTTP %{http_code}]\n" "${auth[@]}" "${BASE_URL}/orders/1001"

echo "== [RedTeam] 2) IDOR: ler pedido do bob (2001) com token da alice — esperado 404 (não vaza) =="
curl -s -o /dev/null -w "[HTTP %{http_code}]\n" "${auth[@]}" "${BASE_URL}/orders/2001"

echo "== [RedTeam] 3) Enumeração em velocidade — rate limit deve cortar em 429 =="
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" "${auth[@]}" "${BASE_URL}/orders/$((1000 + i))")
  echo "req #$i -> HTTP $code"
done
echo "== Esperado: após ~20 requisições na janela, respostas passam a 429 Too Many Requests =="
