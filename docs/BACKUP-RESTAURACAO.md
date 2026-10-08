# Backup e restauração com reaplicação de expurgos

Escopo comprovado em 08/10/2026: PostgreSQL 17.6 e o adaptador local de objetos privados, em três ambientes Testcontainers descartáveis. Este procedimento não define prazo de retenção, rotação, RPO ou RTO e não comprova recuperação de desastre em infraestrutura real.

## Garantias e pré-condições

`ops/recovery/backup.sh` produz um diretório imutável com dump custom do PostgreSQL, arquivo dos objetos privados, manifesto de formato/versões/dependências e `SHA256SUMS`. A consistência exige aplicação parada ou em manutenção, sem escritas no banco nem na raiz de objetos durante toda a cópia; o operador precisa confirmar `BACKUP_WRITES_QUIESCED=true`. O script não tenta congelar uma aplicação ainda ativa.

O diário `purge_completion` vive em outro PostgreSQL, com credencial e ciclo de backup próprios, e não entra na cópia da aplicação. Ele guarda somente execução, titular estável, categoria e horário verificado, sem conteúdo eliminado. `capture-purges.sh` copia somente expurgos `VERIFICADA` e é idempotente. Um job/alerta operacional que assegure captura após cada expurgo ainda precisa ser configurado no ambiente real; sem isso, produção permanece bloqueada.

A restauração exige banco vazio, diretório de objetos vazio, rede/credenciais isoladas e todos os efeitos externos desligados. A V16 mantém `controle_restauracao` diferente de `NORMAL` até verificação e liberação explícita; o filtro HTTP retorna `503 RESTORE_BLOCKED`, e o envio de e-mail também consulta o gate. Pagamento e repasse só são chamados por rotas HTTP atualmente bloqueadas, e os adaptadores padrão continuam indisponíveis. Não executar workers, consoles ou integrações que ignorem a aplicação durante a restauração.

## Comandos

Use URLs/segredos somente no ambiente ou gerenciador de segredos; não grave em shell history, Git ou logs. Os exemplos usam marcadores, não credenciais reais.

```bash
export BACKUP_DATABASE_URL='postgresql://<usuario>:<segredo>@<host>/<banco>'
export PRIVATE_OBJECT_ROOT='/caminho/privado'
export BACKUP_DESTINATION='/destino/imutavel/<backup-id>'
export APPLICATION_RELEASE='<sha-ou-release>'
export BACKUP_WRITES_QUIESCED=true
ops/recovery/backup.sh
```

Depois de cada conclusão de expurgo, e antes de considerar a exclusão recuperável:

```bash
export SOURCE_DATABASE_URL='postgresql://<usuario>:<segredo>@<host>/<banco>'
export PURGE_JOURNAL_DATABASE_URL='postgresql://<usuario-restrito>:<segredo>@<host-independente>/<diario>'
ops/recovery/capture-purges.sh
```

Restaure somente num alvo sem tabelas/objetos e inacessível à aplicação pública:

```bash
export RESTORE_DATABASE_URL='postgresql://<usuario>:<segredo>@<host-isolado>/<banco-vazio>'
export RESTORE_PRIVATE_OBJECT_ROOT='/caminho/isolado/vazio'
export RESTORE_BUNDLE='/destino/imutavel/<backup-id>'
export PURGE_JOURNAL_DATABASE_URL='postgresql://<usuario-restrito>:<segredo>@<host-independente>/<diario>'
export RESTORE_ISOLATION_CONFIRMED=true
export EXTERNAL_EFFECTS_DISABLED=true
ops/recovery/restore.sh
```

O resultado esperado é `state=VERIFICADO access=BLOCKED`. Revise os recibos em `restauracao_expurgo_aplicado`, as invariantes financeiras, logs saneados e a configuração do ambiente. Somente então:

```bash
export RESTORE_RELEASE_APPROVED=true
ops/recovery/release-access.sh
```

## Falhas e retomada

- Checksum/manifesto inválido: não restaure nem “corrija” o checksum. Isole a cópia, investigue a origem e selecione outro backup íntegro. SHA-256 detecta alteração acidental; sem assinatura/chave separada não prova autenticidade contra atacante.
- Falha antes da criação do gate: o destino continua vazio ou parcialmente restaurado dentro da transação única do `pg_restore`; descarte o alvo isolado ou repita somente se estiver vazio.
- Interrupção depois do restore: o `backup_id` e estado `BLOQUEADO` permanecem no alvo. Reexecutar o mesmo comando com o mesmo bundle retoma a reaplicação; outro `backup_id` é recusado.
- Falha em objeto, SQL ou verificação: não libere acesso. Preserve o alvo isolado para diagnóstico; corrija armazenamento/permissões e reexecute. Reaplicação e recibos são idempotentes.
- Diário indisponível: restauração não pode avançar. Não substitua o diário por tombstones da própria cópia antiga.
- Bundle contém artefato extra/duplicado, componente symlink, caminho absoluto, `..` ou symlink de objeto: restauração é recusada; não relaxe a validação.

## Categorias e limites

- Documentos comuns registrados e seus objetos, GPS ligado ao pedido e outbox não financeira do titular são reaplicados e verificados.
- `FINANCIAMENTO`, `COMPROVANTE`, operações, outbox financeira e chaves de deduplicação são preservados. Isso mantém as invariantes existentes, mas não aprova retenção financeira nem prazo infinito.
- Temporários não vinculados, versões de fornecedor, dispositivos e suboperadores continuam fora da comprovação local. Cada ambiente exige inventário/contrato e evidência próprios.
- A cópia contém GPS que existia no instante do backup; a reaplicação evita que GPS já expurgado volte após restore. A política/janela de GPS ainda está pendente e o recurso permanece desabilitado para dados reais.
- O bundle não cifra por si só. O destino real deve fornecer criptografia, acesso mínimo, auditoria e chaves separadas; essa infraestrutura não foi disponibilizada nem homologada nesta rodada.
- O ensaio cobre cópia lógica íntegra, restauração isolada, expurgo posterior, interrupção, retry idempotente e corrupção detectada. Não cobre perda do host/fornecedor/região, point-in-time recovery, grande volume, rotação, ataque ao diário, falha simultânea, credenciais/chaves perdidas ou RPO/RTO aprovado.

## Ensaio reproduzível

```bash
mvn -q -f backend/pom.xml -Dtest=BackupRestoreFlowTest test
```

O teste cria apenas dados/objetos sintéticos. Na execução focal final de 08/10/2026 passou com PostgreSQL 17.6: 1 teste, 0 falhas/erros, 21,83 s para a classe incluindo seu provisionamento; o cenário medido pelo Surefire levou 4,280 s. Duração local não é RTO.
