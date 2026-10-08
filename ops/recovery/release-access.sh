#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_command psql; require_var RESTORE_DATABASE_URL
[[ "${RESTORE_RELEASE_APPROVED:-}" == true ]] || die "liberação exige RESTORE_RELEASE_APPROVED=true após revisão"
changed=$(psql_value "$RESTORE_DATABASE_URL" "UPDATE controle_restauracao SET estado='NORMAL',liberado_em=clock_timestamp(),updated_at=clock_timestamp() WHERE singleton AND estado='VERIFICADO' RETURNING 1")
[[ "$changed" == 1 ]] || die "restauração não está verificada"
printf 'state=NORMAL access=RELEASED\n'
