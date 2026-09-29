# 10: Paciente aceita orçamento e cobertura é confirmada

**What to build:** Paciente escolhe particular ou subsídio, aceita as parcelas e acompanha pendência/insuficiência até a entrega estar financiada.

**Blocked by:** 07 — Paciente solicita benefício e recebe decisão, 08 — Instituição registra aporte e confirma disponibilidade, 09 — Paciente solicita entrega e recebe orçamento particular

**Status:** ready-for-human

- [ ] Banco, API e web preservam paciente + instituição = frete integral, combinando decisões conforme política aprovada.
- [ ] Aceite duplicado não duplica reserva/cobrança; duas reservas sobre último saldo não causam saldo negativo em PostgreSQL real.
- [ ] Sem recurso, mostrar espera/particular com novo aceite; não cobrar diferença automaticamente.
- [ ] Cobrança usa provedor real e outbox; webhook autenticado e cancelamento concorrente não ressuscitam pedido. Timeout fica INCERTA, não sucesso fictício.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.
