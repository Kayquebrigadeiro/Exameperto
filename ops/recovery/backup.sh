#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command pg_dump; require_command psql; require_command tar; require_command sha256sum
require_var BACKUP_DATABASE_URL; require_var PRIVATE_OBJECT_ROOT; require_var BACKUP_DESTINATION
[[ "${BACKUP_WRITES_QUIESCED:-}" == true ]] || die "confirme a suspensão de escritas com BACKUP_WRITES_QUIESCED=true"
[[ -d "$PRIVATE_OBJECT_ROOT" ]] || die "raiz de objetos privados inexistente"
[[ ! -L "$PRIVATE_OBJECT_ROOT" ]] || die "raiz de objetos privados não pode ser symlink"
[[ -z "$(find "$PRIVATE_OBJECT_ROOT" -type l -print -quit)" ]] || die "armazenamento privado contém symlink"
[[ ! -e "$BACKUP_DESTINATION" ]] || die "destino já existe; backups são imutáveis"

backup_parent=$(dirname "$BACKUP_DESTINATION")
mkdir -p "$backup_parent"
stage=$(mktemp -d "$backup_parent/.backup-stage.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT
backup_id=$(cat /proc/sys/kernel/random/uuid)
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
app_version=${APPLICATION_RELEASE:-unknown}
[[ "$app_version" =~ ^[A-Za-z0-9._-]+$ ]] || die "APPLICATION_RELEASE contém caracteres inválidos"

pg_dump "$BACKUP_DATABASE_URL" --format=custom --compress=6 --no-owner --no-privileges \
  --serializable-deferrable --file="$stage/database.dump"
tar --create --gzip --file="$stage/private-objects.tar.gz" --directory="$PRIVATE_OBJECT_ROOT" .

server_version=$(psql_value "$BACKUP_DATABASE_URL" "SHOW server_version_num")
schema_version=$(psql_value "$BACKUP_DATABASE_URL" "SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1),'none')")
dump_version=$(pg_dump --version | sed -E 's/[^0-9]*([0-9]+\.[0-9]+([.][0-9]+)?).*/\1/')
printf '%s\n' \
  'FORMAT_VERSION=1' \
  "BACKUP_ID=$backup_id" \
  "CREATED_AT=$created_at" \
  "APPLICATION_RELEASE=$app_version" \
  "POSTGRES_SERVER_VERSION=$server_version" \
  "PG_DUMP_VERSION=$dump_version" \
  "FLYWAY_SCHEMA_VERSION=$schema_version" \
  'CONSISTENCY=QUIESCED_APPLICATION_WRITES' \
  'OBJECT_SCOPE=REGISTERED_PRIVATE_ROOT' \
  'PURGE_JOURNAL=EXTERNAL_POSTGRES_REQUIRED' > "$stage/manifest.env"
(cd "$stage" && sha256sum database.dump private-objects.tar.gz manifest.env > SHA256SUMS)
chmod 600 "$stage"/*
mv "$stage" "$BACKUP_DESTINATION"
trap - EXIT
printf 'backup_id=%s bundle=%s\n' "$backup_id" "$BACKUP_DESTINATION"
