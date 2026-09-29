# Backlog proposto para revisão

[Spec](spec.md) · [Status da etapa](../../docs/STATUS.md)

Todos os tickets estão `ready-for-human`, sem execução autorizada. Os tickets 01–02 são decisões; os demais são fatias completas com persistência, API, interface e verificações observáveis. Dependências técnicas não substituem autorização para implementar. Valores e fornecedores só entram após decisões reais; partes independentes podem ser desenvolvidas com integração explicitamente indisponível.

Base recebida sem histórico. A base documental foi criada preservando os cinco arquivos originais: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`, branch `docs/planejamento-tecnico`. Remoto confirmado pelo usuário: `https://github.com/Kayquebrigadeiro/Exameperto.git` (vazio na consulta inicial).

| Ticket / entrega verificável | Bloqueado por |
|---|---|
| [01 — Aprovar comprovações, representação e custeio](issues/01-decisoes-operacionais.md) | — |
| [02 — Definir privacidade, acesso privilegiado e provedores](issues/02-privacidade-integracoes.md) | — |
| [03 — Cadastrar, autenticar e recuperar conta](issues/03-cadastro-sessoes.md) | 02 |
| [04 — Paciente concede e revoga representação](issues/04-paciente-familiar.md) | 01, 03 |
| [05 — Entregador envia comprovações e recebe revisão](issues/05-entregador-aprovado.md) | 02, 03 |
| [06 — Entregador comprova vínculo com veículo](issues/06-veiculo-aprovado.md) | 05 |
| [07 — Paciente solicita benefício e recebe decisão](issues/07-beneficio.md) | 01, 04, 05 |
| [08 — Instituição registra aporte e confirma disponibilidade](issues/08-aporte.md) | 01, 02, 03, 05 |
| [09 — Paciente solicita entrega e recebe orçamento particular](issues/09-pedido-orcamento.md) | 01, 04, 05 |
| [10 — Paciente aceita orçamento e cobertura é confirmada](issues/10-aceite-cobertura.md) | 07, 08, 09 |
| [11 — Entregador aceita oferta com exclusividade](issues/11-designacao.md) | 06, 10 |
| [12 — Executar retirada, ocorrência e entrega comprovada](issues/12-execucao.md) | 11 |
| [13 — Compartilhar localização real durante tarefa](issues/13-rastreamento.md) | 11 |
| [14 — Confirmar repasse e conciliar resultado incerto](issues/14-repasse.md) | 12 |
| [15 — Atender titular e verificar operação antes de deploy](issues/15-titular-operacao.md) | 02, 12, 13, 14 |

As escolhas de granulação, dependências e eventual divisão/união ficam para a revisão deste backlog concreto. O fluxo `to-tickets` normalmente publica tickets após revisão; conforme o prompt desta etapa, estes arquivos locais são a proposta, não uma fila liberada para implementação.
