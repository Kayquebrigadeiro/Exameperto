# 13: Compartilhar localização real durante tarefa

**What to build:** App envia GPS real; paciente/familiar autorizado vê horário, precisão e aviso de posição desatualizada.

**Blocked by:** 11 — Entregador aceita oferta com exclusividade

**Status:** ready-for-human

- [ ] Fatias incluem persistência, HTTP, STOMP, tela e app; coleta cessa ao encerrar/revogar participação.
- [ ] CONNECT/SUBSCRIBE/despacho negam usuário alheio, curinga, sessão expirada e familiar revogado, inclusive mensagem em buffer.
- [ ] Interface pública rejeita ponto antigo/fora de ordem/designação alheia; reconexão mostra último ponto real sem interpolação fictícia.
- [ ] Verificar segundo plano em Android real/development build, perda de rede, bateria e política de frequência/retenção; registrar evidências e limitações.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Pode avançar junto da execução, mas demonstração de encerramento completo usa o ticket 12.

## Ajuste D01–D12 — 30/09/2026

D09: hipóteses 15 s/60 s, oito pontos/dois minutos, futuro 30 s e precisão >100 m. Registrar aparelho/SO, permissões, duração, rede, bateria e resultados; sem afirmar adequação antes do ensaio. Ocorrência só mantém GPS se designação/custódia e finalidade ativas.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
