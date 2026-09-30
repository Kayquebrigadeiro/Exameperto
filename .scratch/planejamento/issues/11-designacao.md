# 11: Entregador aceita oferta com exclusividade

**What to build:** Entregador aprovado consulta oferta mínima e aceita tarefa; participantes passam a ver identificação operacional autorizada.

**Blocked by:** 06 — Entregador comprova vínculo com veículo, 10 — Paciente aceita orçamento e cobertura é confirmada

**Status:** ready-for-human

- [ ] App/web, REST e persistência expõem só cidades/frete antes do aceite e endereços mínimos depois.
- [ ] Dois aceites simultâneos em PostgreSQL real resultam em uma designação ativa; perdedor recebe conflito sem dados do vencedor.
- [ ] Veículo/vínculo aprovado é conferido no aceite; suspensão impede nova tarefa e gera tratamento da ativa.
- [ ] Replay idêntico retorna a mesma designação; foto operacional não revela CPF, CNH ou CRLV.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D04/D06: aceite vincula versão de remuneração/cancelamento e protocolo de custódia, sem garantia integral em cancelamento. Sem unidade/protocolo ou cobertura validados, não aceitar tarefa.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
