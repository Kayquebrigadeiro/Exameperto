# 12: Executar retirada, ocorrência e entrega comprovada

**What to build:** Entregador e destinatário acompanham a tarefa até entrega com código ou resolução auditada de ocorrência.

**Blocked by:** 11 — Entregador aceita oferta com exclusividade

**Status:** ready-for-human

- [ ] App/web, API e estados persistentes cobrem retirada autorizada, início, código, conclusão, cancelamento e ocorrência.
- [ ] Código só é emitido ao destinatário autorizado, expira, limita tentativas e não pode ser usado duas vezes.
- [ ] Encerramento é atômico com liquidação local, evento e outbox; soma de parcelas deve o frete integral ao entregador.
- [ ] Cancelamento pós-retirada não apaga remuneração; resolução sem política/cobertura fica pendente; entregador alheio não movimenta pedido.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.
