# Revisão de fechamento — sprints 5 a 9

Revisão iniciada em 13/10/2026 a partir do código, contrato, migrações e testes do repositório, e não do relatório de fechamento anterior. Os arquivos locais `sprints/6.md` a `sprints/9.md` estão sobrescritos com o texto da sprint 5; por isso os recortes corretos abaixo seguem os objetivos originais restabelecidos pelo autor.

## Estado encontrado antes da correção

| Sprint | Escopo original | Superfícies existentes | Evidência real encontrada | Situação inicial corrigida |
| --- | --- | --- | --- | --- |
| 5 | Entregador e veículos | `/app/entregador` e `/app/entregas` | O teste geral apenas abria o cadastro. Serviços isolados cobriam documentos e veículos. | Parcial; faltava uma jornada própria no navegador para cadastro, upload, substituição, veículo, bloqueio e ofertas/histórico. |
| 6 | Operação, análises e privacidade | `/app/operacao`, `/app/analise-beneficios` e `/app/operacao-privacidade` | Existiam testes de serviço, mas nenhum teste de navegador da operação privilegiada. | Parcial; autenticação pública não valida operação, análise ou privacidade operacional. |
| 7 | Entrega, custódia e acompanhamento | `/app/pedidos` e `/app/entregas` | `CustodyBrowserFlowTest` exercitava retirada, início, código, entrega, ocorrência e bloqueio de terceiro; `AssignmentBrowserFlowTest` exercitava acompanhamento da designação. | Implementada em parte; faltava demonstrar a jornada do entregador pela própria interface e a atualização de acompanhamento sem chamada direta criada pelo teste. GPS contínuo continua restrito ao mobile/aparelho. |
| 8 | Gestão financeira e isolamento institucional | `/app/financeiro` | `FundingBrowserFlowTest` exercitava aporte e dupla revisão. `payout-real.spec.ts` consultava a API diretamente e apenas verificava que o painel abriu. | Parcial; faltavam ações reais de repasse no navegador e prova negativa de isolamento entre instituições. |
| 9 | Aceitação integrada do produto inteiro | rotas públicas e todas as rotas `/app/*` permitidas por capacidade | `AcceptanceBrowserFlowTest` validava somente aceite de orçamento e permanência do pagamento pendente. | Não concluída; esse teste não constitui aceitação integrada do produto. |

## Critério para o novo fechamento

Cada sprint só pode ser marcada como validada quando uma jornada Playwright dedicada usar a página Vite, a API HTTP real e PostgreSQL descartável, sem interceptar a API. Preparação sintética é permitida apenas no fixture isolado do teste; a ação sob aceite deve ocorrer pela interface. Testes públicos de cadastro/autenticação não contam para operação ou financeiro.

As dependências externas não serão simuladas como sucesso: autenticidade documental e aprovação profissional, instituições e políticas reais, provedor de rota, pagamento e repasse, antimalware abrangente, participantes humanos independentes e aparelho Android/GPS permanecem condicionantes de homologação real.

## Inventário de URLs

- Sprint 5: `/app/entregador` para cadastro, documentos e veículos; `/app/entregas` para ofertas, tarefas, custódia e histórico.
- Sprint 6: `/app/operacao` para entregador e veículo; `/app/analise-beneficios` para benefícios; `/app/operacao-privacidade` para solicitações e expurgo.
- Sprint 7: `/app/entregas` para execução da tarefa; `/app/pedidos` para aceite, designação, código de recebimento, estado, ocorrência e última posição.
- Sprint 8: `/app/financeiro` para aportes, saldo institucional, obrigações, solicitação e conciliação de repasses.
- Sprint 9: `/`, `/acesso`, `/cadastro`, `/confirmacao`, `/confirmacao/reenviar`, `/recuperacao`, `/recuperacao/nova-senha` e as rotas autenticadas aplicáveis de `/app/*`.

Este documento registra o erro do fechamento anterior. A execução atual corrigiu e revalidou custódia, sessão HTTP de teste, versão de concorrência, navegação financeira e as áreas privilegiadas; a aceitação integrada e a homologação externa continuam explicitamente separadas abaixo.

## Evidência da rodada de 14/10/2026

| Frente | Resultado técnico | Evidência | Situação operacional |
| --- | --- | --- | --- |
| Entrega/custódia | **Passou**; o antigo 401 não era permissão: o fixture HTTP emitia cookie `Secure`, a interface usava a versão de `custodia` em vez da versão do pedido e a consulta de participante tinha referência inválida a `autorizacao_escopo.id`. | `CustodyBrowserFlowTest` 1/1: Chromium → Vite → API → PostgreSQL; retirada, custódia, início, código do destinatário, recebimento e ocorrência pela interface. Banco confirmou entrega encerrada e ocorrência com custódia aberta. | Não homologa unidade, protocolo, destinatário ou GPS/aparelho reais. |
| Atribuição e operação de entrega | **Passou** nos recortes existentes. | `AssignmentBrowserFlowTest`, `DelivererBrowserFlowTest`, `OrderBrowserFlowTest` e `AcceptanceBrowserFlowTest`; `AssignmentService` e `CustodyService` agora retornam a versão do pedido para `If-Match`. | Aceitação integrada completa e GPS real continuam pendentes. |
| Operação, benefícios e privacidade | **Passou** no recorte técnico local. | `PrivilegedOperationsBrowserFlowTest` 1/1: papéis sintéticos, MFA confirmado pela interface, atribuição/inspeção, autoanálise recusada, ausência de papel, sessão sem MFA, decisão de benefício e encerramento de privacidade; ações sob teste ocorreram pela UI. PostgreSQL confirmou a persistência e o titular de privacidade em `ENCERRADO`. | Não homologa analistas independentes, políticas/instituições reais, retenção, backup ou antimalware. |
| Financeiro | **Passou** localmente. | `FundingBrowserFlowTest` 1/1 e `PayoutFlowTest` 4/4; MFA, dupla revisão, repasse pela tela, instituição estrangeira recusada pela API, idempotência, `INCERTO`, `DIVERGENTE` e provedor indisponível. | Provedor controlado é fixture; não é homologação bancária externa. |
| Regressão | **Passou**. | Relatórios Maven comum e browser sem falhas/erros; `mvn ... -DbrowserTest=true` com 10 classes: 16/16 testes; `npm run build --prefix web`; `npm run test --prefix web`: 4 passaram e 10 foram skips condicionais. | Logs de proxy sem backend no Playwright público são esperados pelos testes de indisponibilidade; não são falhas. |

## Fechamento após a rodada de correção — 13/10/2026

| Sprint | Telas e ações implementadas (URLs) | Jornada efetivamente testada no navegador | Lacunas técnicas restantes | Dependências externas específicas | Situação |
| --- | --- | --- | --- | --- | --- |
| 5 | `/app/entregador`: cadastro, upload em quarentena, substituição de documento, veículo e vínculo; `/app/entregas`: ofertas e histórico. | `DelivererBrowserFlowTest` / `deliverer-real.spec.ts`: login, cadastro, HABILITACAO, veículo, dois CRLV com substituição, bloqueio sem aprovação e estado vazio; Chromium → Vite → API → PostgreSQL real, sem interceptação. | Decisão profissional/autenticidade não é automatizada; GPS contínuo permanece mobile. | Serviço oficial de habilitação, antimalware/armazenamento definitivo, aprovação profissional e Android/GPS. | **Validada localmente no recorte web** (commit `d48c177`), não operação real. |
| 6 | `/app/operacao`: fila/inspeção/decisão de entregador e veículo; `/app/analise-beneficios`: fila, evidência, decisão; `/app/operacao-privacidade`: resposta, autorização, execução/verificação de expurgo. | `PrivilegedOperationsBrowserFlowTest` / `privileged-real.spec.ts`: Chromium → Vite → API → PostgreSQL; atribuição/inspeção, autoanálise recusada, ausência de papel, sessão sem MFA, decisão de benefício e encerramento de privacidade. Fixtures SQL são sintéticos; ações sob teste ocorreram pela interface. | Aceitação operacional externa, retenção/backup e antimalware não são simulados. | Analistas AO/AB/OP independentes, instituição/política/responsável reais, retenção/backup e antimalware. | **Validada tecnicamente no ambiente isolado; homologação operacional externa pendente.** |
| 7 | `/app/entregas`: aceitar oferta, endereços após vínculo, retirada, início, entrega por código e ocorrência; `/app/pedidos`: orçamento, designação, custódia, ocorrência e acompanhamento. | `CustodyBrowserFlowTest` executa pela interface as mutações de retirada, início, ocorrência e recebimento, com destinatário em contexto separado e terceiro recusado por chamada direta; `AssignmentBrowserFlowTest` acompanha designação; `DelivererBrowserFlowTest` cobre pendência e bloqueio de oferta. | Aceitar oferta e liberação de endereço ainda não formam uma única jornada Playwright; GPS real não foi capturado. | Unidade/protocolo/cobertura de retorno, rota, participantes/destinatário, aparelho Android, permissões e retenção GPS. | **Validada localmente nos recortes disponíveis; integrada completa pendente.** |
| 8 | `/app/financeiro`: MFA, aportes/revisão e livro de obrigações; ações `Solicitar repasse`/`Conciliar referência` preservam referência única. | `FundingBrowserFlowTest` e `PayoutFlowTest` passaram pela tela; `PayoutFlowTest` também recusou instituição cruzada por API e cobriu incerteza, divergência e idempotência. | A tela não tem cenário de duas instituições simultâneas; a prova negativa cruzada permanece API/serviço, separada da homologação bancária. | Banco/provedor de pagamento e repasse, conta beneficiária, dois gestores independentes, instituição/programa/política financeira reais. | **Validada tecnicamente em ambiente isolado; homologação bancária externa pendente.** |
| 9 | Rotas públicas `/`, `/acesso`, `/cadastro`, `/confirmacao`, `/confirmacao/reenviar`, `/recuperacao`, `/recuperacao/nova-senha`; rotas `/app/*` conforme capacidades. | `AcceptanceBrowserFlowTest` cobre somente aceite de orçamento e permanência de pagamento pendente. Não houve aceite integrado de todas as áreas e papéis. | Falta matriz de aceitação multi-papel, regressão entre jornadas, isolamento institucional e critérios de saída; não declarar o produto inteiro aceito. | Todas as dependências de 5–8, participantes independentes, SMTP real, rota/pagamento/repasse, políticas, unidade e Android. | **Não concluída.** |

As telas permanecem navegáveis quando a capacidade existe, mas ausência, `MFA_REQUIRED`, `POLICY_UNDEFINED` e `INTEGRATION_UNAVAILABLE` são estados de bloqueio válidos. Nenhum fixture cria conta, saldo, instituição ou aprovação no ambiente local do usuário.

## Execuções registradas

- `mvn -DbrowserTest=true -Dtest=DelivererBrowserFlowTest test`: **1/1 passou**; entrega `d48c177`.
- `mvn -DbrowserTest=true -Dtest=PayoutFlowTest#browserRendersAndActsOnRealFinancialPanel test`: **1/1 passou**; entrega `bdb48ce`.
- `mvn -DbrowserTest=true -Dtest=AssignmentBrowserFlowTest,CustodyBrowserFlowTest,AcceptanceBrowserFlowTest test`: Assignment passou; Custody falhou no fechamento com `401` depois de preparar a custódia por `fetch`; Acceptance permanece recorte de aceite de orçamento. Isso não é aceite das sprints 7 ou 9.
- `npm run build` em `web/`: passou após a correção do fluxo de confirmação inicial do TOTP.
- `mvn -DbrowserTest=true -Dtest=PrivilegedOperationsBrowserFlowTest test`: **1/1 passou**; operação, benefícios e privacidade com papéis sintéticos e MFA confirmado na interface.
- `mvn -DbrowserTest=true -Dtest=BrowserFlowTest,FamilyBrowserFlowTest,OrderBrowserFlowTest,AssignmentBrowserFlowTest,CustodyBrowserFlowTest,DelivererBrowserFlowTest,FundingBrowserFlowTest,PayoutFlowTest,AcceptanceBrowserFlowTest,PrivilegedOperationsBrowserFlowTest test`: **16/16 passaram**, sem falhas/erros nos relatórios.
- `npm test --prefix web`: **4 passaram**, **10 skips condicionais**, sem falhas; `npm run build --prefix web`: passou.
