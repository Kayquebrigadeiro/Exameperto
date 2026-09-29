# 04: Paciente concede e revoga representação

**What to build:** Paciente cria perfil e autoriza familiar a agir dentro de escopos definidos, acompanhando as concessões na web.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 03 — Cadastrar, autenticar e recuperar conta

**Status:** ready-for-human

- [ ] Perfil persiste identidade pendente; CPF/cadastro não aprova benefício.
- [ ] Concessão e revogação atravessam banco, API e UI, com expiração, escopos e seleção segura de conta.
- [ ] REST rejeita ação em paciente alheio, autoconcessão/delegação pelo familiar e concessão expirada/revogada.
- [ ] Revogação concorrente com ação possui ponto efetivo documentado e testes no PostgreSQL quando depender dos locks.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.
