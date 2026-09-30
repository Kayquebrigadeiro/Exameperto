# 04: Paciente concede e revoga representação

**What to build:** Paciente cria perfil e autoriza familiar a agir dentro de escopos definidos, acompanhando as concessões na web.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 03 — Cadastrar, autenticar e recuperar conta

**Status:** ready-for-human

- [ ] Perfil persiste identidade pendente; CPF/cadastro não aprova benefício.
- [ ] Concessão e revogação atravessam banco, API e UI, com expiração, escopos e seleção segura de conta.
- [ ] REST rejeita ação em paciente alheio, autoconcessão/delegação pelo familiar e concessão expirada/revogada.
- [ ] Revogação concorrente com ação possui ponto efetivo documentado e testes no PostgreSQL quando depender dos locks.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D03: paciente adulto inicia convite privado, destinatário autenticado aceita e paciente reautenticado confirma escopos/expiração. Testar que aceite sozinho não concede acesso, token não reutiliza e revogação/expiração negam REST/STOMP. Documentar menores/representação legal fora da cobertura inicial; não exigir solução de representação legal para este recorte.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
