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
wait_for_http() {
  local name="$1" url="$2" expected="$3" attempts=0 status body_file
  body_file="$RUN_DIR/$name-readiness.json"
  until status="$(curl -sS -o "$body_file" -w '%{http_code}' --max-time 3 "$url" 2>/dev/null || true)" && [[ "$status" == "$expected" ]]; do
    attempts=$((attempts + 1))
    if [[ "$attempts" -ge 120 ]]; then
      echo "FALHA: $name não ficou pronto via HTTP em 120s (último HTTP ${status:-sem-resposta})." >&2
      [[ -f "$RUN_DIR/$name.log" ]] && tail -40 "$RUN_DIR/$name.log" |
        sed -E 's/([Pp]assword|[Pp]asswd|[Tt]oken|[Aa]uthorization|[Cc]ookie|[Rr]ecipient|[Ee]mail|[Tt]o|[Ff]rom)([=:])[[:space:]]*[^,[:space:]]+/\1\2[REDACTED]/g; s/[[:alnum:]._%+-]+@[[:alnum:].-]+\.[[:alpha:]]{2,}/[REDACTED_EMAIL]/g' >&2
      return 1
    fi
    sleep 1
  done
  echo "OK: $name respondeu HTTP $expected em $url"
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
fi
wait_for_http backend 'http://127.0.0.1:8080/api/v1/auth/csrf' 403

if port_open 5173; then
  [[ -f "$RUN_DIR/web.pid" ]] || fail "porta 5173 ocupada por processo não iniciado por este script"
  echo "OK: web já estava disponível; nenhum processo duplicado foi criado"
else
  (cd "$ROOT_DIR" && nohup setsid bash -c 'pgid="$(ps -o pgid= -p $$ | tr -d " ")"; printf "%s\n" "$pgid" > "$1"; shift; exec "$@"' bash "$RUN_DIR/web.pid" npm run dev --prefix web >"$RUN_DIR/web.log" 2>&1 &)
fi
wait_for_http web 'http://127.0.0.1:5173/' 200

"$ROOT_DIR/scripts/verify-local-environment.sh"
echo "PRONTO: acesse http://127.0.0.1:5173/"
echo "Logs locais sanitizados ficam em .local-run/; pare com scripts/stop-local.sh."
