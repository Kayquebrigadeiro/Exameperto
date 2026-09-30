# 12: Executar retirada, ocorrência e entrega comprovada

**What to build:** Entregador e destinatário acompanham a tarefa até entrega com código ou resolução auditada de ocorrência.

**Blocked by:** 11 — Entregador aceita oferta com exclusividade

**Status:** ready-for-human

- [ ] App/web, API e estados persistentes cobrem retirada autorizada, início, código, conclusão, cancelamento e ocorrência.
- [ ] Código só é emitido ao destinatário autorizado, expira, limita tentativas e não pode ser usado duas vezes.
- [ ] Encerramento é atômico com liquidação local, evento e outbox; serviço completo deve frete acordado, enquanto cancelamento/ocorrência usa apuração de serviço comprovado.
- [ ] Cancelamento pós-retirada não apaga remuneração; resolução sem política/cobertura fica pendente; entregador alheio não movimenta pedido.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D04/D06: retirada registra custódia, prova mínima e cobertura de retorno. Antes da retirada, interrupção encerra deslocamento e abre apuração sem liberar toda reserva; após retirada, ocorrência mantém custódia até destino comprovado. Apurar deslocamento/serviço, consumir devido e liberar somente excedente. Testar cancelamento após designação, retorno sem cobertura, idempotência e conservação de parcelas; não inventar valores/multas/responsabilidades.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
