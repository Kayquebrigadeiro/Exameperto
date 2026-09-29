# 14: Confirmar repasse e conciliar resultado incerto

**What to build:** Entregador acompanha valor devido e transferência real, com conciliação de falha/timeout sem pagamento duplicado.

**Blocked by:** 12 — Executar retirada, ocorrência e entrega comprovada

**Status:** ready-for-human

- [ ] Persistência, adaptador financeiro, REST e telas distinguem liquidação local, repasse pendente e confirmação bancária.
- [ ] Chave de negócio persiste entre retries; timeout consulta a mesma operação antes de tentar de novo; webhook com ID/hash divergente gera reconciliação.
- [ ] Eventos duplicados/fora de ordem e estorno não duplicam repasse nem lançamentos; executar cenários transacionais com PostgreSQL real.
- [ ] Paciente, entregador e instituição só consultam operações de suas parcelas; logs e erros não expõem dados bancários.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.
