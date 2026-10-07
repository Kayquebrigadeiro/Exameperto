# 13: Compartilhar localização real durante tarefa

**What to build:** App envia GPS real; paciente/familiar autorizado vê horário, precisão e aviso de posição desatualizada.

**Blocked by:** 11 — Entregador aceita oferta com exclusividade

**Status:** ready-for-human — implementação técnica parcial

- [x] Fatias incluem persistência, HTTP, STOMP, tela e app; coleta cessa ao encerrar/logout e o servidor cessa acesso ao encerrar/revogar participação.
- [x] CONNECT/SUBSCRIBE/despacho negam usuário alheio, curinga, sessão expirada e familiar sem escopo/revogado, inclusive conexão já aberta.
- [x] Interface pública rejeita ponto antigo/futuro/fora de ordem/designação alheia; reconexão mostra último ponto real com `stale`, sem interpolação fictícia.
- [ ] Verificar segundo plano em Android real/development build, perda de rede, bateria e política de frequência/retenção; registrar evidências e limitações.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Pode avançar junto da execução, mas demonstração de encerramento completo usa o ticket 12.

## Ajuste D01–D12 — 30/09/2026

D09: hipóteses 15 s/60 s, oito pontos/dois minutos, futuro 30 s e precisão >100 m. Registrar aparelho/SO, permissões, duração, rede, bateria e resultados; sem afirmar adequação antes do ensaio. Ocorrência só mantém GPS se designação/custódia e finalidade ativas.

- [x] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Comments

07/10/2026 — Implementação independente a partir de `d40eb5f`: V13, HTTP, STOMP privado, tela web e captura foreground Expo com permissão explícita. `TrackingFlowTest` usa HTTP/WebSocket e PostgreSQL 17.6 reais com coordenadas sintéticas e cobre contas alheias, familiar sem escopo/revogado, logout, expiração, tarefa encerrada, validade/ordem/deduplicação, reconexão/stale e limites. Rastreamento fica desabilitado por padrão porque a retenção não foi aprovada.

Pendente para concluir: development build em aparelho/emulador Android real, permissão e captura física, perda de rede prolongada, segundo plano, consumo de bateria, precisão e validação dos parâmetros/retenção. Testes de transporte não comprovam esses critérios. Ticket não concluído.
