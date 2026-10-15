#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env.smtp.local}"
fail() { echo "FALHA: $1" >&2; exit 1; }

[[ -f "$ENV_FILE" ]] || fail ".env.smtp.local ausente"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || fail ".env.smtp.local deve ter modo 600"

read -r -s -p "Chave privada do provedor de rotas: " route_key
printf '\n' >&2
[[ -n "$route_key" ]] || fail "chave vazia"

umask 077
tmp_file="$(mktemp "${ENV_FILE}.route.XXXXXX")"
trap 'rm -f "$tmp_file"' EXIT
grep -Ev '^EXAME_PERTO_ROUTE_(ENABLED|API_KEY|ENDPOINT|TIMEOUT|MAX_RESPONSE_BYTES|MAX_RETRIES)=' "$ENV_FILE" > "$tmp_file" || true
printf '%s\n' \
  'EXAME_PERTO_ROUTE_ENABLED=true' \
  "EXAME_PERTO_ROUTE_API_KEY=$route_key" \
  'EXAME_PERTO_ROUTE_ENDPOINT=https://api.heigit.org/openrouteservice/v2/directions/driving-car' \
  'EXAME_PERTO_ROUTE_TIMEOUT=PT3S' \
  'EXAME_PERTO_ROUTE_MAX_RESPONSE_BYTES=65536' \
  'EXAME_PERTO_ROUTE_MAX_RETRIES=1' >> "$tmp_file"
chmod 600 "$tmp_file"
mv "$tmp_file" "$ENV_FILE"
trap - EXIT
unset route_key
echo "OK: configuração privada de rota atualizada; chave não exibida."
