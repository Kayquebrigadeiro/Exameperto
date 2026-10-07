#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
maven_repo="${EXAME_PERTO_MAVEN_REPO:-/tmp/exame-m2}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
export EXPO_HOME="${EXPO_HOME:-/tmp/exame-perto-expo}"
export EXPO_NO_TELEMETRY=1
unset EXAME_PERTO_EMAIL_ENABLED EXAME_PERTO_EMAIL_FROM
unset SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD

cd "$repo_root"
npm ci --prefix web
npm ci --prefix mobile
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" test
npm run build --prefix web
npm run test --prefix web
npm run typecheck --prefix mobile
npm run build:android --prefix mobile

# Evidências navegador → API → PostgreSQL são opt-in e separadas do export móvel.
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" -Dtest=OrderBrowserFlowTest -DbrowserTest=true test
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" -Dtest=AcceptanceBrowserFlowTest -DbrowserTest=true test
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" -Dtest=AssignmentBrowserFlowTest -DbrowserTest=true test
mvn -f backend/pom.xml -Dmaven.repo.local="$maven_repo" -DskipTests package

echo "Ticket 11: verificações locais concluídas; dispositivo, aprovações/cobertura reais e integrações externas não exercitados."
