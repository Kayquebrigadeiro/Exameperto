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

closure_rows=$(mktemp)
order_rows=$(mktemp)
trap 'rm -f -- "$journal_rows" "$closure_rows" "$order_rows"' EXIT
psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -At -F $'\t' -c \
  "SELECT execution_id,owner_id,encode(email_cifrado,'hex'),encode(email_busca,'hex'),encode(nome_cifrado,'hex'),senha_hash,coalesce(encode(cpf_cifrado,'hex'),''),coalesce(encode(cpf_busca,'hex'),''),coalesce(nascimento::text,''),coalesce(identidade_estado,'') FROM account_closure_completion ORDER BY completed_at,execution_id" > "$closure_rows"
while IFS=$'\t' read -r execution owner email_cipher email_lookup name_cipher password cpf_cipher cpf_lookup birth identity; do
  [[ -n "$execution" ]] || continue
  valid_uuid "$execution" && valid_uuid "$owner" || die "identificador inválido no diário de encerramento"
  [[ "$email_cipher" =~ ^[0-9a-f]+$ && "$email_lookup" =~ ^[0-9a-f]+$ && "$name_cipher" =~ ^[0-9a-f]+$ && "$cpf_cipher" =~ ^[0-9a-f]*$ && "$cpf_lookup" =~ ^[0-9a-f]*$ ]] || die "cifra inválida no diário de encerramento"
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q \
    -v execution="$execution" -v owner="$owner" -v email_cipher="$email_cipher" -v email_lookup="$email_lookup" -v name_cipher="$name_cipher" -v password="$password" \
    -v cpf_cipher="$cpf_cipher" -v cpf_lookup="$cpf_lookup" -v birth="$birth" -v identity="$identity" <<'SQL'
BEGIN;
UPDATE usuario SET email_cifrado=decode(:'email_cipher','hex'),email_busca=decode(:'email_lookup','hex'),nome_cifrado=decode(:'name_cipher','hex'),senha_hash=:'password',telefone_cifrado=NULL,estado='ENCERRADO',updated_at=clock_timestamp(),version=version+1 WHERE id=:'owner'::uuid;
UPDATE paciente SET cpf_cifrado=CASE WHEN :'cpf_cipher'='' THEN cpf_cifrado ELSE decode(:'cpf_cipher','hex') END,cpf_busca=CASE WHEN :'cpf_lookup'='' THEN cpf_busca ELSE decode(:'cpf_lookup','hex') END,nascimento=CASE WHEN :'birth'='' THEN nascimento ELSE :'birth'::date END,identidade_estado=CASE WHEN :'identity'='' THEN identidade_estado ELSE :'identity' END,verificado_em=NULL,updated_at=clock_timestamp(),version=version+1 WHERE usuario_id=:'owner'::uuid;
UPDATE sessao SET revogada_em=coalesce(revogada_em,clock_timestamp()),updated_at=clock_timestamp() WHERE usuario_id=:'owner'::uuid;
DELETE FROM desafio_conta WHERE usuario_id=:'owner'::uuid;
DELETE FROM mfa_totp WHERE usuario_id=:'owner'::uuid;
UPDATE autorizacao_paciente SET revogada_em=coalesce(revogada_em,clock_timestamp()),updated_at=clock_timestamp(),version=version+1 WHERE familiar_id=:'owner'::uuid OR concedida_por=:'owner'::uuid OR paciente_id IN (SELECT id FROM paciente WHERE usuario_id=:'owner'::uuid);
UPDATE convite_familiar SET estado='REVOGADO',revogado_em=coalesce(revogado_em,clock_timestamp()),updated_at=clock_timestamp(),version=version+1 WHERE paciente_id IN (SELECT id FROM paciente WHERE usuario_id=:'owner'::uuid) OR aceito_por=:'owner'::uuid;
UPDATE membro_instituicao SET revogado_em=coalesce(revogado_em,clock_timestamp()) WHERE usuario_id=:'owner'::uuid;
UPDATE papel_global SET revogado_em=coalesce(revogado_em,clock_timestamp()),motivo='ACCOUNT_CLOSED' WHERE usuario_id=:'owner'::uuid;
UPDATE entregador SET estado='SUSPENSO',updated_at=clock_timestamp(),version=version+1 WHERE usuario_id=:'owner'::uuid AND estado<>'SUSPENSO';
UPDATE execucao_expurgo SET estado='VERIFICADA',verificada_em=coalesce(verificada_em,clock_timestamp()),erro_codigo=NULL WHERE id=:'execution'::uuid;
UPDATE solicitacao_privacidade SET estado='VERIFICADA',version=version+1,updated_at=clock_timestamp() WHERE expurgo_id=:'execution'::uuid;
UPDATE encerramento_conta SET estado='ENCERRADA',anonimizado_em=coalesce(anonimizado_em,clock_timestamp()),encerrado_em=coalesce(encerrado_em,clock_timestamp()),bloqueio_codigo=NULL,detalhe_codigo=NULL,updated_at=clock_timestamp(),version=version+1 WHERE usuario_id=:'owner'::uuid;
COMMIT;
SQL
done < "$closure_rows"
psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -At -F $'\t' -c \
  "SELECT execution_id,owner_id,order_id,encode(origem_cifrada,'hex'),encode(destino_cifrada,'hex'),origem_lat,origem_lon,destino_lat,destino_lon FROM account_closure_order ORDER BY execution_id,order_id" > "$order_rows"
while IFS=$'\t' read -r execution owner order origin destination origin_lat origin_lon destination_lat destination_lon; do
  [[ -n "$execution" ]] || continue
  valid_uuid "$execution" && valid_uuid "$owner" && valid_uuid "$order" || die "identificador inválido em endereço encerrado"
  [[ "$origin" =~ ^[0-9a-f]+$ && "$destination" =~ ^[0-9a-f]+$ ]] || die "cifra inválida em endereço encerrado"
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q \
    -v order="$order" -v origin="$origin" -v destination="$destination" -v origin_lat="$origin_lat" -v origin_lon="$origin_lon" -v destination_lat="$destination_lat" -v destination_lon="$destination_lon" <<'SQL'
UPDATE pedido SET origem_cifrada=decode(:'origin','hex'),destino_cifrada=decode(:'destination','hex'),origem_lat=:'origin_lat'::numeric,origem_lon=:'origin_lon'::numeric,destino_lat=:'destination_lat'::numeric,destino_lon=:'destination_lon'::numeric,updated_at=clock_timestamp(),version=version+1 WHERE id=:'order'::uuid;
SQL
done < "$order_rows"

remaining=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN documento d ON d.proprietario_id=a.titular_id WHERE a.backup_id='$BACKUP_ID'::uuid AND d.categoria NOT IN ('FINANCIAMENTO','COMPROVANTE') AND (d.estado<>'EXPURGADO' OR d.mime<>'application/x-deleted')")
positions=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN pedido pe ON true LEFT JOIN paciente pa ON pa.id=pe.paciente_id LEFT JOIN designacao d ON d.pedido_id=pe.id AND d.encerrada_em IS NULL LEFT JOIN entregador e ON e.id=d.entregador_id JOIN posicao_tarefa p ON p.pedido_id=pe.id WHERE a.backup_id='$BACKUP_ID'::uuid AND (pe.solicitante_id=a.titular_id OR pe.destinatario_id=a.titular_id OR pa.usuario_id=a.titular_id OR e.usuario_id=a.titular_id)")
queued=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN outbox o ON o.payload_saneado->>'usuarioId'=a.titular_id::text WHERE a.backup_id='$BACKUP_ID'::uuid")
closures=$(psql_value "$RESTORE_DATABASE_URL" "SELECT count(*) FROM restauracao_expurgo_aplicado a JOIN encerramento_conta c ON c.usuario_id=a.titular_id WHERE a.backup_id='$BACKUP_ID'::uuid AND c.estado<>'ENCERRADA'")
if [[ "$remaining" != 0 || "$positions" != 0 || "$queued" != 0 || "$closures" != 0 ]]; then
  psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -c "UPDATE controle_restauracao SET estado='FALHA',detalhe_codigo='PURGE_VERIFICATION_FAILED',updated_at=clock_timestamp() WHERE singleton"
  die "verificação de expurgos falhou"
fi
psql "$RESTORE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q -c "UPDATE controle_restauracao SET estado='VERIFICADO',verificado_em=clock_timestamp(),detalhe_codigo=NULL,updated_at=clock_timestamp() WHERE singleton"
printf 'backup_id=%s state=VERIFICADO access=BLOCKED\n' "$BACKUP_ID"
