#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${1:-$ROOT_DIR/.env.smtp.local}"
fail() { echo "FALHA: $1" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || fail ".env.smtp.local ausente"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || fail ".env.smtp.local deve ter modo 600"

read -r -s -p "Access Token de teste Mercado Pago: " access_token; printf '\n' >&2
read -r -s -p "Segredo de assinatura do Webhook de teste: " webhook_secret; printf '\n' >&2
read -r -p "E-mail da conta compradora de teste (opcional): " payer_email
[[ -n "$access_token" ]] || fail "Access Token vazio"
[[ -n "$webhook_secret" ]] || fail "segredo de Webhook vazio"
[[ -z "$payer_email" || "$payer_email" == *@testuser.com ]] || fail "o sandbox exige comprador @testuser.com"

umask 077; tmp_file="$(mktemp "${ENV_FILE}.payment.XXXXXX")"; trap 'rm -f "$tmp_file"' EXIT
grep -Ev '^EXAME_PERTO_PAYMENT_(ENABLED|ENVIRONMENT|ACCESS_TOKEN|WEBHOOK_SECRET|SANDBOX_PAYER_EMAIL)=' "$ENV_FILE" > "$tmp_file" || true
printf '%s\n' 'EXAME_PERTO_PAYMENT_ENABLED=true' 'EXAME_PERTO_PAYMENT_ENVIRONMENT=SANDBOX' "EXAME_PERTO_PAYMENT_ACCESS_TOKEN=$access_token" "EXAME_PERTO_PAYMENT_WEBHOOK_SECRET=$webhook_secret" "EXAME_PERTO_PAYMENT_SANDBOX_PAYER_EMAIL=$payer_email" >> "$tmp_file"
chmod 600 "$tmp_file"; mv "$tmp_file" "$ENV_FILE"; trap - EXIT
unset access_token webhook_secret payer_email
echo "OK: sandbox de pagamento configurado; credenciais não exibidas."
