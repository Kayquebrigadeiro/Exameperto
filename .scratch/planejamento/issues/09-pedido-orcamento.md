# 09: Paciente solicita entrega e recebe orçamento particular

**What to build:** Paciente/familiar informa origem/destino, comprova retirada e vê orçamento particular com rota e tarifa reais.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 04 — Paciente concede e revoga representação, 05 — Entregador envia comprovações e recebe revisão

**Status:** implemented-partially (06/10/2026; operação externa bloqueada)

- [x] Persistência, REST e UI permitem solicitação, acompanhamento e evidência privada de retirada sem financiamento institucional; unidade permanece sem aceite presumido.
- [x] Adaptador de rota identificado impede cotação quando indisponível; resposta inválida é rejeitada e distância, duração, trânsito, origem e horário ficam rastreáveis.
- [x] Tarifa sintética de teste usa versão, composição BRL exata, arredondamento, validade, idempotência; alteração de endereço substitui orçamento e exige nova cotação.
- [x] Endereços e autorizações ficam privados; paciente/familiar é validado no backend por PEDIDOS/RECEBIMENTO vigente e conta alheia é rejeitada.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Depende também da capacidade de upload privado e revisão operacional do ticket 05; dependência explicitada no índice.

## Ajuste D01–D12 — 30/09/2026

D04/D05: unidade/protocolo de custódia e retorno aceitos, destinatário autorizado; operação bloqueada enquanto nenhuma unidade estiver confirmada. Rota real e parâmetros versionados, aceite, frete/subsídio/receita separados; valores/modelo comercial pendentes. Particular não depende de 07/08.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Evidência técnica — 06/10/2026

`OrderFlowTest` passou contra PostgreSQL 17.6/Testcontainers, HTTP real e armazenamento privado temporário. Cobriu isolamento, escopo PEDIDOS, destinatário RECEBIMENTO, evidência privada, endereços inválidos, ausência de tarifa (`422 POLICY_UNDEFINED`), rota inválida/timeout (`422`/`503`), cálculo sintético (`R$ 38,00`), replay idempotente, `412` de versão, substituição por mudança de endereço e revogação.

`OrderBrowserFlowTest` passou em Chromium → Vite → API → PostgreSQL real isolado, sem interceptação da API. O cenário anexou evidência pelo formulário, criou e acompanhou o pedido em `EM_VERIFICACAO`, verificou as mensagens do painel e confirmou o bloqueio de cotação enquanto a unidade não aceita o procedimento. Esse ensaio é funcional, não verificação visual manual ou acessibilidade.

O `RouteProvider` padrão permanece desabilitado; respostas controladas existem apenas no teste isolado e não homologam fornecedor. Não há cobrança, reserva financeira, designação, motorista ou execução da entrega. Permanecem bloqueados: unidade/protocolo e retorno reais, provedor de rota contratado, política tarifária/comercial aprovada, critérios de custódia, revisão operacional do ticket 05, cobrança e etapas posteriores. Não iniciar o ticket 10.
