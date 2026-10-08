# Encerramento e transformação controlada da conta (V17)

Esta é a especificação do ensaio local autorizado a partir de `a97ffef`. Ele usa somente dados sintéticos, PostgreSQL e armazenamento descartáveis. `ENCERRADA` significa que a conta foi encerrada e os campos controlados foram transformados; não significa anonimização integral.

## Inventário e destino por categoria

| Categoria e vínculos | Excluir no expurgo | Transformar/revogar | Conservar sob regra específica | Política ou dependência pendente |
|---|---|---|---|---|
| Perfil, e-mail, telefone, nome, senha, CPF, nascimento e estado | Segredos de autenticação temporários | Nome/e-mail pseudônimos cifrados, telefone nulo, senha aleatória, CPF/HMAC substituídos, nascimento sentinela, identidade rejeitada, conta `ENCERRADO` | UUID/FKs, protocolo, auditoria e recibo mínimo | Mapeamentos internos, chaves e logs ainda podem reidentificar; não é anonimização integral |
| Sessões, refresh/access tokens, desafios, MFA, papéis e conexões ativas | Desafios/tokens e segredo MFA; conexões locais são encerradas | Sessões revogadas, concessões/convites/membros/papéis revogados, entregador suspenso, live registry revogado | Eventos de auditoria sem segredo | Dispositivos já conectados e fornecedores não controlados |
| Documentos, fotos, versões, temporários, objetos privados, GPS, fila e cache | Recursos não financeiros abrangidos por política, inclusive objetos e posições | Linhas saneadas/tombstones quando a FK exige preservação | Evidências financeiras e comprovantes | Retenção legal/financeira, temporários órfãos, backups e fornecedores exigem regra aprovada |
| Endereços e coordenadas de pedidos | Conteúdo operacional removível quando permitido | Endereço cifrado substituído por marcador e coordenadas zeradas para preservar vínculo do pedido | Pedido, chave de deduplicação e trilha financeira | Critério probatório e prazo do endereço ainda pendentes |
| Autorizações familiares e institucionais | Segredos de convite e tokens | Concessões, convites aceitos, membros e papéis revogados; não há cascata silenciosa | Histórico mínimo da decisão | Representação legal/incapacidade e retenção da prova de autorização |
| Pedidos, custódia, operações, repasses, subsídios e reservas | Nunca por cascata | Bloqueia conclusão enquanto houver custódia aberta, operação incerta/em processamento, obrigação de repasse ou reserva ativa | Lançamentos, comprovantes, invariantes financeiras e chaves de deduplicação | Prazos financeiros, disputa, reconciliação e descarte de evidências não aprovados |
| Solicitações, expurgos, auditoria e diário | Conteúdo de resposta somente conforme política | Estado de encerramento e execução são atualizados idempotentemente | Protocolo, tombstone, recibo e diário independente permanecem | Logs operacionais, rotação de chaves, acesso de custódia e retenção específica |

Hash de CPF/e-mail não é anonimização: HMAC, UUID, relações, diário e ciphertexts mantêm possibilidade de correlação enquanto as chaves/mapeamentos existirem.

## Procedimento e garantias

1. O titular autenticado cria a solicitação; outra conta/familiar não consegue assumir o protocolo. Operador autoriza com papel nominal e MFA.
2. O executor inventaria os alvos, adquire locks e pode ser interrompido. Reexecução é idempotente; `encerramento_conta` usa estado/versionamento e registra `BLOQUEADA` com motivo quando há custódia ou obrigação pendente.
3. Após o expurgo verificável, V17 revoga sessões, tokens, MFA, concessões, convites, papéis e conexões locais, transforma campos permitidos e marca a conta `ENCERRADO`. Uma obrigação não é apagada nem abandonada.
4. O backup/restore usa `ops/recovery/`. O diário PostgreSQL independente recebe a conclusão e as transformações necessárias. A cópia antiga permanece bloqueada, reaplica expurgos e encerramentos, verifica ausência/invariantes e só então pode receber `release-access.sh`.
5. O ensaio deve desligar SMTP, pagamentos, repasses, webhooks, workers e adaptadores externos. Falha de integridade, interrupção ou dependência ausente deixa o gate bloqueado; não há fallback inseguro.

Comandos principais: `ops/recovery/backup.sh`, `capture-purges.sh`, `restore.sh`, `verify-restore.sh` e `release-access.sh`, sempre com URLs de banco e diretórios temporários descartáveis. O roteiro não sobrescreve banco ou arquivos da aplicação do autor.

## Evidência e limites

`PrivacyFlowTest` cobre terceiro/familiar sem autorização, sessão/conexão ativa, bloqueio por obrigação, retomada e reexecução, referências indiretas e preservação financeira. `BackupRestoreFlowTest` cobre backup anterior ao expurgo, diário externo, restauração isolada, reaplicação de perfil/endereço, interrupção, retry e corrupção recusada. Os testes medem duração local, não RPO/RTO.

Não foram usados dados reais, contratação, deploy, fornecedores ou dispositivos. A exclusão em cópias de terceiros, e-mails já recebidos, caches de dispositivos, PITR e chaves fora do ambiente não é comprovada. A conclusão local não autoriza declarar recuperação de desastre nem anonimização integral.
