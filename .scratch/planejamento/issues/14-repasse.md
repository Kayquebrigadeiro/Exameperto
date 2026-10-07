# 14: Confirmar repasse e conciliar resultado incerto

**What to build:** Entregador acompanha valor devido e transferência real, com conciliação de falha/timeout sem pagamento duplicado.

**Blocked by:** 12 — Executar retirada, ocorrência e entrega comprovada

**Status:** implementação independente concluída; dependências externas pendentes

- [x] Persistência, adaptador financeiro, REST e tela distinguem obrigação/liquidação local, repasse solicitado, falho/incerto e confirmação externa.
- [x] Chave `repasse:{pedido}` persiste entre tentativas; timeout consulta a mesma operação; ID/hash divergente, valor, moeda ou destinatário geram reconciliação.
- [x] Eventos duplicados/fora de ordem não duplicam repasse nem lançamentos; cenários concorrentes foram executados em PostgreSQL 17.6 real descartável.
- [x] Paciente e entregador consultam apenas suas parcelas; painel institucional exige GF nominal, vínculo institucional e MFA; logs/erros não expõem dados bancários.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D05/D06: repasse do serviço completo usa frete acordado; cancelamento/ocorrência usa valor apurado e cobertura comprovada. Transferência e receita da plataforma separadas; não presumir frete integral devido no aceite.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

Cancelamento, ocorrência e serviço parcial permanecem `BLOQUEADA` sem política aprovada. O adaptador de produção continua indisponível; o adaptador controlado existe somente em testes. Implementação não homologa banco, não transfere dinheiro e não encerra o ticket enquanto provedor, responsáveis, retenção financeira e regras operacionais não forem habilitados.
