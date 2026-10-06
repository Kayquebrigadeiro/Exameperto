# Status — atualizado em 06/10/2026

Situação atual: tickets 03–04 implementados localmente e ticket 05 em continuidade técnica parcial; entrega real de e-mail e aprovação profissional continuam não homologadas. Registros abaixo preservam o histórico; ver a seção final para continuidade.

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

Verificações executadas: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -DskipTests compile` passou; a suíte Maven passou com Testcontainers/PostgreSQL 17.6, HTTP real e bancos descartáveis, verificando `422 POLICY_UNDEFINED` e `503 INTEGRATION_UNAVAILABLE` sem linhas em `usuario`, `sessao`, `desafio_conta` e `outbox`, além de `400` para tentativa de privilégio sem persistência e limite aplicado antes das decisões. `npm run build --prefix web` passou; Playwright passou em duas verificações: apresentação do erro com POST interceptado e Axe sem violações básicas. A integração navegador→backend real e a validação visual do formulário ainda não foram ensaiadas. A primeira execução de Playwright foi bloqueada pelo sandbox/porta 5173 e repetida em porta local livre autorizada. Nenhum dado pessoal real foi usado.

Limitações: não há caminho de cadastro bem-sucedido, confirmação de e-mail, login ou recuperação nesta fatia; isso permanece no ticket 03. Teste de entrega real de e-mail não foi executado nem alegado. Docker/Testcontainers foi usado somente com dados sintéticos descartáveis. Não houve deploy.

Próxima ação proposta: ticket 03 — completar confirmação de e-mail, sessão e recuperação com provedor real configurado e testes correspondentes, mediante nova autorização; não iniciado.

Fechamento 03A: revisão em dois eixos contra `67f5e11` confirmou zero achados materiais após corrigir a confiança em `X-Forwarded-For`, restringir o Compose ao loopback, tornar a senha local configurável, aplicar limite antes das decisões e verificar o bloqueio de política pela fronteira REST. O conjunto foi commitado em `142f97f39f05e939abf33baec47a67fc3e21af87` e enviado para `origin/docs/planejamento-tecnico`; SHA remoto conferido. Sem deploy.

## Ticket 03 — implementação autorizada e retomada

Base conferida: `604487b9040d78aff89a1e68c2edc6f079a39a89`, HEAD recebido nessa base, branch `docs/planejamento-tecnico`, remoto `https://github.com/Kayquebrigadeiro/Exameperto.git`; SHA remoto inicialmente igual. Havia alterações locais incompletas de autenticação, preservadas e corrigidas. Nenhum workflow de deploy local. Autorização atual abrange implementação, commits/revisão e push desta fatia; não autoriza deploy nem próximo ticket.

Não havia provedor de e-mail configurado nas variáveis relevantes do processo ou arquivos locais de ambiente. Implementado adaptador SMTP real, desabilitado por padrão; requisitos exatos e procedimento de homologação em [CONTA-EMAIL](CONTA-EMAIL.md). Nenhuma mensagem real enviada, serviço contratado ou credencial incluída no Git/chat. Privacidade permanece bloqueada por padrão.

Implementados confirmação/reenvio com tokens aleatórios de 256 bits, hash/prazo/consumo único; sessão JWT com consulta de revogação no banco, refresh rotativo e revogação da família em replay/logout; recuperação genérica em fila sem tokens persistidos e troca de senha com revogação de todas as sessões. Locks de conta serializam consumo/renovação/reset; revogação por replay persiste apesar do 401. Origin/CSRF web, separação mobile/cookie, limites de IP/conta, cifra e HMAC com chaves externas independentes. Cadastro preserva rollback sem efeitos parciais na ausência/falha SMTP e bloqueio por política de 03A. Web contém os fluxos e explicita que e-mail confirmado não equivale a identidade, elegibilidade ou entregador/veículo verificados.

Verificações executadas nesta rodada:

- Suíte `mvn -f backend/pom.xml test`: 16 testes executados, zero falhas; BrowserFlowTest é opt-in e fica ignorado nessa execução. PostgreSQL 17.6/Testcontainers real, bancos sintéticos descartáveis. Confirmação e recuperação concorrentes/expiradas/reutilizadas, refresh concorrente/reutilizado/expirado, access expirado, logout, reset versus refresh, CSRF inválido/ausente e Origin, reenvio limitado, rate limit e rejeição de privilégios. Falha de transporte e respostas genéricas verificadas; testes 03A preservados.
- `PersistenceRestartTest`: encerrou e reiniciou aplicação contra o mesmo PostgreSQL isolado; conta confirmada continuou autenticável. `SmtpFailureTest`: adaptador JavaMail real contra conexão local indisponível retornou 503, sem conta/desafio/sessão/outbox parcial.
- Build TypeScript/Vite passou. Dois testes Playwright de apresentação do erro e Axe passaram; o primeiro intercepta o POST e não comprova integração.
- `mvn -f backend/pom.xml -Dtest=BrowserFlowTest -DbrowserTest=true test`: passou com um ensaio Chromium → Vite → backend HTTP real → PostgreSQL descartável, sem interceptar API. Cadastro, confirmação, login web, cookie Secure/HttpOnly, renovação, logout, recuperação, nova senha e indisponibilidade passaram. Fronteira de e-mail substituída exclusivamente em código de teste, com capturas temporárias removidas no fechamento. Não é homologação de entrega real.
- OpenAPI válido; 79 operações, 925 referências internas resolvidas, YAML sem duplicatas, 94 links locais verificados. Diagramas Mermaid e revisão final registrados no fechamento. `git diff --check` passou.

Durante a retomada foram corrigidos caminho duplicado no teste existente, rollback indevido da revogação, ausência de serialização e CSRF superficial. No primeiro ensaio de navegador, a herança de stdout do subprocesso corrompeu o protocolo do Surefire apesar do teste passar; saída foi redirecionada a artefato ignorado e a execução repetida com sucesso. Contextos Spring agora fecham antes de remover bancos de teste, evitando consultas do agendador após teardown. Nenhum desses ensaios utilizou dados de pessoas reais.

Bloqueio externo: SMTP **ainda não homologado**. Faltam configuração privada, remetente autorizado, política pertinente validada, endereço controlado pelo autor e autorização específica para envios. 202 de recuperação/reenvio significa solicitação recebida, não e-mail enviado; falhas ficam RECONCILIAR. Aceitação SMTP tampouco comprova recebimento. Limites por processo, retenção/expurgo, rotação operacional de chaves e observação de bounces estão explicitados em CONTA-EMAIL; não há declaração de prontidão para produção.

### Fechamento do ticket 03 — 02/10/2026

Revisão local contra `604487b` concluída sem achados materiais pendentes; critérios verificáveis do ticket foram marcados. A suíte Maven foi repetida em Java 21 com PostgreSQL 17.6/Testcontainers: 17 testes descobertos, 16 executados com sucesso e o `BrowserFlowTest` opt-in ignorado nessa execução. Build web passou; Playwright comum teve dois testes aprovados e o ensaio completo ignorado por desenho. O ensaio opt-in foi então executado separadamente e passou: Chromium → Vite → backend HTTP → PostgreSQL real descartável, sem interceptação da API da aplicação.

OpenAPI 3.0.3 passou em `openapi-spec-validator 0.7.2`; YAML sem chaves duplicadas, 79 `operationId` únicos, 925 referências internas resolvidas e 96 links Markdown rastreados existentes. Onze blocos Mermaid passaram em `mermaid.parse` 12.0.0 com jsdom, sem inspeção visual. `git diff --check` passou. Todos os dados dos testes foram sintéticos e isolados.

Bloqueio remanescente: entrega real de e-mail não homologada. Para o ensaio próprio ainda são necessários provedor SMTP escolhido, host/porta, credencial privada, remetente verificado, três chaves privadas independentes, política pertinente habilitada, origem web, PostgreSQL autorizado, endereço controlado pelo autor e autorização específica do envio. Valores permanecem fora do Git e do chat. Aceitação SMTP e recebimento devem ser comprovados separadamente conforme [CONTA-EMAIL](CONTA-EMAIL.md).

Próxima ação permitida: somente configurar e autorizar o ensaio de e-mail real do ticket 03. Ticket 04 e deploy não foram iniciados.

### Complemento de validação do ticket 03 — 02/10/2026

O teste Maven opt-in antes contabilizado como ignorado foi identificado como `BrowserFlowTest`. Ele não é cobertura separada do Playwright opt-in: orquestra PostgreSQL/Testcontainers, backend real e Vite, então executa `web/tests/account-real.spec.ts`. O spec Playwright se ignora fora desse ambiente quando `TEST_MAILBOX` não foi fornecido. Comando e dependências estão documentados em [CONTA-EMAIL](CONTA-EMAIL.md).

Criado `./scripts/verify-ticket-03.sh` como entrada única para instalar dependências web do lockfile, executar suíte Maven, build, Playwright de UI, ensaio integrado com API/PostgreSQL reais e conferir o pacote de produção. O script remove variáveis SMTP do seu processo e não faz envio externo. A confirmação e a recuperação do ensaio usam `@MockBean EmailGateway` em `src/test`, arquivos temporários `confirmation`/`recovery` e variável `TEST_MAILBOX` entregue apenas ao subprocesso Playwright. Não há capturador, endpoint auxiliar ou retorno de token em `src/main`; o diretório é apagado ao final e o JAR é verificado contra classes/identificadores de teste.

O comando completo foi executado nesta rodada e passou: suíte Maven com 17 testes descobertos, 16 aprovados e o opt-in ignorado; build web; dois testes Playwright de UI aprovados; `BrowserFlowTest` opt-in executado separadamente com um teste aprovado e nenhum ignorado; pacote Spring Boot gerado e inspecionado sem classes ou identificadores do mecanismo de captura. A execução usou dados sintéticos, Docker local e PostgreSQL 17.6 descartável. Nenhuma conexão SMTP externa foi tentada.

Validação documental final: OpenAPI 3.0.3 válido; 98 links Markdown locais rastreados existentes; onze blocos Mermaid válidos em `mermaid.parse` 12.0.0; `bash -n` no novo script e `git diff --check` aprovados. A primeira invocação do validador Mermaid falhou somente na preparação temporária do jsdom sob Node.js 24; a atribuição incompatível foi removida e os onze blocos passaram sem alteração do repositório.

Próximo ticket lido, sem implementação: [04 — paciente concede e revoga representação](../.scratch/planejamento/issues/04-paciente-familiar.md). Escopo: perfil de paciente com identidade pendente e autorização temporária/revogável de familiar adulto por convite privado, aceite do destinatário, confirmação reautenticada do paciente e escopos explícitos. Aceite sozinho não concede acesso; familiar não delega; identidade/CPF não aprova benefício; menores e representação legal permanecem fora do recorte.

Critérios previstos: persistência/API/UI do perfil e das concessões; expiração e seleção segura da conta; negação de paciente alheio, autoconcessão, delegação e concessão expirada/revogada; ponto efetivo e teste PostgreSQL da revogação concorrente; consumo único do convite e perda de acesso REST/STOMP após revogação. Dependências: ticket 03 localmente validado, decisão humana do ticket 01 sobre responsável e prazos do convite/concessão, política de dados reais e canal privado habilitado para a entrega do convite.

Sem SMTP homologado podem avançar, após autorização própria do ticket 04: migrações e estado do perfil/convite/concessão; autorização por titular/registro/escopo; listagem e revogação; bloqueios por política/canal ausente; UI; e testes isolados de expiração, uso único, aceite sem concessão e concorrência. O envio real do convite e sua homologação permanecem bloqueados pelo canal privado. Nenhuma parte do ticket 04 foi implementada nesta rodada.

## Ticket 04 — implementação autorizada e fechamento — 02/10/2026

Autorização recebida para implementar a partir de `b33acfa19655cc77fd20315c429e732994813732`, na branch `docs/planejamento-tecnico`, sem deploy e sem iniciar o ticket 05. A implementação local foi concluída preservando `referencia.md` fora do Git.

Implementados perfil mínimo de paciente com identidade pendente e atualização exclusiva do titular; convite privado vinculado ao destinatário por HMAC, token aleatório com somente o hash persistido, expiração e uso único; aceite pela própria conta autenticada; confirmação do paciente com reautenticação; escopos explícitos, prazo máximo, revogação imediata e reavaliação no banco em cada operação protegida. A interface web permite consultar e administrar perfil, convites e concessões com mensagens acessíveis, sem expor token/link. Falha ou indisponibilidade do canal retorna `503 INTEGRATION_UNAVAILABLE` e não deixa convite utilizável nem concessão ativa.

Os pontos de integração futuros (pedidos, benefícios, documentos e assinaturas/STOMP) estão documentados em arquitetura, segurança e diagramas; nenhum módulo futuro foi presumido como implementado. Parentesco, idade, deficiência, menores e representação legal não concedem acesso neste recorte.

Evidências de validação:

- `./scripts/verify-ticket-04.sh` passou integralmente: suíte Maven com 25 testes descobertos (23 na execução principal e 2 opt-in executados separadamente), zero falhas; build web; dois testes Playwright de UI; fluxo integrado do ticket 03; fluxo integrado do ticket 04; empacotamento e inspeção do JAR sem classes/identificadores do capturador.
- `RepresentationFlowTest` passou com 7 testes, incluindo acesso indevido, destinatário divergente, convite expirado/reutilizado/encaminhado, concorrência, escopos e revogação com sessão aberta. O fluxo de navegador usou Chromium → Vite → backend HTTP real → PostgreSQL 17.6/Testcontainers, com dados sintéticos e sem interceptação da API.
- `contracts/openapi.yaml` passou em `openapi-spec-validator 0.9.0`; `bash -n` dos scripts e `git diff --check` passaram. O capturador de convite/confirmação existe somente em `src/test`, usa caixa temporária descartável e não homologa entrega externa de e-mail.

Limitações e bloqueios: SMTP real continua desabilitado e não homologado; não houve envio externo, deploy ou uso de dados pessoais reais. A próxima ação humana, se desejada, é revisar/homologar o canal privado de e-mail conforme `docs/CONTA-EMAIL.md`; não iniciar o ticket 05.

## Ticket 05 — implementação parcial e fechamento da rodada — 02/10/2026

Autorização recebida a partir de `966c0b2ef74e3bc0dbbaa1e1ebd3cc38f9a02354`, na branch `docs/planejamento-tecnico`, preservando `referencia.md`, sem deploy e sem iniciar o ticket 06.

Implementados: app Expo/React Native do entregador; cadastro persistido em `RASCUNHO`; migração V4 para entregador, documentos, histórico e revisões; API multipart autenticada; armazenamento privado local configurável; quarentena, nomes/chaves gerados, SHA-256, limite de 10 MiB e validação por assinatura real de PDF/JPEG/PNG; substituição com nova revisão e histórico mínimo; download apenas por proxy autenticado, `no-store`, após estado `INSPECAO_APROVADA`; painel web para cadastro, upload e acompanhamento. Cadastro, inspeção e aprovação profissional permanecem estados distintos.

O analista só consulta a fila com papel nominal `ANALISTA_OPERACIONAL`; atribuição/decisão exigem MFA verificado no servidor. Como o verificador MFA, a inspeção oficial, os critérios profissionais e integrações externas não estão disponíveis, essas ações permanecem bloqueadas (`MFA_REQUIRED`/`POLICY_UNDEFINED`), sem aprovação simulada, conta administrativa padrão ou revisor fictício. Foto operacional não é promovida automaticamente.

Evidências:

- `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 test`: 25 testes existentes, zero falhas, 2 opt-in ignorados; migração V4 validada em PostgreSQL 17.6/Testcontainers.
- `DelivererEvidenceFlowTest` passou com PostgreSQL/Testcontainers, HTTP real e armazenamento temporário isolado: cadastro, upload real, quarentena, proxy bloqueado/aprovado, acesso indevido por ID, tipo inválido, arquivo excessivo, substituição, fila e bloqueio MFA.
- `PrivateObjectStoreTest` passou escrevendo, lendo e removendo objeto real com chave gerada. `npm run build --prefix web`, compilação Maven, OpenAPI 3.0.3 e `git diff --check` passaram.
- Não houve execução em emulador Expo nem aparelho físico; o app foi compilado/inspecionado como código, e não se declara validação mobile ponta a ponta. Dados de documentos foram sintéticos e isolados.

Conclusão: ticket 05 **parcial**, não pronto para aprovação operacional. Dependências para concluir: armazenamento oficial escolhido/homologado, mecanismo de inspeção, critérios profissionais publicados, analista real atribuído, MFA verificável e política de retenção/expurgo. Não apresentar a análise local como consulta oficial. Não iniciar ticket 06 nem deploy.

## Ticket 05 — continuidade técnica autorizada — 05/10/2026

Base `fd670c8` confirmada em `docs/planejamento-tecnico`, inicialmente igual a `origin/docs/planejamento-tecnico`; remoto do aplicativo confirmado. `referencia.md` permaneceu fora do Git. Ticket 06 não foi iniciado; não houve deploy, contratação ou envio externo de mensagens.

Entregas técnicas: migração V5 e TOTP do AO com segredo cifrado, confirmação/anti-reuso, cinco falhas em 15 minutos, elevação de cinco minutos vinculada à sessão e revogação efetiva; atribuição e inspeção estrutural exigem MFA na própria API. O teste cobre chamada direta sem MFA (403), acesso por conta comum (403), decisão pelo proprietário (403), bloqueio profissional por política ausente e chamada após logout (401); o status desse bloqueio foi alinhado para 422 na consolidação abaixo. Não existe bypass, recuperação automática, conta privilegiada padrão ou analista criado em produção; contas/papéis sintéticos aparecem somente no banco descartável de teste.

A inspeção estrutural local verifica fechamento mínimo de PDF/JPEG/PNG e recursos ativos conhecidos de PDF. Ela é uma inspeção limitada de segurança do **arquivo**, não comprovação de autenticidade do **documento** e não aprovação **profissional**. A decisão profissional continua invariavelmente em `POLICY_UNDEFINED` até existirem critérios publicados e responsável real; o status, antes registrado como 503 nesta rodada histórica, foi corrigido para 422 na consolidação abaixo. Um fornecedor de autenticidade não foi presumido: integração externa só passa a ser obrigatória se um requisito concreto for aprovado.

Armazenamento local: raiz/quarentena isoladas, chave gerada e caminho normalizado, traversal/symlink recusados, diretórios `0700` e objetos `0600` em POSIX, proxy com autorização por proprietário e `no-store`, além de exclusão física testada. O ensaio local comprova esse adaptador, não operação externa, backups/restauração, retenção/expurgo ou varredura antimalware abrangente. Ausência de objeto externo não invalida o teste local.

Classificação das pendências:

- **Implementação ausente:** decisão profissional/transições de aprovação, projeção de foto aprovada, processo de autenticidade documental e orquestração de retenção/expurgo; provisionamento/recuperação independente de papéis privilegiados.
- **Implementação existente, não testada em dispositivo:** app Expo, SecureStore/refresh, seleção e upload nativos, erros de rede, quarentena e acompanhamento visual. Typecheck/export não substituem esse ensaio. `npm audit --omit=dev --prefix mobile` encontrou 29 alertas transitivos (21 altos, 8 moderados, 0 críticos); as correções sugeridas implicam mudanças incompatíveis de Expo/React Native, portanto não foram aplicadas sem migração e ensaio em dispositivo.
- **Dependência externa para operação real:** critérios profissionais aprovados e responsáveis reais; infraestrutura/backup/restauração/antimalware conforme requisitos de deploy; eventual fonte oficial de autenticidade apenas se formalmente exigida. SMTP continua dependência do cadastro real, sem afetar os ensaios sintéticos.

Evidências desta rodada:

- `./scripts/verify-ticket-05.sh`: passou integralmente. A suíte comum descobriu 28 testes, executou 26 com sucesso e ignorou os 2 opt-in; esses dois foram então executados explicitamente, 1/1 aprovado em cada comando, sem skips. Também passaram build web, 2 testes Playwright, typecheck móvel, export Android (568 módulos, bundle de 1,77 MB) e package Maven.
- `mvn -f backend/pom.xml -Dtest=DelivererEvidenceFlowTest test`: 2 testes, 0 falhas/erros/skips, com PostgreSQL 17.6, API HTTP e filesystem reais isolados; inclui limite persistente de MFA, sessão/revogação, upload/erros/quarentena/inspeção/acesso e bloqueio profissional.
- Opt-in de conta: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -Dtest=BrowserFlowTest -DbrowserTest=true test`; finalidade Chromium → Vite → API → PostgreSQL para conta/sessão; 1 executado, 0 falhas, 0 ignorados.
- Opt-in familiar: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -Dtest=FamilyBrowserFlowTest -DbrowserTest=true test`; finalidade autorização/revogação com sessão aberta pelo mesmo caminho; 1 executado, 0 falhas, 0 ignorados.

Não havia `adb`, `emulator` ou `xcrun` no ambiente. O app passou em TypeScript e export Android, mas não foi instalado/executado; o roteiro reproduzível está em `mobile/README.md` e a validação mobile → API → PostgreSQL → armazenamento permanece pendente. O fluxo API → PostgreSQL → armazenamento foi exercitado separadamente e não será apresentado como ponta a ponta móvel.

Fechamento: implementação, testes, contrato e documentação foram revisados no commit `d632eeb698cdbb29612972a837b77c3c744b6fa4`, enviado para `origin/docs/planejamento-tecnico`; o SHA remoto foi conferido. Sem force push ou deploy. Próxima ação técnica do ticket 05: migrar as dependências Expo/React Native com os alertas do audit e executar o roteiro em emulador/aparelho. A conclusão operacional continua dependente de critérios profissionais e responsáveis reais; manter a decisão bloqueada. Não iniciar o ticket 06.

## Ticket 05 — dependências mobile e consolidação — 05/10/2026

Base recebida `fd186a4`, igual a `origin/docs/planejamento-tecnico`; remoto do aplicativo e branch conferidos. `referencia.md` permaneceu fora do Git. Não houve deploy, distribuição ou início do ticket 06.

Dependências mobile migradas incrementalmente conforme documentação oficial: Expo SDK 53 → 54 → 55 → 56 → 57. O estado final usa Expo 57.0.26, React Native 0.86.3 e módulos compatíveis. A opção removida `newArchEnabled` saiu do `app.json`; SDK 55+ exige a Nova Arquitetura. O doctor no SDK 56 detectou a regressão conhecida do Hermes e recomendou SDK 57, que passou em 21/21 verificações. O lockfile npm v3 foi regenerado por instalação normal, sem `npm audit fix --force`, `--legacy-peer-deps` ou overrides.

O [relatório de dependências mobile](AUDITORIA-DEPENDENCIAS-MOBILE.md) classifica individualmente os 21 nós altos e 8 moderados iniciais. Eram dois nós diretos (`expo`, `react-native`) e 27 transitivos; os advisories alcançavam o toolchain de CLI/Metro/prebuild/Jest, sem caminho identificado ao JavaScript executado pelo usuário final. Isso é análise de exposição, não prova de inexplotabilidade. O audit final reporta 23 nós (16 altos, 7 moderados, zero críticos) que convergem para `braces`, `node-forge` e `uuid`; não há versão corrigida compatível indicada. Os alertas permanecem abertos e a sugestão automática de downgrade para Expo 44/RN 0.72 foi rejeitada por incompatibilidade, não tratada como correção.

`POLICY_UNDEFINED` foi alinhado à semântica transversal: decisão profissional sem critérios/responsáveis agora retorna `422` na implementação, contrato e teste. `503 INTEGRATION_UNAVAILABLE` continua reservado à capacidade técnica externa/integração indisponível. A decisão profissional continua invariavelmente bloqueada; nenhuma aprovação foi simulada.

Evidências desta rodada:

- `./scripts/verify-ticket-05.sh` passou integralmente: suíte comum com 28 testes descobertos, 26 aprovados e 2 opt-in ignorados; os opt-in foram executados separadamente com 1/1 aprovado cada. Também passaram build web, 2 testes Playwright, typecheck/export Android e package Maven.
- `npm ci` no mobile concluiu instalação reproduzível de 494 pacotes; `npx expo install --check` informou dependências atualizadas; `npx expo-doctor` passou 21/21.
- `npm run typecheck` passou com TypeScript 6.0.3. `expo export --platform android` passou com 581 módulos e bundle Hermes de 1,4 MB. Export não equivale a instalação ou uso.
- `DelivererEvidenceFlowTest`: 2 testes, zero falhas/erros/skips, com PostgreSQL 17.6/Testcontainers, HTTP e filesystem reais isolados; confirmou `422 POLICY_UNDEFINED`. A primeira tentativa foi impedida pelo socket Docker no sandbox e repetida com acesso local autorizado.
- `npm audit --omit=dev --json`: 16 altos, 7 moderados, zero críticos remanescentes, documentados sem supressão.
- `contracts/openapi.yaml` passou em `openapi-spec-validator 0.7.2`; links locais alterados, `bash -n` e `git diff --check` passaram.

Não há `adb`, `emulator` ou `xcrun` neste ambiente. O fluxo instalado UI móvel → API → PostgreSQL → armazenamento permanece pendente; [o roteiro Android](../mobile/README.md) agora inclui pré-requisitos, development build, cenários de SecureStore/upload/rede/refresh/logout e evidência mínima. A inspeção estrutural continua descrita somente como inspeção limitada de segurança do arquivo, não autenticidade documental nem varredura antimalware.

Pendências consolidadas: ensaio em dispositivo; critérios e responsável profissional reais; decisão/transições e foto operacional projetada; decisão de requisito/fornecedor para autenticidade; retenção/expurgo; armazenamento, backup, restauração e antimalware de operação; provisionamento/recuperação privilegiados; e atualização upstream dos três advisories do toolchain. A aprovação profissional e a conclusão operacional permanecem bloqueadas. Próxima ação: executar o roteiro Android na máquina do autor e resolver as políticas/dependências operacionais antes de distribuição. Não iniciar ticket 06 nem deploy.

Fechamento Git: o conjunto foi commitado localmente em `6c6575a`, revisado contra `fd186a4` e enviado com sucesso para `origin/docs/planejamento-tecnico`; a ponta remota anterior foi confirmada em `fd186a4`. A revisão cumulativa confirmou lockfile/manifests coerentes, contrato/teste/implementação em 422 e ausência de alterações em `referencia.md`. Este registro de resultado segue em commit documental separado, também sem force push ou deploy.

## Ticket 06 — implementação parcial autorizada — 05/10/2026

Base autorizada: `caff6af42346c286b3668b60e1046db48b559b59`, branch `docs/planejamento-tecnico`; `referencia.md` foi preservada sem alteração. Implementadas migração V6 e API pública de veículos/vínculos: placa, marca/modelo, cor, anos, tipo próprio/alugado/autorizado, validade, CRLV, fotos e autorização de uso. O veículo não é presumido como propriedade do entregador. A API restringe leitura/escrita a vínculos próprios, rejeita campos privilegiados por contrato estrito, rejeita reutilização de documento de outra conta e aplica versão otimista `412`.

Documentos reaproveitam `documento` e `PrivateObjectStore`: quarentena, nomes/chaves privadas, inspeção estrutural, proxy `no-store` e download autorizado por revisão. `vinculo_documento` mantém substituições; cada troca bloqueia revisão pendente/atribuída e cria snapshot corrente em `revisao_vinculo_*`. Atribuição e inspeção de AO exigem MFA, impedem autoanálise e não liberam ofertas. Decisão continua `422 POLICY_UNDEFINED`; `503 INTEGRATION_UNAVAILABLE` permanece reservado à integração indisponível. O contrato OpenAPI, modelo, segurança, arquitetura e diagrama foram alinhados; não há consulta oficial simulada nem mecanismo paralelo de aprovação.

Validações: compilação Maven, build web, typecheck mobile, export Android e YAML OpenAPI passaram. O novo `VehicleLinkFlowTest` cobre interfaces HTTP de contas distintas, documento de terceiro, propriedades privilegiadas, concorrência, substituição, revisão obsoleta, MFA, download autorizado, decisão bloqueada e logout. A execução não pôde iniciar porque o ambiente não fornece Docker/Testcontainers (`Could not find a valid Docker environment`); portanto os resultados de integração PostgreSQL são pendentes para a máquina do autor. Não há `adb`, emulador ou `xcrun`; export não substitui instalação/uso e o fluxo mobile ponta a ponta segue pendente.

Pendências preservadas do ticket 05: ensaio em dispositivo/emulador; aprovação profissional e responsáveis/critério; dependências mobile remanescentes (16 altos/7 moderados no audit final, convergindo para `braces`, `node-forge` e `uuid`); retenção/expurgo, storage/backup/restore e antimalware operacional; eventual fornecedor oficial somente se requisito for aprovado. Ticket 06 fica parcial e aprovação operacional bloqueada. Não iniciar ticket 07, distribuir aplicativo, contratar ou fazer deploy.

### Fechamento de verificação PostgreSQL do ticket 06 — 05/10/2026

Docker foi verificado disponível (`28.5.2+dfsg4`) e o Testcontainers encontrou o socket Unix local. `mvn -q -Dtest=VehicleLinkFlowTest test` executou PostgreSQL 17.6 descartável com HTTP real, Flyway, filesystem privado e contas sintéticas: **2 testes, 0 falhas, 0 erros, 0 skips**.

Além do fluxo público de vínculo, o teste confirmou acesso/alteração entre contas, documento de terceiro rejeitado, `412 VERSION_MISMATCH` sem sobrescrita, histórico de substituição, snapshots e revisão obsoleta, atribuição/MFA/autoanálise, download autorizado, logout e `422 POLICY_UNDEFINED`. O segundo caso migrou schema vazio até V5, executou V6 e confirmou `vinculo_veiculo` e Flyway em V6; o schema público da aplicação também foi criado do zero e aplicado até V6. O container foi descartado pelo lifecycle do Testcontainers.

O teste mobile em dispositivo/emulador permanece uma pendência independente, assim como os alertas de dependências mobile, critérios/responsável profissional e demais bloqueios operacionais do ticket 05. Nenhum build amplo foi repetido, não houve deploy, distribuição, liberação de ofertas ou início do ticket 07. `referencia.md` permanece fora do Git.

## Ticket 07 — implementação parcial autorizada — 06/10/2026

Base `a7443bf` confirmada na branch `docs/planejamento-tecnico`; `referencia.md` preservada fora do Git. V7 adiciona instituição/política versionada, solicitação com snapshot, dimensões independentes IDADE/DEFICIENCIA/RENDA, evidência privada com substituição/histórico e revisões inicial/recurso. A API exige paciente ou familiar com BENEFICIOS vigente; fila e decisão exigem membro da instituição, atribuição, MFA e impedem autoanálise/revisor do recurso anterior. Política ausente retorna `422 POLICY_UNDEFINED`; não há percentuais, documentos aceitos ou responsáveis reais ativos e decisão não reserva financiamento.

`BenefitFlowTest` passou com PostgreSQL 17.6 descartável via Docker/Testcontainers: HTTP real → Spring Boot → Flyway V1–V7 → PostgreSQL, 1 teste, 0 falhas/erros/skips. Cobriu acesso entre pacientes, escopo familiar revogado/expirado, documento de outra conta, conflito `412` sem sobrescrita, substituição/histórico, MFA/atribuição/autoanálise, recurso com segundo analista e bloqueio `422`. `BenefitCalculatorTest` cobre cálculo sintético, limite 100 e arredondamento. Não substitui homologação de critérios profissionais.

O painel web ganhou acompanhamento/criação explícita de solicitação e `npm run build --prefix web` passou. `BrowserFlowTest -DbrowserTest=true` passou (navegador → API → PostgreSQL); o cenário de benefício usa política UUID sintética e não constitui homologação de elegibilidade.

Contrato, modelo, segurança, arquitetura, diagramas e ticket foram alinhados. Pendentes: teste em dispositivo mobile; critérios/responsável profissional, integrações, armazenamento/expurgo e alertas do ticket 05. Aprovação profissional/operacional permanece bloqueada; ticket 08 não iniciado, sem distribuição, contratação ou deploy.

## Ticket 08 — implementação parcial autorizada — 06/10/2026

Base `2299e5e` confirmada na branch `docs/planejamento-tecnico`; `referencia.md` preservada fora do Git. V8 adiciona referência administrativa de programa, aporte BRL com valor exato, evidência privada, estados PENDENTE/CONFIRMADO/REJEITADO, idempotência, auditoria e lançamentos confirmados. A aplicação não cria instituição/programa, não semeia saldo e não consulta banco externo.

Gestores financeiros exigem papel global nominal, vínculo ativo à instituição e MFA. Registro exige Idempotency-Key e permanece pendente. Revisão exige outro GF da mesma instituição, MFA, `If-Match`, evidência de conciliação própria aprovada e motivo; confirmação cria exatamente um lançamento. Disponibilidade soma somente `lancamento_aporte`; rejeições e pendências não alteram saldo.

`FundingFlowTest` passou com PostgreSQL 17.6/Testcontainers e HTTP real: isolamento entre instituições, MFA/papel, auto-confirmação, evidência de outra conta, valores inválidos, versão obsoleta, rejeição, replay e concorrência sem duplicação. `FundingBrowserFlowTest` também passou: Chromium → Vite → API real → PostgreSQL 17.6 descartável, com armazenamento privado temporário e sem interceptação; registrou/listou aporte pendente, confirmou disponibilidade zero, trocou para outro GF, exigiu MFA, inspecionou conciliação e confirmou, atualizando o painel para `CONFIRMADO`/`100.5`. O painel permite a revisão prevista no contrato. Automação de navegador é evidência funcional, não verificação visual manual ou acessibilidade; estas permanecem não executadas.

Permanecem bloqueados: responsáveis e procedimento financeiro reais, acordo/programa habilitado por governança, comprovação de transferência, eventual integração bancária, retenção/expurgo e liberação de subsídio. Pendências do ticket 05 e teste mobile continuam separadas. Sem movimentação externa, contratação, deploy ou ticket 09.

Ticket 09 permanece **não iniciado** (`ready-for-human`). Escopo atual: solicitação de entrega por paciente/familiar com origem/destino e comprovação de retirada; orçamento particular com rota, tarifa, distância, tempo/trânsito conhecidos, validade, centavos e arredondamento rastreáveis; reorçamento quando endereço mudar; privacidade de endereços/autorizações e escopo familiar `PEDIDOS`. Dependências: decisões D04/D05 e critérios de unidade/protocolo, destinatário autorizado, rota/provedor e modelo comercial ainda não aprovados; controles privados e revisão operacional do ticket 05; representação do ticket 04; e as políticas/capacidades de custeio do ticket 01. Não foram alterados arquivos do ticket 09, nem habilitado financiamento institucional ou subsídio.

## Ticket 09 — implementação parcial autorizada — 06/10/2026

Base `474cf951` confirmada na branch `docs/planejamento-tecnico`; `referencia.md` preservada. V9 adiciona pedido com endereços cifrados, autorização de retirada privada, escopos PEDIDOS/RECEBIMENTO, tarifa versionada, adaptador de rota, orçamento com composição/validade/origem da rota, idempotência e substituição por mudança de endereço. A unidade não é criada nem presumida como aceita; sem aceite o pedido fica `EM_VERIFICACAO`.

`OrderFlowTest` passou com PostgreSQL 17.6/Testcontainers e HTTP real: isolamento, familiar sem escopo/revogado, destinatário, evidência, endereço inválido, rota inválida/timeout, ausência de tarifa (`422`), cálculo sintético, replay, `412` e substituição. `OrderBrowserFlowTest` passou com Chromium → Vite → API → PostgreSQL real, armazenamento privado temporário e sem interceptação; anexou evidência, criou/acompanhou o pedido e exibiu o bloqueio de cotação sem aceite da unidade. Não é validação visual manual nem acessibilidade.

O adaptador padrão de rota permanece indisponível (`503 INTEGRATION_UNAVAILABLE`); respostas controladas são exclusivas dos testes e não homologam fornecedor. A tarifa de teste é sintética e não é política comercial. Permanecem bloqueados unidade/protocolo e retorno reais, provedor contratado, política tarifária/comercial, custódia, cobrança, reserva, designação e execução. Pendências do ticket 05, teste mobile e aprovação operacional permanecem separadas. Não iniciar ticket 10.

## Ticket 10 — implementação técnica parcial autorizada — 06/10/2026

Base autorizada `2cc16fd`, branch `docs/planejamento-tecnico`, inicialmente igual a `origin/docs/planejamento-tecnico`; `referencia.md` permaneceu não rastreada e sem alteração. O recorte não iniciou designação/ticket 11, liquidação, repasse, deploy, contratação ou movimentação financeira externa.

V10 implementa aceite append-only protegido também por trigger, com snapshot de valores, rota, tarifa, política apresentada, ator e versões do pedido/orçamento. Usuário/escopo é reavaliado; orçamento vencido/substituído, pedido alterado, valores/política divergentes e familiar revogado são recusados. Mudança de endereço substitui o orçamento e exige novo aceite; antes de qualquer designação libera somente a reserva local ainda não consumida e envia eventual cobrança para reconciliação, sem inventar política de cancelamento/estorno.

Particular não consulta instituição financiadora. Parcela positiva exige adaptador de pagamento habilitado, cria obrigação/outbox única e permanece `PENDENTE_PAGAMENTO`; somente evento autenticado e validado pode tornar a cobertura confirmada. O frontend não possui comando de confirmação. O adaptador padrão está indisponível e retorna `503`; não foi implementada chamada financeira externa. O contrato do adaptador recebe bytes/headers originais e devolve evento normalizado apenas após autenticação; ID+hash deduplicam, valor/moeda/destinatário divergentes são rejeitados, timeout/resultado incerto fica `INCERTA` e evento tardio não regride confirmação nem ressuscita orçamento substituído.

Subsidiado separa elegibilidade de disponibilidade: cotação e aceite exigem decisão aprovada ainda vigente, política ativa da mesma instituição e programa `HABILITADO`. `conta_programa` recebe apenas aportes confirmados e a reserva usa atualização condicional no PostgreSQL; falta de saldo retorna `409 FUNDING_INSUFFICIENT`. Paciente + subsídio permanece igual ao frete em banco e no snapshot. Reserva, aceite, operação e outbox pertencem à mesma transação; falha final testada reverte todos os efeitos.

Evidências executadas nesta rodada:

- `QuoteAcceptanceFlowTest` em PostgreSQL 17.6/Testcontainers: 3 testes, zero falhas/erros. Cobriu vencido, substituído, conflito de versão, familiar revogado, provedor/política ausentes, replay, corrida de dois aceites/reservas no último saldo, saldo insuficiente, liberação por novo endereço, rollback forçado sem efeito parcial, assinatura controlada ausente, duplicação, payload divergente, valor divergente, resultado incerto e evento tardio fora de ordem.
- Suíte Maven comum final: 40 testes descobertos, 35 aprovados e 5 opt-in de navegador ignorados. Uma execução intermediária expôs uma asserção histórica que calculava `max(version)` como texto e esperava V8; ela foi corrigida para inteiro/V10 antes da execução final verde. Após o último endurecimento de destinatário/evento, `QuoteAcceptanceFlowTest` passou novamente com 3/3.
- `AcceptanceBrowserFlowTest -DbrowserTest=true`: Chromium → Vite → API HTTP real → PostgreSQL 17.6 descartável, 1 teste aprovado. A interface aceitou as condições e mostrou `PENDENTE_PAGAMENTO`; banco confirmou um aceite, uma operação pendente e nenhuma confirmação de cobertura. É evidência funcional local, não homologação de adquirente/provedor financeiro.
- Build web e compilação Maven passaram. OpenAPI 3.0.3 passou em `openapi-spec-validator 0.7.2`; a validação também corrigiu três parâmetros de path preexistentes nas rotas V7 de recurso/revisão. `git diff --check` passou.

Conclusão separada: **particular tecnicamente parcial** — aceite/outbox/eventos e bloqueio sem integração concluídos, cobrança externa não homologada; **subsidiado tecnicamente parcial** — benefício/programa/saldo/reserva concorrente concluídos, sem programa ou responsáveis financeiros reais habilitados; **dependências externas bloqueadas** — provedor/credenciais/formato definitivo de evento, política de cancelamento/estorno, unidade/protocolo/retorno, tarifa comercial e homologação financeira. O ticket 10 inteiro permanece aberto. Próxima ação humana é revisar o conjunto e resolver essas dependências; não iniciar ticket 11.
