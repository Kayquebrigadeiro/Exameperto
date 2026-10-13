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

Este documento registra o erro do fechamento anterior; a evidência final e as lacunas remanescentes serão atualizadas somente depois das novas execuções.

## Fechamento após a rodada de correção — 13/10/2026

| Sprint | Telas e ações implementadas (URLs) | Jornada efetivamente testada no navegador | Lacunas técnicas restantes | Dependências externas específicas | Situação |
| --- | --- | --- | --- | --- | --- |
| 5 | `/app/entregador`: cadastro, upload em quarentena, substituição de documento, veículo e vínculo; `/app/entregas`: ofertas e histórico. | `DelivererBrowserFlowTest` / `deliverer-real.spec.ts`: login, cadastro, HABILITACAO, veículo, dois CRLV com substituição, bloqueio sem aprovação e estado vazio; Chromium → Vite → API → PostgreSQL real, sem interceptação. | Decisão profissional/autenticidade não é automatizada; GPS contínuo permanece mobile. | Serviço oficial de habilitação, antimalware/armazenamento definitivo, aprovação profissional e Android/GPS. | **Validada localmente no recorte web** (commit `d48c177`), não operação real. |
| 6 | `/app/operacao`: fila/inspeção/decisão de entregador e veículo; `/app/analise-beneficios`: fila, evidência, decisão; `/app/operacao-privacidade`: resposta, autorização, execução/verificação de expurgo. | Há testes de serviço para as três regras, mas não existe ainda uma jornada Playwright dedicada que atravesse as três áreas privilegiadas com papéis nominais e MFA. | Falta prova de navegador de cada ação, incluindo bloqueios por autoanálise, instituição e política; não confundir login público com isso. | Analista AO/AB/OP independentes, instituição/política/responsável reais, retenção/backup e antimalware. | **Parcial; não concluída.** |
| 7 | `/app/entregas`: aceitar oferta, endereços após vínculo, retirada, início, entrega por código e ocorrência; `/app/pedidos`: orçamento, designação, custódia, ocorrência e acompanhamento. | `CustodyBrowserFlowTest` e `AssignmentBrowserFlowTest` usam navegador real, mas parte das mutações de custódia/designação é feita por `fetch` no próprio teste antes/depois da tela; não é uma jornada integral do entregador pela interface. | Falta fluxo Playwright integral para aceitar oferta, liberar endereço, custódia e acompanhamento; GPS real não foi capturado. | Unidade/protocolo/cobertura de retorno, rota, participantes/destinatário, aparelho Android, permissões e retenção GPS. | **Parcial; não concluída.** |
| 8 | `/app/financeiro`: MFA, aportes/revisão e livro de obrigações; ações `Solicitar repasse`/`Conciliar referência` preservam referência única. | `FundingBrowserFlowTest` cobre aporte/revisão; `PayoutFlowTest` foi ampliado para elevar MFA, solicitar repasse pela tela e confirmar estado no PostgreSQL com provedor controlado no fixture. Isolamento negativo institucional continua coberto por serviço, não pela tela. | Falta prova negativa no navegador com segunda instituição e conciliação de divergência/incerteza pela tela. Provedor controlado não homologa transferência. | Banco/provedor de pagamento e repasse, conta beneficiária, dois gestores independentes, instituição/programa/política financeira reais. | **Parcial; ações locais cobertas, isolamento web pendente.** |
| 9 | Rotas públicas `/`, `/acesso`, `/cadastro`, `/confirmacao`, `/confirmacao/reenviar`, `/recuperacao`, `/recuperacao/nova-senha`; rotas `/app/*` conforme capacidades. | `AcceptanceBrowserFlowTest` cobre somente aceite de orçamento e permanência de pagamento pendente. Não houve aceite integrado de todas as áreas e papéis. | Falta matriz de aceitação multi-papel, regressão entre jornadas, isolamento institucional e critérios de saída; não declarar o produto inteiro aceito. | Todas as dependências de 5–8, participantes independentes, SMTP real, rota/pagamento/repasse, políticas, unidade e Android. | **Não concluída.** |

As telas permanecem navegáveis quando a capacidade existe, mas ausência, `MFA_REQUIRED`, `POLICY_UNDEFINED` e `INTEGRATION_UNAVAILABLE` são estados de bloqueio válidos. Nenhum fixture cria conta, saldo, instituição ou aprovação no ambiente local do usuário.
