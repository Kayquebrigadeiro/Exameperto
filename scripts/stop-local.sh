#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"

echo "Encerre backend e Vite com Ctrl+C nos terminais em que foram iniciados."
if [[ -f "$ENV_FILE" ]]; then
  docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/infra/compose.yaml" stop postgres
else
  echo "PostgreSQL não foi parado porque .env.smtp.local não foi encontrado." >&2
fi
echo "O volume exame_perto_postgres e os dados locais foram preservados."

