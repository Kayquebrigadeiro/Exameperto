# 14: Confirmar repasse e conciliar resultado incerto

**What to build:** Entregador acompanha valor devido e transferência real, com conciliação de falha/timeout sem pagamento duplicado.

**Blocked by:** 12 — Executar retirada, ocorrência e entrega comprovada

**Status:** ready-for-human

- [ ] Persistência, adaptador financeiro, REST e telas distinguem liquidação local, repasse pendente e confirmação bancária.
- [ ] Chave de negócio persiste entre retries; timeout consulta a mesma operação antes de tentar de novo; webhook com ID/hash divergente gera reconciliação.
- [ ] Eventos duplicados/fora de ordem e estorno não duplicam repasse nem lançamentos; executar cenários transacionais com PostgreSQL real.
- [ ] Paciente, entregador e instituição só consultam operações de suas parcelas; logs e erros não expõem dados bancários.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D05/D06: repasse do serviço completo usa frete acordado; cancelamento/ocorrência usa valor apurado e cobertura comprovada. Transferência e receita da plataforma separadas; não presumir frete integral devido no aceite.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
