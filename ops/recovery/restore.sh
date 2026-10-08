#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_command pg_restore; require_command psql; require_command tar; require_command sha256sum
require_var RESTORE_DATABASE_URL; require_var RESTORE_PRIVATE_OBJECT_ROOT; require_var RESTORE_BUNDLE; require_var PURGE_JOURNAL_DATABASE_URL
[[ "${RESTORE_ISOLATION_CONFIRMED:-}" == true ]] || die "confirme rede/credenciais isoladas com RESTORE_ISOLATION_CONFIRMED=true"
[[ "${EXTERNAL_EFFECTS_DISABLED:-}" == true ]] || die "confirme e-mail, pagamento, repasse e demais efeitos desligados"
verify_bundle "$RESTORE_BUNDLE"
declare -A seen_manifest=()
while IFS='=' read -r key value; do
  case "$key" in
    FORMAT_VERSION|BACKUP_ID|CREATED_AT|APPLICATION_RELEASE|POSTGRES_SERVER_VERSION|PG_DUMP_VERSION|FLYWAY_SCHEMA_VERSION|CONSISTENCY|OBJECT_SCOPE|PURGE_JOURNAL) ;;
    *) die "campo desconhecido no manifesto: $key" ;;
  esac
  [[ -z "${seen_manifest[$key]:-}" ]] || die "campo duplicado no manifesto: $key"
  seen_manifest[$key]=1
  printf -v "$key" '%s' "$value"
done < "$RESTORE_BUNDLE/manifest.env"
for key in FORMAT_VERSION BACKUP_ID CREATED_AT APPLICATION_RELEASE POSTGRES_SERVER_VERSION PG_DUMP_VERSION FLYWAY_SCHEMA_VERSION CONSISTENCY OBJECT_SCOPE PURGE_JOURNAL; do
  [[ -n "${seen_manifest[$key]:-}" ]] || die "campo ausente no manifesto: $key"
done
[[ "$FORMAT_VERSION" == 1 ]] || die "versão de manifesto incompatível"
valid_uuid "$BACKUP_ID" || die "backup_id inválido"
[[ "$CONSISTENCY" == QUIESCED_APPLICATION_WRITES ]] || die "modelo de consistência incompatível"
[[ "$OBJECT_SCOPE" == REGISTERED_PRIVATE_ROOT ]] || die "escopo de objetos incompatível"
[[ "$PURGE_JOURNAL" == EXTERNAL_POSTGRES_REQUIRED ]] || die "dependência de expurgos incompatível"

control_table=$(psql_value "$RESTORE_DATABASE_URL" "SELECT COALESCE(to_regclass('public.controle_restauracao')::text,'')")
existing=''
if [[ -n "$control_table" ]]; then
  existing=$(psql_value "$RESTORE_DATABASE_URL" "SELECT COALESCE(backup_id::text,'') FROM controle_restauracao WHERE singleton")
fi
if [[ -z "$existing" ]]; then
  [[ -z "$(psql_value "$RESTORE_DATABASE_URL" "SELECT string_agg(tablename,',') FROM pg_tables WHERE schemaname='public'")" ]] || die "destino não está vazio"
  pg_restore --dbname="$RESTORE_DATABASE_URL" --exit-on-error --single-transaction --no-owner --no-privileges "$RESTORE_BUNDLE/database.dump"
  mkdir -p "$RESTORE_PRIVATE_OBJECT_ROOT"
  [[ -z "$(find "$RESTORE_PRIVATE_OBJECT_ROOT" -mindepth 1 -print -quit)" ]] || die "destino de objetos não está vazio"
  while IFS= read -r member; do
    [[ "$member" != /* && "$member" != *'../'* && "$member" != '..' ]] || die "caminho inseguro no arquivo de objetos"
  done < <(tar -tzf "$RESTORE_BUNDLE/private-objects.tar.gz")
  while IFS= read -r listing; do
    [[ "${listing:0:1}" != l && "${listing:0:1}" != h ]] || die "link não permitido no arquivo de objetos"
  done < <(tar -tvzf "$RESTORE_BUNDLE/private-objects.tar.gz")
  tar -xzf "$RESTORE_BUNDLE/private-objects.tar.gz" -C "$RESTORE_PRIVATE_OBJECT_ROOT" --no-same-owner --no-same-permissions
  [[ -z "$(find "$RESTORE_PRIVATE_OBJECT_ROOT" -type l -print -quit)" ]] || die "link inesperado nos objetos restaurados"
  chmod 700 "$RESTORE_PRIVATE_OBJECT_ROOT"
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -v backup="$BACKUP_ID" <<'SQL'
UPDATE controle_restauracao SET estado='BLOQUEADO',backup_id=:'backup'::uuid,iniciado_em=clock_timestamp(),
  verificado_em=NULL,liberado_em=NULL,detalhe_codigo=NULL,updated_at=clock_timestamp() WHERE singleton;
SQL
elif [[ "$existing" != "$BACKUP_ID" ]]; then
  die "destino pertence a outro backup"
fi

if [[ "${RECOVERY_FAIL_AFTER_RESTORE:-}" == true ]]; then
  die "interrupção de ensaio após restauração; gate permanece bloqueado"
fi

psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -v backup="$BACKUP_ID" <<'SQL'
UPDATE controle_restauracao SET estado='APLICANDO',detalhe_codigo=NULL,updated_at=clock_timestamp()
WHERE singleton AND backup_id=:'backup'::uuid;
SQL

apply_owner() {
  local execution="$1" owner="$2" verified="$3" key path key_file
  key_file=$(mktemp)
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -Atq -c \
    "SELECT objeto_chave FROM documento WHERE proprietario_id='$owner'::uuid AND categoria NOT IN ('FINANCIAMENTO','COMPROVANTE') AND estado<>'EXPURGADO'" > "$key_file" || { rm -f -- "$key_file"; return 1; }
  while IFS= read -r key; do
    [[ -n "$key" ]] || continue
    [[ "$key" != /* && "$key" != *'../'* && "$key" != '..' ]] || die "chave de objeto insegura no banco restaurado"
    path="$RESTORE_PRIVATE_OBJECT_ROOT/$key"
    [[ ! -L "$path" ]] || die "objeto restaurado não pode ser symlink"
    rm -f -- "$path"
  done < "$key_file"
  rm -f -- "$key_file"
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -v backup="$BACKUP_ID" -v execution="$execution" -v owner="$owner" -v verified="$verified" <<'SQL'
BEGIN;
UPDATE documento SET objeto_chave='tombstone/'||id,sha256=decode(md5(id::text),'hex'),mime='application/x-deleted',
  tamanho=1,estado='EXPURGADO',updated_at=clock_timestamp(),version=version+1
WHERE proprietario_id=:'owner'::uuid AND categoria NOT IN ('FINANCIAMENTO','COMPROVANTE') AND estado<>'EXPURGADO';
DELETE FROM posicao_tarefa p USING pedido pe
  LEFT JOIN paciente pa ON pa.id=pe.paciente_id
  LEFT JOIN designacao d ON d.pedido_id=pe.id AND d.encerrada_em IS NULL
  LEFT JOIN entregador e ON e.id=d.entregador_id
WHERE p.pedido_id=pe.id AND (pe.solicitante_id=:'owner'::uuid OR pe.destinatario_id=:'owner'::uuid OR pa.usuario_id=:'owner'::uuid OR e.usuario_id=:'owner'::uuid);
DELETE FROM outbox WHERE payload_saneado->>'usuarioId'=:'owner';
INSERT INTO restauracao_expurgo_aplicado(backup_id,execucao_id,titular_id,verificado_origem_em)
VALUES (:'backup'::uuid,:'execution'::uuid,:'owner'::uuid,:'verified'::timestamptz) ON CONFLICT DO NOTHING;
COMMIT;
SQL
}

journal_rows=$(mktemp)
trap 'rm -f -- "$journal_rows"' EXIT
psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -At -F $'\t' -c \
  "SELECT execution_id,owner_id,verified_at FROM purge_completion WHERE category='CONTA' ORDER BY verified_at,execution_id" > "$journal_rows"
while IFS=$'\t' read -r execution owner verified; do
  [[ -n "$execution" ]] || continue
  valid_uuid "$execution" && valid_uuid "$owner" || die "entrada inválida no diário de expurgos"
  apply_owner "$execution" "$owner" "$verified"
done < "$journal_rows"

remaining=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN documento d ON d.proprietario_id=a.titular_id WHERE a.backup_id='$BACKUP_ID'::uuid AND d.categoria NOT IN ('FINANCIAMENTO','COMPROVANTE') AND (d.estado<>'EXPURGADO' OR d.mime<>'application/x-deleted')")
positions=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN pedido pe ON true LEFT JOIN paciente pa ON pa.id=pe.paciente_id LEFT JOIN designacao d ON d.pedido_id=pe.id AND d.encerrada_em IS NULL LEFT JOIN entregador e ON e.id=d.entregador_id JOIN posicao_tarefa p ON p.pedido_id=pe.id WHERE a.backup_id='$BACKUP_ID'::uuid AND (pe.solicitante_id=a.titular_id OR pe.destinatario_id=a.titular_id OR pa.usuario_id=a.titular_id OR e.usuario_id=a.titular_id)")
queued=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN outbox o ON o.payload_saneado->>'usuarioId'=a.titular_id::text WHERE a.backup_id='$BACKUP_ID'::uuid")
if [[ "$remaining" != 0 || "$positions" != 0 || "$queued" != 0 ]]; then
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -c "UPDATE controle_restauracao SET estado='FALHA',detalhe_codigo='PURGE_VERIFICATION_FAILED',updated_at=clock_timestamp() WHERE singleton"
  die "verificação de expurgos falhou"
fi
psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -c "UPDATE controle_restauracao SET estado='VERIFICADO',verificado_em=clock_timestamp(),detalhe_codigo=NULL,updated_at=clock_timestamp() WHERE singleton"
printf 'backup_id=%s state=VERIFICADO access=BLOCKED\n' "$BACKUP_ID"
