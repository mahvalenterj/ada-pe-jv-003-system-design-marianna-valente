#!/usr/bin/env bash
#
# Gera um JWT HS256 assinado com o segredo do laboratório, para exercitar os endpoints
# protegidos. Útil também para demonstrar JWT tampering: altere um claim do token gerado
# sem reassinar e o resource server devolverá 401 (assinatura inválida).
#
# Uso:  ./gen_token.sh <scope[,scope...]> [subject]
# Ex.:  TOKEN=$(./gen_token.sh orders.read alice)
#       TOKEN=$(./gen_token.sh orders.read,orders.write alice)
#
set -euo pipefail

SCOPES="${1:-orders.read}"
SUBJECT="${2:-alice}"
SECRET="${JWT_HMAC_SECRET:-change-me-dev-only-0123456789-0123456789-0123456789}"

b64url() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }

# "scope" como lista JSON (o resource server converte cada item em SCOPE_<valor>).
scope_json=$(printf '%s' "$SCOPES" | awk -F, '{for(i=1;i<=NF;i++){printf (i>1?",":"") "\"" $i "\""}}')

now=$(date +%s)
exp=$((now + 3600))

header='{"alg":"HS256","typ":"JWT"}'
payload="{\"sub\":\"${SUBJECT}\",\"scope\":[${scope_json}],\"iat\":${now},\"exp\":${exp}}"

h=$(printf '%s' "$header"  | b64url)
p=$(printf '%s' "$payload" | b64url)
sig=$(printf '%s' "${h}.${p}" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)

echo "${h}.${p}.${sig}"
