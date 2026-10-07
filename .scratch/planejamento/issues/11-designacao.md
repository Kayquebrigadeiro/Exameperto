# 11: Entregador aceita oferta com exclusividade

**What to build:** Entregador aprovado consulta oferta mínima e aceita tarefa; participantes passam a ver identificação operacional autorizada.

**Blocked by:** 06 — Entregador comprova vínculo com veículo, 10 — Paciente aceita orçamento e cobertura é confirmada

**Status:** ready-for-human — implementação independente parcial

- [x] App/web, REST e persistência expõem só cidades/frete antes do aceite e endereços mínimos depois.
- [x] Dois aceites simultâneos em PostgreSQL real resultam em uma designação ativa; perdedor recebe versão superada/conflito sem dados do vencedor.
- [ ] Veículo/vínculo aprovado é conferido no aceite; suspensão impede nova tarefa e gera tratamento da ativa.
- [x] Replay idêntico retorna a mesma designação; foto operacional não revela CPF, CNH ou CRLV.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D04/D06: aceite vincula versão de remuneração/cancelamento e protocolo de custódia, sem garantia integral em cancelamento. Sem unidade/protocolo ou cobertura validados, não aceitar tarefa.

- [x] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md), ressalvado o ensaio móvel em dispositivo e as dependências reais abaixo.

## Comments

### Implementação independente autorizada — 06/10/2026

Implementadas migração V11, API e interfaces para oferta mínima e designação exclusiva. A oferta exige cobertura efetivamente confirmada: cobrança particular `CONFIRMADA` quando devida e reserva subsidiada integral ainda `RESERVADA`. O aceite bloqueia pedido e entregador/vínculo, reavalia aprovação, vigência, cobertura, protocolo e capacidade, grava snapshots/evento/idempotência e atualiza o pedido na mesma transação. Uma designação ativa por pedido é reforçada por índice parcial; aceites paralelos do mesmo entregador são serializados pelo lock e respeitam `politica_designacao`.

Antes do aceite, o app recebe somente cidades, distância, frete/moeda, versão e política. Endereços completos ficam restritos ao paciente/familiar pertinente e ao entregador ativamente designado; a web do paciente acompanha status e identidade operacional mínima. Saúde, renda, benefício, CPF, CNH e CRLV não integram essas projeções.

Testes PostgreSQL 17.6/Testcontainers cobrem dois entregadores no mesmo pedido, capacidade concorrente, replay, versão obsoleta, perda de aprovação/vínculo, coberturas pendente/incerta/insuficiente/liberada, corridas com endereço/cancelamento, evento financeiro tardio e acesso por entregador alheio. Aprovações, política, protocolo e cobertura são inseridos sinteticamente apenas nesses bancos descartáveis. O fluxo Chromium → web → API → PostgreSQL comprova o acompanhamento do paciente separadamente; typecheck/export Android não comprovam execução móvel em dispositivo.

O critério de suspensão está apenas parcialmente concluído: suspensão impede novas tarefas, mas o tratamento seguro de uma designação já ativa depende das regras ainda pendentes de ocorrência/cancelamento, custódia e apuração. A aplicação não semeia aprovação profissional, política de capacidade ou protocolo/cobertura; portanto a operação real permanece bloqueada. Retirada, rastreamento, liquidação/repasse e cancelamento pós-designação não foram iniciados.
