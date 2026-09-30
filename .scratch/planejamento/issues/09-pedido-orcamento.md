# 09: Paciente solicita entrega e recebe orçamento particular

**What to build:** Paciente/familiar informa origem/destino, comprova retirada e vê orçamento particular com rota e tarifa reais.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 04 — Paciente concede e revoga representação, 05 — Entregador envia comprovações e recebe revisão

**Status:** ready-for-human

- [ ] Persistência, REST e UI permitem solicitação/verificação/negação de retirada e orçamento sem financiamento institucional.
- [ ] Rota indisponível impede cotação; valor, distância, tempo, trânsito conhecido, tarifa e validade ficam rastreáveis.
- [ ] Interface pública de precificação verifica centavos exatos, arredondamento, expiração e mudança de endereço que exige novo orçamento.
- [ ] Endereços e autorizações ficam privados; familiar sem PEDIDOS não cria nem consulta pedido.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Depende também da capacidade de upload privado e revisão operacional do ticket 05; dependência explicitada no índice.

## Ajuste D01–D12 — 30/09/2026

D04/D05: unidade/protocolo de custódia e retorno aceitos, destinatário autorizado; operação bloqueada enquanto nenhuma unidade estiver confirmada. Rota real e parâmetros versionados, aceite, frete/subsídio/receita separados; valores/modelo comercial pendentes. Particular não depende de 07/08.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
