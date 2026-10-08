#!/usr/bin/env bash
set -euo pipefail
umask 077

die() { printf 'recovery: %s\n' "$*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || die "dependência ausente: $1"; }
require_var() { [[ -n "${!1:-}" ]] || die "variável obrigatória ausente: $1"; }
valid_uuid() { [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]]; }

psql_value() {
  local url="$1" sql="$2"
  psql "$url" -X --no-psqlrc -v ON_ERROR_STOP=1 -Atq -c "$sql"
}

verify_bundle() {
  local bundle="$1" digest name extra count=0
  local -A sums=()
  [[ -d "$bundle" ]] || die "bundle não encontrado: $bundle"
  [[ -f "$bundle/SHA256SUMS" && ! -L "$bundle/SHA256SUMS" ]] || die "índice de integridade ausente ou inseguro"
  while read -r digest name extra; do
    [[ -z "${extra:-}" && "$digest" =~ ^[0-9a-f]{64}$ ]] || die "índice de integridade inválido"
    case "$name" in database.dump|private-objects.tar.gz|manifest.env) ;; *) die "artefato inesperado no índice: $name" ;; esac
    [[ -z "${sums[$name]:-}" ]] || die "artefato duplicado no índice: $name"
    sums[$name]=1; count=$((count+1))
  done < "$bundle/SHA256SUMS"
  [[ "$count" == 3 ]] || die "índice de integridade incompleto"
  for name in database.dump private-objects.tar.gz manifest.env; do
    [[ -n "${sums[$name]:-}" && -f "$bundle/$name" && ! -L "$bundle/$name" ]] || die "bundle incompleto ou inseguro"
  done
  (cd "$bundle" && sha256sum --check --strict SHA256SUMS >/dev/null) || die "falha de integridade do bundle"
  # O manifesto é deliberadamente shell-safe: somente atribuições com valores alfanuméricos restritos.
  while IFS= read -r line; do
    [[ "$line" =~ ^[A-Z0-9_]+=[A-Za-z0-9_.:+@/-]+$ ]] || die "manifesto inválido"
  done < "$bundle/manifest.env"
}
