#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"

if [[ ! -f "$ENV_FILE" ]] || [[ "$(stat -c '%a' "$ENV_FILE")" != "600" ]]; then
  echo "A configuração privada .env.smtp.local é obrigatória e deve estar com modo 600." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

cd "$ROOT_DIR"
docker compose --env-file "$ENV_FILE" -f infra/compose.yaml up -d postgres

echo "A ferramenta só altera papéis de uma conta ativa e registra cada concessão/revogação."
echo "Ela não cria instituição, política, saldo, aprovação ou evidência."
EXAME_PERTO_EMAIL_ENABLED=false exec mvn -q -f backend/pom.xml \
  -Dmaven.repo.local=/tmp/exame-m2 \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --local.provision.enabled=true --email.enabled=false" \
  spring-boot:run

