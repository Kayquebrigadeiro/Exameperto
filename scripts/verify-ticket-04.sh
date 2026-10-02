#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
maven_repo="${EXAME_PERTO_MAVEN_REPO:-/tmp/exame-m2}"

# A verificação local nunca usa o SMTP eventualmente configurado no shell.
unset EXAME_PERTO_EMAIL_ENABLED EXAME_PERTO_EMAIL_FROM
unset SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD

cd "$repo_root"
npm ci --prefix web
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" test
npm run build --prefix web
npm run test --prefix web -- tests/registration.spec.ts
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" \
  -Dtest=BrowserFlowTest,FamilyBrowserFlowTest -DbrowserTest=true test
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" -DskipTests package

application_jar="$(find backend/target -maxdepth 1 -type f -name 'exame-perto-*.jar' ! -name '*.original' -print -quit)"
if [[ -z "$application_jar" ]]; then
  echo "JAR da aplicação não encontrado." >&2
  exit 1
fi
if jar tf "$application_jar" | grep -Eq '(BrowserFlowTest|FamilyBrowserFlowTest|AuthFlowTest|RepresentationFlowTest|PersistenceRestartTest|SmtpFailureTest|RegistrationTest).*\.class$'; then
  echo "Classe exclusiva de teste encontrada no JAR da aplicação." >&2
  exit 1
fi
if unzip -p "$application_jar" 'BOOT-INF/classes/*' 2>/dev/null \
    | strings | grep -Eq 'TEST_MAILBOX|TEST_FAMILY_MAILBOX|exame-(family-)?browser-mail-|family-browser-playwright\.log|browser-playwright\.log'; then
  echo "Mecanismo de captura exclusivo de teste encontrado nas classes de produção." >&2
  exit 1
fi
echo "Ticket 04: verificações locais completas aprovadas; SMTP externo não exercitado."
