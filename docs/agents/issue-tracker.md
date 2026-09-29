# Tracker: Markdown local

Configuração escolhida pelo usuário em 29/09/2026; baseada no template `issue-tracker-local.md` de setup-matt-pocock-skills.

- Uma iniciativa por `.scratch/<iniciativa>/`; spec em `spec.md`.
- Um ticket por `issues/NN-slug.md`, em ordem de dependência, com `What to build`, `Blocked by`, `Status` e critérios verificáveis.
- Comentários posteriores ficam em `## Comments` no próprio arquivo.
- Publicar/fetch no tracker significa escrever/ler esses arquivos. Não criar GitHub Issues ou labels remotas nesta etapa.
- O backlog desta etapa é uma proposta para revisão, marcada `ready-for-human`. Não executar tickets de implementação sem autorização posterior.
- Vocabulário reservado: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. Não foi instalada a skill triage, portanto não foi feita configuração remota nem criado arquivo de mapeamento para ela.
- Índice e dependências: [backlog](../../.scratch/planejamento/README.md). Base Git inicial criada após confirmar remoto vazio: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`; ver spec para contexto.
