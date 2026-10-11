#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if (exec 3<>/dev/tcp/127.0.0.1/5173) 2>/dev/null; then
  exec 3>&-
  echo "A porta 5173 já está em uso; use o Vite existente ou encerre-o antes de iniciar outro." >&2
  exit 1
fi

cd "$ROOT_DIR"
exec npm run dev --prefix web
