# 08: Instituição registra aporte e confirma disponibilidade

**What to build:** Gestores de uma instituição registram recurso comprovado, outro revisor concilia e o painel mostra saldo real.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 02 — Definir privacidade, acesso privilegiado e provedores, 03 — Cadastrar, autenticar e recuperar conta, 05 — Entregador envia comprovações e recebe revisão

**Status:** ready-for-human

- [ ] Programa/acordo e habilitação têm procedimento auditado; banco/API/painel distinguem aporte PENDENTE de CONFIRMADO.
- [ ] Registro sem conciliação não aumenta saldo; revisor é distinto do registrador e pertence à mesma instituição.
- [ ] Revisão preserva comprovante original e vínculo separado da evidência de conciliação autorizada, revisor, data e motivo; rejeição também mantém a evidência, e replay não substitui a decisão.
- [ ] Replay de confirmação credita uma única vez e lançamentos reconciliam com saldo, em PostgreSQL real isolado.
- [ ] Instituição não vê saldo/documentos de outra; integração ausente aparece indisponível.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Reutiliza upload privado; não exige benefício aprovado para registrar recurso.
