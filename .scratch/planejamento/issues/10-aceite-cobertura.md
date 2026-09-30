# 10: Paciente aceita orçamento e cobertura é confirmada

**What to build:** Paciente escolhe particular ou subsídio, aceita as parcelas e acompanha pendência/insuficiência até a entrega estar financiada.

**Blocked by:** 09 — Orçamento particular; 07 e 08 somente para a extensão subsidiada. Habilitação externa conforme 01–02, no escopo da modalidade.

**Status:** ready-for-human

- [ ] Banco, API e web preservam paciente + instituição = frete integral, combinando decisões conforme política aprovada.
- [ ] Aceite duplicado não duplica reserva/cobrança; duas reservas sobre último saldo não causam saldo negativo em PostgreSQL real.
- [ ] Sem recurso, mostrar espera/particular com novo aceite; não cobrar diferença automaticamente.
- [ ] Cobrança usa provedor real e outbox; webhook autenticado e cancelamento concorrente não ressuscitam pedido. Timeout fica INCERTA, não sucesso fictício.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D02/D05/D06/D12: caminho particular depende de 09 e de pagamento/políticas reais pertinentes, sem 07/08. Somente extensão subsidiada depende de 07/08. Exibir política de cancelamento versionada no aceite; não prometer remuneração integral automaticamente. Ambos bloqueiam operação enquanto faltar dependência real.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
