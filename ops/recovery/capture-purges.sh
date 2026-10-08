#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_command psql; require_var SOURCE_DATABASE_URL; require_var PURGE_JOURNAL_DATABASE_URL

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -f "$script_dir/journal-init.sql"
source_rows=$(mktemp)
trap 'rm -f -- "$source_rows"' EXIT
psql "$SOURCE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -At -F $'\t' -c \
  "SELECT e.id,s.usuario_id,t.categoria,e.verificada_em FROM execucao_expurgo e JOIN solicitacao_privacidade s ON s.id=e.solicitacao_id JOIN tombstone_expurgo t ON t.execucao_id=e.id WHERE e.estado='VERIFICADA' AND e.verificada_em IS NOT NULL ORDER BY e.verificada_em,e.id" > "$source_rows"
captured=0
while IFS=$'\t' read -r execution owner category verified; do
  [[ -n "$execution" ]] || continue
  valid_uuid "$execution" && valid_uuid "$owner" || die "identificador inválido vindo do banco de origem"
  [[ "$category" == CONTA ]] || die "categoria de expurgo não suportada: $category"
  psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q \
    -v execution="$execution" -v owner="$owner" -v category="$category" -v verified="$verified" <<'SQL'
INSERT INTO purge_completion(execution_id,owner_id,category,verified_at)
VALUES (:'execution'::uuid,:'owner'::uuid,:'category',:'verified'::timestamptz)
ON CONFLICT (execution_id) DO UPDATE SET captured_at=purge_completion.captured_at
WHERE purge_completion.owner_id=excluded.owner_id
  AND purge_completion.category=excluded.category
  AND purge_completion.verified_at=excluded.verified_at;
SELECT CASE WHEN EXISTS (
  SELECT 1 FROM purge_completion WHERE execution_id=:'execution'::uuid AND owner_id=:'owner'::uuid
    AND category=:'category' AND verified_at=:'verified'::timestamptz
) THEN true ELSE pg_catalog.set_config('recovery.capture_error','conflict',false)::boolean END;
SQL
  captured=$((captured+1))
done < "$source_rows"
printf 'captured=%s\n' "$captured"
