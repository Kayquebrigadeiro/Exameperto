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
closure_rows=$(mktemp)
order_rows=$(mktemp)
trap 'rm -f -- "$source_rows" "$closure_rows" "$order_rows"' EXIT
psql "$SOURCE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -At -F $'\t' -c \
  "SELECT e.id,s.usuario_id,encode(u.email_cifrado,'hex'),encode(u.email_busca,'hex'),encode(u.nome_cifrado,'hex'),u.senha_hash,coalesce(encode(pa.cpf_cifrado,'hex'),''),coalesce(encode(pa.cpf_busca,'hex'),''),coalesce(pa.nascimento::text,''),coalesce(pa.identidade_estado,''),c.anonimizado_em FROM execucao_expurgo e JOIN solicitacao_privacidade s ON s.id=e.solicitacao_id JOIN encerramento_conta c ON c.solicitacao_id=s.id AND c.estado='ENCERRADA' JOIN usuario u ON u.id=s.usuario_id LEFT JOIN paciente pa ON pa.usuario_id=u.id WHERE e.estado='VERIFICADA' AND e.verificada_em IS NOT NULL" > "$closure_rows"
while IFS=$'\t' read -r execution owner email_cipher email_lookup name_cipher password cpf_cipher cpf_lookup birth identity completed; do
  [[ -n "$execution" ]] || continue
  valid_uuid "$execution" && valid_uuid "$owner" || die "identificador inválido no encerramento"
  [[ "$email_cipher" =~ ^[0-9a-f]+$ && "$email_lookup" =~ ^[0-9a-f]+$ && "$name_cipher" =~ ^[0-9a-f]+$ && "$cpf_cipher" =~ ^[0-9a-f]*$ && "$cpf_lookup" =~ ^[0-9a-f]*$ ]] || die "cifra inválida no encerramento"
  psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q \
    -v execution="$execution" -v owner="$owner" -v email_cipher="$email_cipher" -v email_lookup="$email_lookup" -v name_cipher="$name_cipher" -v password="$password" \
    -v cpf_cipher="$cpf_cipher" -v cpf_lookup="$cpf_lookup" -v birth="$birth" -v identity="$identity" -v completed="$completed" <<'SQL'
INSERT INTO account_closure_completion(execution_id,owner_id,email_cifrado,email_busca,nome_cifrado,senha_hash,cpf_cifrado,cpf_busca,nascimento,identidade_estado,completed_at)
VALUES (:'execution'::uuid,:'owner'::uuid,decode(:'email_cipher','hex'),decode(:'email_lookup','hex'),decode(:'name_cipher','hex'),:'password',CASE WHEN :'cpf_cipher'='' THEN NULL ELSE decode(:'cpf_cipher','hex') END,CASE WHEN :'cpf_lookup'='' THEN NULL ELSE decode(:'cpf_lookup','hex') END,NULLIF(:'birth','')::date,NULLIF(:'identity',''),:'completed'::timestamptz)
ON CONFLICT (execution_id) DO NOTHING;
SQL
done < "$closure_rows"
psql "$SOURCE_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -At -F $'\t' -c \
  "SELECT e.id,s.usuario_id,p.id,encode(p.origem_cifrada,'hex'),encode(p.destino_cifrada,'hex'),p.origem_lat,p.origem_lon,p.destino_lat,p.destino_lon FROM execucao_expurgo e JOIN solicitacao_privacidade s ON s.id=e.solicitacao_id JOIN encerramento_conta c ON c.solicitacao_id=s.id AND c.estado='ENCERRADA' JOIN pedido p ON p.solicitante_id=s.usuario_id OR p.destinatario_id=s.usuario_id OR p.paciente_id IN (SELECT id FROM paciente WHERE usuario_id=s.usuario_id) WHERE e.estado='VERIFICADA'" > "$order_rows"
while IFS=$'\t' read -r execution owner order origin destination origin_lat origin_lon destination_lat destination_lon; do
  [[ -n "$execution" ]] || continue
  valid_uuid "$execution" && valid_uuid "$owner" && valid_uuid "$order" || die "identificador inválido em endereço encerrado"
  [[ "$origin" =~ ^[0-9a-f]+$ && "$destination" =~ ^[0-9a-f]+$ ]] || die "cifra inválida em endereço encerrado"
  psql "$PURGE_JOURNAL_DATABASE_URL" -X --no-psqlrc -v ON_ERROR_STOP=1 -q \
    -v execution="$execution" -v owner="$owner" -v order="$order" -v origin="$origin" -v destination="$destination" \
    -v origin_lat="$origin_lat" -v origin_lon="$origin_lon" -v destination_lat="$destination_lat" -v destination_lon="$destination_lon" <<'SQL'
INSERT INTO account_closure_order(execution_id,owner_id,order_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon)
VALUES (:'execution'::uuid,:'owner'::uuid,:'order'::uuid,decode(:'origin','hex'),decode(:'destination','hex'),:'origin_lat'::numeric,:'origin_lon'::numeric,:'destination_lat'::numeric,:'destination_lon'::numeric)
ON CONFLICT DO NOTHING;
SQL
done < "$order_rows"
printf 'captured=%s\n' "$captured"
