#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"
RUN_DIR="$ROOT_DIR/.local-run"

stop_managed() {
  local name="$1"
  local pid_file="$RUN_DIR/$name.pid"
  if [[ ! -f "$pid_file" ]]; then
    echo "$name: nenhum processo gerenciado registrado."
    return
  fi
  local pid
  pid="$(<"$pid_file")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 -- "-$pid" 2>/dev/null; then
    command_line="$(ps -o args= -g "$pid" 2>/dev/null || true)"
    if [[ "$name" == "backend" && "$command_line" != *ExamePertoApplication* && "$command_line" != *spring-boot:run* ]] || [[ "$name" == "web" && "$command_line" != *AquiPerto/web* && "$command_line" != *"vite --host 127.0.0.1"* ]]; then
      echo "$name: o grupo $pid não corresponde ao serviço gerenciado; nada foi encerrado." >&2
      rm -f "$pid_file"
      return
    fi
    kill -- "-$pid"
    for _ in {1..20}; do kill -0 -- "-$pid" 2>/dev/null || break; sleep 0.25; done
    if kill -0 -- "-$pid" 2>/dev/null; then
      echo "$name ainda está encerrando; verifique o grupo $pid antes de agir." >&2
    else
      echo "$name encerrado."
    fi
  else
    echo "$name já estava parado."
  fi
  rm -f "$pid_file"
}

stop_managed web
stop_managed backend
"$ROOT_DIR/scripts/stop-local-antimalware.sh"
if [[ -f "$ENV_FILE" ]]; then
  docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/infra/compose.yaml" stop postgres
else
  echo "PostgreSQL não foi parado porque .env.smtp.local não foi encontrado." >&2
fi
echo "O volume exame_perto_postgres e os dados locais foram preservados."
