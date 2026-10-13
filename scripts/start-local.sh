#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"
RUN_DIR="$ROOT_DIR/.local-run"

fail() { echo "FALHA: $1" >&2; exit 1; }
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
wait_for_port() {
  local name="$1" port="$2" attempts=0
  until port_open "$port"; do
    attempts=$((attempts + 1))
    [[ "$attempts" -lt 60 ]] || fail "$name não respondeu na porta $port; consulte .local-run/$name.log"
    sleep 1
  done
  echo "OK: $name disponível em 127.0.0.1:$port"
}

for command in docker java mvn node npm curl; do
  command -v "$command" >/dev/null || fail "dependência ausente: $command"
done
docker info >/dev/null 2>&1 || fail "Docker não está disponível"
[[ -f "$ENV_FILE" ]] || fail ".env.smtp.local ausente; execute scripts/configure-local-email.sh"
[[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || fail ".env.smtp.local deve ter modo 600"

mkdir -p "$RUN_DIR"
chmod 700 "$RUN_DIR"
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

echo "Dependências locais verificadas: Docker, Java, Maven, Node, npm e curl."
docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/infra/compose.yaml" up -d postgres
wait_for_port postgres 5433

if port_open 8080; then
  [[ -f "$RUN_DIR/backend.pid" ]] || fail "porta 8080 ocupada por processo não iniciado por este script"
  echo "OK: backend já estava disponível; nenhum processo duplicado foi criado"
else
  (cd "$ROOT_DIR" && nohup setsid bash -c 'pgid="$(ps -o pgid= -p $$ | tr -d " ")"; printf "%s\n" "$pgid" > "$1"; shift; exec "$@"' bash "$RUN_DIR/backend.pid" mvn -q -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 spring-boot:run >"$RUN_DIR/backend.log" 2>&1 &)
  wait_for_port backend 8080
fi

if port_open 5173; then
  [[ -f "$RUN_DIR/web.pid" ]] || fail "porta 5173 ocupada por processo não iniciado por este script"
  echo "OK: web já estava disponível; nenhum processo duplicado foi criado"
else
  (cd "$ROOT_DIR" && nohup setsid bash -c 'pgid="$(ps -o pgid= -p $$ | tr -d " ")"; printf "%s\n" "$pgid" > "$1"; shift; exec "$@"' bash "$RUN_DIR/web.pid" npm run dev --prefix web >"$RUN_DIR/web.log" 2>&1 &)
  wait_for_port web 5173
fi

"$ROOT_DIR/scripts/verify-local-environment.sh"
echo "PRONTO: acesse http://127.0.0.1:5173/"
echo "Logs locais sanitizados ficam em .local-run/; pare com scripts/stop-local.sh."
