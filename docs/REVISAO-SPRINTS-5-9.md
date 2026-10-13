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
