#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"

fail() { echo "FALHA: $1" >&2; exit 1; }

[[ -f "$ENV_FILE" ]] || fail ".env.smtp.local ausente"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || fail ".env.smtp.local deve ter modo 600"

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

required=(DATABASE_URL DATABASE_USER DATABASE_PASSWORD EXAME_PERTO_DATA_KEY EXAME_PERTO_SEARCH_KEY EXAME_PERTO_SIGNING_KEY EXAME_PERTO_EMAIL_FROM EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD)
for name in "${required[@]}"; do
  [[ -n "${!name:-}" ]] || fail "configuração obrigatória ausente: $name"
done
[[ "${REGISTRATION_ALLOWED_ORIGIN:-}" == "http://127.0.0.1:5173" ]] || fail "origem local deve ser http://127.0.0.1:5173"
[[ "${EXAME_PERTO_EMAIL_ENABLED:-}" == "true" ]] || fail "gateway de e-mail não está habilitado"
[[ "${REGISTRATION_PRIVACY_APPROVED:-}" == "true" ]] || fail "política local não está habilitada"
[[ "$EXAME_PERTO_EMAIL_FROM" == "$SPRING_MAIL_USERNAME" ]] || fail "remetente e usuário SMTP divergem"
case ",$EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS," in
  *",$EXAME_PERTO_EMAIL_FROM,"*) ;;
  *) fail "remetente não está na allowlist local" ;;
esac

curl -fsS --max-time 3 http://127.0.0.1:5173/ >/dev/null || fail "Vite não responde em 127.0.0.1:5173"
status="$(curl -sS -o /tmp/exame-perto-local-check.json -w '%{http_code}' --max-time 3 -X POST http://127.0.0.1:5173/api/v1/auth/login -H 'Origin: http://127.0.0.1:5173' -H 'Content-Type: application/json' --data '{}')"
[[ "$status" == "400" ]] || fail "proxy/backend não retornaram a validação esperada (HTTP $status)"
grep -q '"code":"INVALID_INPUT"' /tmp/exame-perto-local-check.json || fail "corpo de erro da API não é o esperado"

docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/infra/compose.yaml" exec -T postgres \
  psql -U "$DATABASE_USER" -d exameperto -Atc \
  "select case when (select max(installed_rank) from flyway_schema_history)=19 and (select estado from controle_restauracao where singleton)='NORMAL' then 'ok' else 'fail' end" \
  | grep -qx ok || fail "migrações ou gate de restauração não estão prontos"

echo "OK: Vite → backend → PostgreSQL; V19 aplicada; restauração NORMAL; configuração privada estruturalmente válida."
