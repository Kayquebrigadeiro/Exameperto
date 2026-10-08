# Revisão consolidada de prontidão

Data: 08/10/2026 · base auditada: `3b78b64` · branch: `docs/planejamento-tecnico`.

Esta revisão conferiu o código, migrações, configuração e testes atuais; os relatórios anteriores foram usados apenas como índice. Não houve deploy, operação externa, movimentação financeira ou exclusão de dados reais.

## Decisão curta

O recorte local é verificável em banco/objetos descartáveis e a interface web pode ser publicada somente como portfólio honesto, com integrações indisponíveis e sem cadastros, saldos ou contas fictícias. A operação real com pacientes, entregadores e instituições não está pronta: backup/restore, anonimização integral, políticas financeiras/retensão, homologações externas, aparelho móvel e operação privilegiada ainda bloqueiam publicação operacional.

## Prioridades auditadas

| Prioridade | O que o código prova | Lacuna/bloqueio |
|---|---|---|
| Backup, restauração e reaplicação | V15 registra tombstone e exige reconferência; retry é idempotente nos recursos locais | Não há infraestrutura de backup/restore, ensaio RPO/RTO, nem reaplicação automática de expurgos antes de reabrir acesso |
| Anonimização e finanças | Documentos/GPS/outbox não financeira são saneados; operação, comprovante, outbox financeira e deduplicação são preservados | Não há anonimização integral de `usuario`, paciente, sessões, representações e referências operacionais; retenção financeira ainda não foi aprovada |
| Permissões e sessões | Auth revalida sessão/acesso; WebSocket rejeita CONNECT/SUBSCRIBE/SEND indevidos e remove conexão aberta revogada no próximo publish | Falta homologação externa de identidade e ensaio prolongado em infraestrutura real; nenhum bug demonstrado nesta rodada |
| Recursos de teste | Defaults desabilitam e-mail, tracking e purge; não há contas/seed fictícios em `main`; mocks ficam nos testes | Ainda falta uma verificação automatizada de que perfil normal nunca habilita recursos exclusivos de teste |
| Financeiro | Reserva/custódia/obrigação/repasse têm transação, locks, referências estáveis, deduplicação e estados INCERTO/DIVERGENTE | Provedor real, cancelamento, parcialidade, disputa, beneficiários e homologação contábil/fiscal não existem |
| Mobile/dependências | Typecheck/export Android e 20/21 checks do Expo Doctor passam; web audit sem achados | Não há aparelho; `expo-location`/Expo têm incompatibilidade de patch e mobile mantém 23 avisos (16 altos, 7 moderados) |

## Matriz dos tickets

| Ticket | Implementado e evidência local | Ausente tecnicamente | Homologação externa | Impacto local / portfólio / operação real |
|---|---|---|---|---|
| 01 | Decisões de comprovação, representação e custeio refletidas como guardas; critérios operacionais continuam abertos | Política aprovada, responsáveis e cancelamento | Revisão humana/institucional | Local executa limites; portfólio deve mostrar bloqueios; real bloqueado |
| 02 | Matriz de privacidade, acesso privilegiado e provedores usada por API/segurança | Inventário completo por categoria e controlador | Provedores, identidade e contratos | Local isolado; web sem integrações; real bloqueado |
| 03 | Cadastro/sessão/recuperação e expiração testados em `AuthFlowTest`/`Registration*` | E-mail transacional real e recuperação operacional | SMTP e suporte | Local verificável; portfólio não promete e-mail; real pendente |
| 04 | Representação concedida/revogada e escopo testados em `RepresentationFlowTest` | Fluxos humanos/documentais completos | Aprovação profissional/familiar | Local limitado; portfólio sem contas reais; real pendente |
| 05 | Comprovações e revisão persistem com escopo/versão | Reuso por outro entregador e decisão profissional | Antimalware, revisão e dispositivos | Local parcial; portfólio pode exibir fluxo; real não |
| 06 | Vínculo veículo/entregador e upgrade V5→V15 em `VehicleLinkFlowTest` | Suspensão e estado operacional completo | Consulta documental/órgão | Local parcial; web sem operação; real pendente |
| 07 | Benefício, revisão distinta, MFA e isolamento em `BenefitFlowTest`/`FundingFlowTest` | Política e aprovação profissional | Instituições e elegibilidade | Local sintético; portfólio sem saldo; real bloqueado |
| 08 | Aporte/reserva/ledger e replay idempotente | Provedor, disputa e política financeira | Banco/instituição | Local com mocks controlados; web declara indisponível; real bloqueado |
| 09 | Pedido, cobertura e orçamento com adaptador indisponível por padrão | Rota/preço/cancelamento reais | Mapas, unidade e política | Local sintético; portfólio sem tarifa real; real pendente |
| 10 | Aceite, cobertura e outbox com invariantes de valor | Pagamento, cancelamento e parcialidade | PSP/banco e instituição | Local sem dinheiro; portfólio não simula saldo; real bloqueado |
| 11 | Oferta/designação, concorrência e escopo em `AssignmentFlowTest` e browser opt-in | Suspensão/cancelamento pós-designação | Capacidade/cobertura real | Local verificável; web demonstra apenas shell; real pendente |
| 12 | Custódia, ocorrência e código de recebimento em `CustodyFlowTest` e browser | Retorno/reentrega/cancelamento e liquidação completa | Entrega física e operação | Local sintético; portfólio sem promessa de entrega; real bloqueado |
| 13 | GPS HTTP/STOMP, reautorização e conexão revogada em `TrackingFlowTest` | Job/retenção GPS e ensaio físico/background | Android, rede, bateria e política | Local verificável; web sem rastreamento real; real pendente |
| 14 | Apuração, obrigação, repasse e conciliação em transação; `PayoutFlowTest` + browser | Provedor, beneficiários, disputa e regras fiscal/contábil | Homologação bancária | Local sem dinheiro; portfólio sem saldo simulado; real bloqueado |
| 15 | Solicitações próprias ACESSO/CORRECAO/EXCLUSAO, protocolo, autorização, expurgo idempotente e retenção versionada em `PrivacyFlowTest` | Backup/restore, scheduler, anonimização integral e execução externa | Fornecedores, backups, políticas e controlador | Local verificável; portfólio só informa limitações; real bloqueado |

## Testes e composição da contagem

`mvn -q test` descobriu 57 casos: 49 executados (0 falhas/erros) e 8 opt-ins ignorados sem `browserTest=true`. Os oito opt-ins são `BrowserFlowTest`, `FamilyBrowserFlowTest`, `FundingBrowserFlowTest`, `OrderBrowserFlowTest`, `AcceptanceBrowserFlowTest`, `AssignmentBrowserFlowTest`, `CustodyBrowserFlowTest` e o caso browser de `PayoutFlowTest`. Com `-DbrowserTest=true`, os oito passaram após corrigir a mensagem de integração indisponível em `web/src/main.tsx`; `npm run build && npm run test` também passou (2 testes comuns, 8 opt-ins ignorados).

Comparação verificável no histórico: ticket 13 tinha 50 casos/7 opt-ins; ticket 14, 53/7; a revisão atual tem 57/8. Não houve renomeação ou remoção (`git diff --name-status 719357e..3b78b64 -- backend/src/test/java` mostra apenas adições de `PayoutFlowTest`, `PrivacyFlowTest` e alteração de `VehicleLinkFlowTest`). O aumento resulta de quatro casos de repasse, três de privacidade e um caso adicional de migração; o oitavo opt-in é o browser financeiro. Logo, a variação não indica regressão por quantidade.

Verificações adicionais: foco `PrivacyFlowTest,TrackingFlowTest,PayoutFlowTest,CustodyFlowTest,QuoteAcceptanceFlowTest,AssignmentFlowTest,RepresentationFlowTest,AuthFlowTest,VehicleLinkFlowTest`; Flyway V1–V15 em banco novo; upgrade V5→V15; OpenAPI 3.0.3 (126 operações, referências internas resolvidas); links/diagramas; padrões de segredo; permissões de armazenamento privado. Mobile typecheck/export passaram sem aparelho. O audit mobile permanece pendente; não há scanner CVE JVM configurado.

## Três recortes

1. **Versão local verificável:** banco PostgreSQL/Testcontainers e objetos temporários, dados sintéticos, integrações padrão desligadas, testes HTTP/STOMP/browser e migrações. É adequada para revisão técnica, não para pacientes reais.
2. **Portfólio publicável:** somente shell/web/documentação que declare “integração indisponível”, sem contas fictícias, saldo simulado, seed produtivo ou botão anunciado como funcional quando bloqueado. Cadastro, pagamento, rota, e-mail, GPS e repasse devem permanecer claramente desabilitados.
3. **Operação real:** não liberada. Exige os tickets de seguimento abaixo, política financeira/retensão aprovada, backup/restore ensaiado, homologações, aparelho móvel, observabilidade, resposta a incidentes e revisão de publicação.

## Tickets pequenos propostos

- **P0 — Backup/restore com reaplicação:** backup isolado versionado; restaurar cópia, reaplicar tombstones/expurgos antes de liberar sessão e produzir evidência auditável; critérios: ensaio repetível, falha recuperável, RPO/RTO aprovados e nenhum dado eliminado reaberto.
- **P0 — Anonimização integral por categoria:** inventariar conta, paciente, sessão, representação, documentos e índices; anonimizar apenas campos autorizados e preservar referências financeiras justificadas; critérios: teste de ausência por recurso, vínculo financeiro íntegro e aprovação do responsável.
- **P0 — Retenção financeira e deduplicação:** decidir prazo, disputa, obrigação e chaves; critérios: nenhuma exclusão sem regra ativa, expurgo idempotente e trilha de fundamento/escopo/responsável.
- **P1 — Guarda de configuração normal:** teste CI que inicie perfil normal e prove que recursos `TEST`, seeds, mocks e endpoints de ensaio não são acessíveis; critérios: falha fechada e relatório de configuração.
- **P1 — Sessões/WebSocket e instituições:** ensaio de revogação em múltiplas instâncias/conexões e matriz entre contas/instituições; critérios: zero mensagem após revogação e auditoria de cada rejeição.
- **P1 — Mobile/dependências:** alinhar versões Expo compatíveis, resolver avisos sem downgrade inseguro e executar Android físico/background/rede; critérios: doctor limpo, auditoria triada e evidência em aparelho.
- **P1 — Homologação financeira/fornecedores:** contratos, sandbox e confirmação externa para rota, e-mail, objeto, pagamento e repasse; critérios: timeout/conciliação sem duplicidade e relatório externo anexado.

Nenhum prazo acima é política operacional aprovada; são critérios de trabalho para decisão humana. Fornecedores, dispositivos e backups fora do controle local permanecem “procedimento/contrato pendente”, nunca “exclusão comprovada”.

**Conclusão:** a versão local é mantível e auditável, o recorte de portfólio pode ser honesto com limitações explícitas, e a operação real/publicação produtiva continuam bloqueadas.
