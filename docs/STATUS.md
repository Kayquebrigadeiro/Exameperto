# Status — 29/09/2026

## Etapa e artefatos

Planejamento técnico documental concluído e publicado para revisão humana; aplicação, migrações e deploy não iniciados.

- [Modelo físico](MODELO-DADOS.md): campos/tipos, FKs, índices, estados, dinheiro exato, concorrência, idempotência e reconciliação.
- [OpenAPI](../contracts/openapi.yaml): contrato HTTP proposto com autenticação, DTOs, erros, paginação, versões e comandos idempotentes; STOMP descrito em segurança.
- [Segurança](SEGURANCA.md): matriz por ação/registro, riscos, controles e retenção pendente.
- [Glossário](../CONTEXT.md), [configuração de agentes](agents/issue-tracker.md), [spec e 15 tickets locais](../.scratch/planejamento/README.md), diagramas e índice de ADRs atualizados.

## Git e origem

Workspace recebido com `.git` vazio; remoto confirmado pelo usuário: `https://github.com/Kayquebrigadeiro/Exameperto.git`, consultado sem refs. Não havia workflow de deploy local nem histórico remoto. Criada branch documental `docs/planejamento-tecnico` e base preservando os cinco arquivos originais: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`. Essa base substitui a ausência de HEAD anterior para a revisão da etapa.

Skills exigidas não estavam instaladas. Fontes e templates consultados em cópia temporária do fork indicado, SHA `6654f6b60cd9d5be8b54c6fafe44346dabeb3b76`; configuração registrada, sem alegar instalação permanente. Histórico das escolhas em [domain](agents/domain.md). Repositório das skills nunca foi configurado como remoto do aplicativo.

## Verificações

- Contrato corrigido passou em `openapi-spec-validator 0.9.0` (OpenAPI 3.0.3): 69 operações. Comando: `/tmp/exame-perto-validation/bin/python -m openapi_spec_validator contracts/openapi.yaml`.
- Validação complementar local: YAML sem chaves duplicadas; 823 referências internas resolvidas, operationIds únicos, parâmetros de path/permissão e paginação conferidos; 57 links Markdown locais existentes; 15 tickets com critérios e dependências sem ciclos. Script temporário `/tmp/exame-perto-validate.py`; estes números são históricos da primeira sessão.
- Sete blocos Mermaid passaram em `mermaid.parse`, versão 12.0.0, ambiente temporário com jsdom. Não houve renderização/inspeção visual dos diagramas.
- `git diff --check` passou; nenhum código de aplicação/workflow foi criado.
- Consultas Git e inspeção documental inicial executadas; base original preservada.
- [Revisão em dois eixos](REVISAO.md): na retomada, dois revisores confirmaram as quatro correções até `63e033d`. Standards identificou novo P2 no vínculo da evidência de conciliação; correção documental commitada em `84631aa` e reconferida pelos dois revisores no conjunto cumulativo. Standards e Spec encerrados com zero achados pendentes.
- Nenhum teste de aplicação, PostgreSQL, Android, provedor, segurança ou desempenho foi executado: não existe implementação nesta etapa.

## Retomada e análise do prompt

O prompt da raiz autoriza concluir modelo, contrato, permissões, backlog e publicação documental; não autoriza implementação ou deploy. Setup, spec e tickets já produzidos foram preservados. A continuidade correta é fechar a revisão antes do envio, sem executar o backlog.

HEAD recebido: `63e033d93e081c812e1a2c99aa1de321481c2447`, árvore limpa. `origin` permanece no aplicativo Exameperto; consulta remota na retomada sem refs. Não há workflows locais.

Validações refeitas após ajustar a conciliação: OpenAPI 3.0.3 válido com `openapi-spec-validator 0.9.0`; 69 operações, 823 referências resolvidas, YAML sem duplicatas, operationIds/permissões/paths conferidos, 61 links locais existentes e 15 tickets com critérios/dependências sem ciclos. Sete diagramas passaram novamente em `mermaid.parse 12.0.0` com jsdom; sem inspeção visual. `git diff --check` passou. Ferramentas e scripts em `/tmp`, sem dependências adicionadas ao aplicativo.

## Pendências e próxima ação

Revisão técnica e primeiro push concluídos. Próxima ação humana: revisar o backlog concreto e decidir as políticas materiais dos tickets 01–02; autorizar explicitamente as próximas fatias antes de implementação. Política de retenção, comprovações, custeio, cancelamento, acesso privilegiado, representação e fornecedores continuam abertas, detalhadas nos artefatos correspondentes.

Push executado com sucesso para `https://github.com/Kayquebrigadeiro/Exameperto.git`, branch `docs/planejamento-tecnico`, até `84631aa4244120c8ddfa6dea00355fa8e8fde594`, após revisão. Este registro de fechamento segue em commit documental separado. Sem force push, workflows de deploy ou alterações de aplicação. Nenhum bloqueio técnico documental remanescente; políticas materiais e autorização da próxima etapa continuam pendentes.

## Rodada atual — proposta para os tickets 01–02

Base recebida: `629ad36a405bb4a290165ffdd08dae6ae789c03d`, árvore limpa; branch local e remota `docs/planejamento-tecnico` no mesmo SHA. Remoto conferido: `https://github.com/Kayquebrigadeiro/Exameperto.git`; nenhum workflow local de deploy. Os registros anteriores permanecem como histórico.

Produzida a [proposta de decisões pendentes](DECISOES-PENDENTES.md), com 12 decisões, recomendações/justificativas, impactos no produto/segurança/custo/implementação e dependências externas. Tickets 01–02 e índice de decisões apontam para a proposta; critérios não foram marcados, e o backlog continua `ready-for-human`. Regras anteriores reaproveitadas; LGPD e orientação da ANPD consultadas em fontes oficiais, sem declaração de conformidade ou homologação de fornecedor.

Modelo, OpenAPI, matriz de segurança e diagramas foram confrontados para registrar lacunas futuras (convite, custódia, garantia/remuneração, retenção e MFA); seus contratos e fluxos não foram alterados. Nenhuma aplicação, migração, contratação ou deploy foi iniciado.

Validações desta rodada: OpenAPI válido com `openapi-spec-validator 0.9.0`; YAML sem duplicatas, 69 operações, 823 referências resolvidas, permissões/paths conferidos, 80 links Markdown locais existentes e 15 tickets com critérios/dependências sem ciclos. Sete diagramas passaram em `mermaid.parse 12.0.0`, sem inspeção visual. `git diff --check` passou. Ferramentas temporárias preexistentes em `/tmp`, sem dependências adicionadas ao projeto. Nenhum teste de aplicação, provedor ou aparelho foi executado. Revisão local contra `629ad36` conferiu cobertura dos tickets, distinção entre proposta/lei/capacidade externa e lacunas frente ao modelo/API. Identificada pergunta redundante em D12 sobre bloqueios já estabelecidos; substituída por decisão aberta sobre o recorte do piloto. Conjunto cumulativo reconferido após a correção. Push da proposta e correção concluído até `80ed3a975d9c9426aa4d572ed0d221f642fdb004` em `origin/docs/planejamento-tecnico`, no remoto do aplicativo; SHA remoto conferido após envio. Este fechamento segue em commit documental separado, revisado antes do envio, sem force push ou deploy.

Bloqueios: decisão humana por ID; responsáveis institucionais/privacidade; unidades participantes; custeio real, tarifa e cláusulas de remuneração em falha; prazo financeiro validado; contratos, credenciais e homologações externas. Próxima ação: responder às perguntas da proposta, validar as dependências externas e então revisar os artefatos técnicos afetados. Implementação continua dependente de autorização posterior.

## Rodada D01–D12 — 30/09/2026

Verificação inicial: tarefa **parcial**, não concluída. HEAD/base `7a8077fa6a7b80e8ba642d3fe2838bd299859ba3`; seis documentos já modificados localmente, preservados e completados. Remoto `https://github.com/Kayquebrigadeiro/Exameperto.git`, branch `docs/planejamento-tecnico`; SHA remoto conferido igual à base. Sem aplicação/workflow de deploy.

Direções D01–D12 incorporadas em decisões, glossário/arquitetura, modelo, segurança, OpenAPI 0.2.0, diagramas, spec e dependências dos tickets. Convite adulto com aceite/confirmação, evidência mínima/reuso/recurso independente, proxy reautorizado, unidade/protocolo/custódia, apuração de cancelamento e cobertura de retorno explícitos. Particular separado da dependência de benefício/aporte; cadastro/segurança sem programa obrigatório. Políticas e integrações continuam com guardas distintas, sem valores, responsáveis ou contratos fictícios.

Criada proposta [03A](../.scratch/planejamento/issues/03a-cadastro-local.md): formulário web/REST e PostgreSQL local isolado, validação, negação de privilégios e bloqueio de cadastro por ausência de e-mail sem efeitos parciais. Critérios concretos disponíveis para autorização; cadastro completo permanece em 03. Nenhuma implementação, compra, dado real ou deploy executado.

Skills domain-modeling, to-spec e to-tickets consultadas em `/tmp/exame-skills`, SHA `6654f6b60cd9d5be8b54c6fafe44346dabeb3b76`; setup preservado, sem instalação permanente. Preferências explícitas do usuário mantêm tracker local e `ready-for-human`. Ferramentas temporárias de validação em `/tmp`, sem dependências do aplicativo.

Validações: OpenAPI 3.0.3 válido com openapi-spec-validator 0.9.0; YAML sem duplicatas, 77 operações únicas, 910 referências resolvidas, paths/permissões conferidos, 86 links locais existentes e 16 tickets com critérios/dependências sem ciclos. Erro de sintaxe de sequência Mermaid causado por ponto e vírgula identificado e corrigido; dez diagramas passaram em mermaid.parse 12.0.0 com jsdom, sem renderização/inspeção visual. `git diff --check` passou. Não houve testes de aplicação, PostgreSQL, aparelho ou provedor, pois esta etapa é documental.

Próxima ação desta rodada: commit local, revisão do conjunto contra a base capturada e push documental. Bloqueios operacionais: documentos/critérios profissionais, valores/financiador/modelo comercial, unidades/protocolo/cobertura real, responsáveis, retenção validada e prazo financeiro, procedimentos/fatores MFA, provedores e ensaios GPS. Próxima autorização solicitável: somente implementação da fatia 03A; deploy posterior. Resultado da revisão e envio será registrado no fechamento.

### Fechamento D01–D12

Commit inicial `543279c`; revisão code-review em Standards e Spec, com delegação explicitamente autorizada no prompt. Standards apontou P1 no encerramento de custódia por entrega normal: corrigido em `c498e90`, distinguindo evento estruturado de recebimento por código de prova documental de retorno. Revisão local também completou vínculo/autorização das evidências operacionais, leitura de verificações reutilizadas, aceite de política pelo entregador e erros de versão. Ambos os revisores reconferiram o conjunto: zero achados materiais pendentes. Evidências detalhadas em REVISAO.

Validações finais após correções: OpenAPI válido; 77 operações, 917 referências resolvidas, YAML sem duplicatas, paths/permissões válidos, 87 links locais existentes, 16 tickets sem ciclos e dez diagramas Mermaid válidos. `git diff --check` cumulativo passou; sem inspeção visual ou testes de aplicação/aparelho/provedor.

Push concluído para `origin/docs/planejamento-tecnico`, remoto do aplicativo confirmado, até `c498e90e5f31aa554bbf21367ab171dcd9c84df3`; SHA remoto conferido após envio. Este registro final e REVISAO seguem em commit documental de fechamento, com revisão local antes de envio, sem force push ou deploy. Tarefa documental concluída; políticas/integrações reais continuam pendentes. Próxima ação humana: autorizar, se desejado, somente 03A conforme seus critérios de aceite; não reabrir D01–D12 como decisões técnicas ainda sem resposta.

## Implementação 03A — 30/09/2026

Autorização recebida a partir de `67f5e113241dcec733a46c5bdc47e1730f8b2401`. Implementada a primeira fatia local: backend Spring Boot 3.5.16/Java 21 com Flyway/PostgreSQL, fronteira REST `POST /api/v1/auth/register`, validação server-side, rejeição de propriedades desconhecidas (`role`, `status` e equivalentes), limite de tentativas, CORS de origem definida e respostas seguras. A operação não possui sucesso falso: sem política de privacidade validada retorna `422 POLICY_UNDEFINED`; com política de teste mas sem adaptador real de e-mail retorna `503 INTEGRATION_UNAVAILABLE` antes de qualquer escrita. Nenhum usuário, sessão, desafio ou outbox é criado nesse caminho.

Implementado frontend React/TypeScript/Vite com formulário acessível, mensagens compreensíveis, estado ocupado e feedback de indisponibilidade/falha sem afirmar cadastro ou envio. Configuração local do PostgreSQL está em [infra/compose.yaml](../infra/compose.yaml); o banco usa somente credencial de desenvolvimento local e não é configuração de produção. O adaptador de e-mail desta fatia é explicitamente indisponível; não há simulador de entrega, seed ou conta de demonstração.

Verificações executadas: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -DskipTests compile` passou; `RegistrationTest` passou com Testcontainers/PostgreSQL 17.6, HTTP real e banco descartável, verificando `503` sem linhas em `usuario`, `sessao`, `desafio_conta` e `outbox`, além de `400` para tentativa de privilégio sem persistência; teste unitário cobre bloqueio por política antes do provedor. `npm run build --prefix web` passou; Playwright passou em duas verificações: mensagem de indisponibilidade via API e Axe sem violações básicas. A primeira execução de Playwright foi bloqueada pelo sandbox/porta 5173 e repetida em porta local livre autorizada. Nenhum dado pessoal real foi usado.

Limitações: não há caminho de cadastro bem-sucedido, confirmação de e-mail, login ou recuperação nesta fatia; isso permanece no ticket 03. Teste de entrega real de e-mail não foi executado nem alegado. Docker/Testcontainers foi usado somente com dados sintéticos descartáveis. Não houve deploy.

Próxima ação proposta: ticket 03 — completar confirmação de e-mail, sessão e recuperação com provedor real configurado e testes correspondentes, mediante nova autorização; não iniciado. Revisão e push desta implementação continuam pendentes neste fechamento.
