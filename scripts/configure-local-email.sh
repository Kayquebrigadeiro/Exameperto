#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.smtp.local"

umask 077

read -r -p "E-mail Gmail autorizado (remetente e único destinatário): " mail_address
if [[ ! "$mail_address" =~ ^[[:alnum:]._%+-]+@gmail\.com$ ]]; then
  echo "Endereço Gmail inválido." >&2
  exit 1
fi

read -r -s -p "Senha de app Google (não a senha normal): " app_password
echo
app_password="${app_password//[[:space:]]/}"
if [[ ! "$app_password" =~ ^[[:alpha:]]{16}$ ]]; then
  echo "A senha de app deve ter 16 letras; espaços são aceitos e removidos." >&2
  exit 1
fi

postgres_password="$(openssl rand -hex 24)"
data_key="$(openssl rand -base64 32 | tr -d '\n')"
search_key="$(openssl rand -base64 32 | tr -d '\n')"
signing_key="$(openssl rand -base64 32 | tr -d '\n')"

if [[ -e "$ENV_FILE" ]]; then
  echo "O arquivo privado já existe; remova-o manualmente se quiser recriá-lo." >&2
  exit 1
fi

{
  printf '%s\n' "EXAME_PERTO_POSTGRES_PASSWORD=$postgres_password"
  printf '%s\n' "DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/exameperto"
  printf '%s\n' "DATABASE_USER=exameperto"
  printf '%s\n' "DATABASE_PASSWORD=$postgres_password"
  printf '%s\n' "SPRING_MAIL_HOST=smtp.gmail.com"
  printf '%s\n' "SPRING_MAIL_PORT=587"
  printf '%s\n' "SPRING_MAIL_USERNAME=$mail_address"
  printf '%s\n' "SPRING_MAIL_PASSWORD=$app_password"
  printf '%s\n' "EXAME_PERTO_EMAIL_FROM=$mail_address"
  printf '%s\n' "EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS=$mail_address"
  printf '%s\n' "EXAME_PERTO_EMAIL_ENABLED=true"
  printf '%s\n' "EXAME_PERTO_DATA_KEY=$data_key"
  printf '%s\n' "EXAME_PERTO_SEARCH_KEY=$search_key"
  printf '%s\n' "EXAME_PERTO_SIGNING_KEY=$signing_key"
  printf '%s\n' "REGISTRATION_PRIVACY_APPROVED=false"
  printf '%s\n' "REGISTRATION_ALLOWED_ORIGIN=http://localhost:5173"
} > "$ENV_FILE"

chmod 600 "$ENV_FILE"
unset app_password postgres_password data_key search_key signing_key

echo "Configuração privada criada em .env.smtp.local (modo 600)."
echo "A política de cadastro permaneceu desabilitada; não a altere sem decisão explícita."
