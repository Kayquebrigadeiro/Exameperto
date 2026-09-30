# Especificação — planejamento técnico do Exame Perto

**Status:** ready-for-human — entregáveis documentais para revisão; implementação não autorizada.
**Base Git:** inexistente no workspace recebido; `.git` vazio, sem HEAD, branch ou remoto. A base inicial foi criada preservando os cinco arquivos recebidos: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`. Branch `docs/planejamento-tecnico`; remoto do aplicativo confirmado pelo usuário, vazio na consulta inicial.

## Problem Statement

Pacientes precisam receber resultados em envelope fechado sem novo deslocamento à unidade, com representação autorizada, entregador aprovado e custos transparentes. O desenho inicial ainda não especificava persistência, interfaces e permissões suficientes para implementação verificável.

## Solution

Produzir modelo físico, contrato HTTP, matriz de segurança e backlog de fatias funcionais. Separar aprovação de benefício, cobertura financeira e transferência real. Registrar dependências externas e regras materiais abertas como pendências explícitas.

## User Stories

1. Como paciente, quero cadastrar minha conta sem receber privilégios administrativos.
2. Como paciente, quero recuperar acesso e revogar sessões comprometidas.
3. Como paciente, quero conceder e revogar ações específicas a um familiar.
4. Como familiar, quero atuar somente nos pacientes e ações autorizados.
5. Como entregador, quero submeter meus documentos e vínculo de veículo para análise.
6. Como analista, quero revisar somente casos atribuídos ao meu escopo.
7. Como paciente, quero enviar comprovações privadas e acompanhar a decisão de benefício.
8. Como paciente, quero distinguir gratuidade, desconto por renda e ausência de financiamento.
9. Como instituição, quero disponibilizar recursos comprovados sem dupla reserva concorrente.
10. Como paciente, quero orçamento rastreável com rota, tarifa, parcelas e expiração.
11. Como paciente, quero aceitar o custo explicitamente, sem cobrança automática da diferença.
12. Como entregador, quero aceitar uma oferta sem disputar uma designação já confirmada.
13. Como participante, quero reconhecer o entregador sem receber seus documentos privados.
14. Como entregador, quero registrar retirada autorizada, ocorrência e entrega comprovada.
15. Como paciente, quero ver localização real e sua idade durante a tarefa ativa.
16. Como familiar revogado, devo perder consultas e assinaturas imediatamente após a revogação efetiva.
17. Como entregador, quero receber o frete acordado pelo serviço completo e a remuneração apurada em cancelamento, distinguindo valor devido de transferência concluída.
18. Como operador, quero conciliar webhooks duplicados e resultados incertos sem repetir efeitos financeiros.
19. Como titular, quero solicitar tratamento/exclusão dos dados conforme finalidade e obrigações validadas.
20. Como mantenedor, quero contratos coerentes, pendências rastreáveis e critérios de aceite verificáveis antes de implementar.

## Implementation Decisions

Monólito modular, stack já escolhida, interfaces REST e STOMP. Módulos de identidade, entregadores/veículos, evidências, benefícios, financiamento, precificação, entregas, rastreamento, integrações e auditoria. Valores monetários exatos em BRL; constraints locais e FKs compostas para vínculos críticos; bloqueios e idempotência transacional. Sessões revogáveis; autorização por ação/registro/instituição. Contrato é proposta implementável, não API em execução. Valores de políticas e provedores permanecem pendentes; ausência gera indisponibilidade, nunca simulação.

## Testing Decisions

Nesta etapa: parsing e validação OpenAPI, resolução de referências, links Markdown e revisão cruzada de estados/invariantes/dependências. Não existe suíte anterior nem aplicação. Na implementação: observar REST com autorização por registro, interfaces públicas de elegibilidade/precificação, aceite/reserva concorrentes e publicação/assinatura autorizada de localização. Usar PostgreSQL real isolado para transações/constraints, sem substituição por banco em memória. Testar repetição de webhook, timeout de repasse, revogação e GPS fora de ordem como comportamentos independentes. Limites já autorizados no prompt; limites materiais novos exigem decisão.

## Out of Scope

Implementar aplicação, migrations executáveis, dados reais ou seeds, selecionar/contratar fornecedores, instalar app, abrir Issues remotas, iniciar os tickets ou fazer deploy. Não transportar pacientes/amostras, diagnosticar ou prometer benefício governamental.

## Further Notes

Critérios de comprovação, renda, tarifas, custeio, cancelamento pós-retirada, prazos de retenção, política operacional de GPS e administrador inicial precisam de aprovação verificável antes da respectiva operação. A documentação propõe contratos técnicos sem inventar essas aprovações. Commit/push documental estão autorizados sob as condições do prompt; o workspace inicial não oferece Git utilizável.

## Ajuste autorizado D01–D12 — 30/09/2026

Base recebida `7a8077fa6a7b80e8ba642d3fe2838bd299859ba3`, com seis documentos parcialmente alterados, preservados e completados nesta rodada. Autorizados atualização documental, revisão, commit e push; nenhuma implementação, contratação ou deploy.

Revisão administrativa humana com evidência mínima/reuso e recurso independente; benefício versionado sem ativar 50%/25%; autorização expressa de adulto com convite/aceite/confirmação/escopos/expiração/revogação. Representação legal e menores fora deste primeiro recorte. Unidade/protocolo/destinatário e cobertura de retorno obrigatórios, nenhuma unidade confirmada. Rota real e parâmetros versionados, receita separada. Cancelamento não garante integral: apurar deslocamento/serviço antes/depois da retirada, manter custódia e retorno sem inventar valores/multas/responsabilidades.

Inventário e procedimento de privacidade sem responsáveis fictícios; retenção configurável, propostas de prazo ainda não aprovadas, expurgo/backups verificáveis e prazo financeiro aberto. GPS com hipóteses para aparelho real, documentos reautorizados por proxy. MFA e segregação obrigatórios; dupla revisão sem pessoas reais bloqueada. Seleção de fornecedor por requisitos/custo total, biometria adiada, sem compras.

Primeiro marco é versão funcional validada localmente. Cadastro/segurança independem de subsídio contratado. Particular e subsidiado exigem suas próprias dependências reais. Próxima fatia proposta: cadastro local com validação e bloqueio explícito por e-mail indisponível, sem persistência parcial ou sucesso fictício. Aprovação desta proposta documental não autoriza executá-la. Limites de testes públicos já acordados preservados; nesta rodada apenas validações documentais.
