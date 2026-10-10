#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Execute scripts/configure-local-email.sh primeiro." >&2
  exit 1
fi
if [[ "$(stat -c '%a' "$ENV_FILE")" != "600" ]]; then
  echo ".env.smtp.local deve estar com modo 600." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

cd "$ROOT_DIR"
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml up -d postgres
exec mvn -q -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 spring-boot:run
