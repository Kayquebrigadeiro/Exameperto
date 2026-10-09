# 04: Paciente concede e revoga representação

**What to build:** Paciente cria perfil e autoriza familiar a agir dentro de escopos definidos, acompanhando as concessões na web.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 03 — Cadastrar, autenticar e recuperar conta

**Status:** ready-for-human — implementação local validada; SMTP privado pendente. No portfólio V1, o perfil de paciente/autorizações familiares está utilizável condicionalmente; perfil básico da conta e privacidade são fluxos separados em [PORTFOLIO-V1](../../../docs/PORTFOLIO-V1.md).

- [x] Perfil persiste identidade pendente; CPF/cadastro não aprova benefício.
- [x] Concessão e revogação atravessam banco, API e UI, com expiração, escopos e seleção segura de conta.
- [x] REST rejeita ação em paciente alheio, autoconcessão/delegação pelo familiar e concessão expirada/revogada.
- [x] Revogação concorrente com ação possui ponto efetivo documentado e testes no PostgreSQL quando depender dos locks.

Implementação local autorizada e validada sobre a base `b33acfa`; SMTP externo permanece pendente de homologação.

## Ajuste D01–D12 — 30/09/2026

D03: paciente adulto inicia convite privado, destinatário autenticado aceita e paciente reautenticado confirma escopos/expiração. Testar que aceite sozinho não concede acesso, token não reutiliza e revogação/expiração negam REST/STOMP. Documentar menores/representação legal fora da cobertura inicial; não exigir solução de representação legal para este recorte.

- [x] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Comments

02/10/2026 — Implementado após autorização do autor a partir de `b33acfa`. V3 persiste perfil mínimo, convite privado vinculado ao destinatário por HMAC, hash de token imprevisível, expiração/uso único, aceite autenticado, confirmação reautenticada, escopos explícitos e concessão revogável. A fronteira HTTP consulta a concessão no PostgreSQL em cada leitura; módulos de pedidos, benefícios, documentos e STOMP ainda precisam integrar essa decisão quando autorizados. Menores e representação legal permanecem fora do recorte.

`./scripts/verify-ticket-04.sh` passou: 25 testes Maven descobertos, 23 executados sem falha e 2 opt-in executados separadamente; build web, dois testes Playwright de UI, navegador integrado do ticket 03, navegador integrado do ticket 04 e inspeção do JAR passaram. Os ensaios usaram PostgreSQL 17.6/Testcontainers, Chromium e dados sintéticos descartáveis. O capturador de confirmação/convite existe somente em `src/test`, é removido ao final e não homologa entrega externa.

O SMTP real permanece desabilitado/não homologado. Sem canal ou com falha de transporte a API retorna `503 INTEGRATION_UNAVAILABLE` e reverte convite e escopos; a resposta, listagens e logs de aplicação não expõem token/link. Não iniciar o ticket 05 nem deploy.

08/10/2026 — Reconciliação V1: a gestão familiar existente é acessível na web após login e mantém os escopos/expiração/revogação desta spec; ela não transforma a conta em representante legal e não habilita pedidos, benefícios ou operações externas. Convites continuam condicionados ao SMTP privado. O novo painel de perfil básico (`/me/profile`) não substitui o perfil de paciente (`/me/patient`), que segue com identidade `PENDENTE` até verificação própria.
