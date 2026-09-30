# Revisão documental da etapa

Skill aplicada: [code-review do fork indicado](https://github.com/Kayquebrigadeiro/skillsproject/blob/6654f6b60cd9d5be8b54c6fafe44346dabeb3b76/skills/engineering/code-review/SKILL.md). Dois agentes independentes e somente leitura, conforme autorização específica do prompt. Nenhuma outra delegação foi usada.

Base: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`, criada com os cinco arquivos originais porque não havia HEAD no workspace nem histórico remoto. Primeiro conjunto revisto: `d8930de` e `093f39a`. Comando: `git diff 4ff66bf2332de25e48aa2bc884825b12a8b35f11...HEAD`. Spec: [planejamento](../.scratch/planejamento/spec.md); normas: [AGENTS](../AGENTS.md) e [prompt autorizado](../PROMPT-CODEX-EXAME-PERTO.md).

## Standards

Relatório inicial, conservando a separação entre violações e julgamentos:

1. **P2 — Limites de VehicleInput incompatíveis com persistência.** Marca/modelo/cor aceitavam 200 caracteres, enquanto o banco previa 80/80/40; anos não tinham máximo correspondente a smallint. Viola a coerência Dados/API de AGENTS.
2. **P2 — Replay de upload sem resposta implementável.** Criação idempotente retornava URL temporária obrigatória, mas a persistência da idempotência proibia URLs e não existia reemissão da URL para registro existente. Viola a coerência Dados/API e a garantia de idempotência do modelo.
3. **P2 — Foto operacional pendente sem vínculo persistente.** A API recebia operationalPhotoId, mas o banco só descrevia foto aprovada e finalidades RG/CNH/selfie. Não suportava corretamente a exigência de nova revisão da foto.

Correções: limites alinhados; criação retorna só Document, emissão de URL movida para consulta autorizada ao registro existente; finalidade FOTO_OPERACIONAL adicionada ao vínculo submetido, distinta da aprovação. Nenhum julgamento adicional de smells foi relatado, pois não há código de aplicação.

## Spec

Relatório inicial:

1. **P1 — Retomada de ocorrência pré-retirada saltava retirada autorizada.** Diagrama/modelo permitiam ACEITA → OCORRENCIA → EM_ENTREGA. O contrato oferecia somente REENTREGA/ENCERRAR. Isso violava a história de retirada autorizada, ocorrência e entrega comprovada da spec.

Correção: ocorrência registra estado_origem; RETOMAR restaura apenas ACEITA, RETIRADA ou EM_ENTREGA conforme origem persistida. Retomar ACEITA ainda exige confirmar retirada; custo novo continua dependente de política, concordância e cobertura. Nenhum desvio material de escopo foi encontrado.

## Ajustes complementares e fechamento

Revisão local também explicitou leitura das referências de evidências pelo analista autorizado e consulta do protocolo de privacidade pelo titular, fluxos já previstos na spec, além de limpeza do cookie no logout. Não houve implementação da aplicação.

Totais iniciais: Standards 3 (maior severidade P2); Spec 1 (maior severidade P1). As quatro correções foram conferidas no conjunto completo até `63e033d` na retomada, por dois revisores independentes. Esta revisão não substitui testes de aplicação, segurança ou transações PostgreSQL.


## Conferência na retomada

Base mantida em `4ff66bf`; HEAD recebido `63e033d93e081c812e1a2c99aa1de321481c2447`. Mesma skill e fonte, consultadas novamente em cópia temporária; setup anterior preservado, sem instalação permanente.

### Standards

As três correções anteriores foram confirmadas. Novo **P2 — evidência de conciliação sem vínculo persistente**: `FundingReview.reconciliationEvidenceId` era obrigatório, mas `aporte` só preservava o comprovante original. Isso impedia rastrear a evidência da revisão independente, contrariando a coerência Dados/API de AGENTS e a confirmação verificável de recursos.

Correção documental: aporte preserva separadamente evidência da revisão, revisor, data e motivo; revisão única sob lock e na transação do eventual crédito. Contrato retorna referências autorizadas, segurança restringe instituição/finalidade, diagrama explicita os vínculos e ticket 08 exige sua preservação. Nenhum smell adicional relatado.

### Spec

Zero novos achados materiais ou desvios de escopo. Retomada de ocorrência restaura somente a origem persistida, mantendo confirmação posterior de retirada quando a origem é ACEITA. O P1 anterior está corrigido.

Resultado da conferência até `63e033d`: Standards 1 novo P2; Spec 0 novos achados. A correção foi commitada em `84631aa4244120c8ddfa6dea00355fa8e8fde594` e conferida pelos mesmos dois revisores no diff corretivo e no conjunto cumulativo contra a base antes do envio.


## Fechamento da revisão

### Standards

P2 de conciliação encerrado: vínculo separado, revisor, data e motivo persistidos; autorização, revisão única e crédito atômico coerentes entre modelo, contrato, segurança, diagrama e ticket. As três correções anteriores permanecem válidas. Nenhuma nova violação ou heurística relatada.

### Spec

Zero novos achados. A correção atende à história de recursos comprovados, preserva as garantias anteriores e permanece no escopo documental.

Resultado do conjunto até `84631aa`: **Standards 0 pendentes; Spec 0 pendentes**. Primeiro push desse conjunto concluído para `origin/docs/planejamento-tecnico`; sem implementação ou deploy. O commit de fechamento altera somente este relatório e o status, com conferência local do diff antes de envio.

## Rodada D01–D12 — 30/09/2026

Skill code-review da mesma fonte e SHA registrados acima, consultada em cópia temporária. Base capturada: `7a8077fa6a7b80e8ba642d3fe2838bd299859ba3`; diff `git diff 7a8077f...HEAD`. Commits `543279c` e `c498e90`. Dois revisores somente leitura, com diff, spec, decisões D01–D12 e normas pertinentes; delegação restrita à revisão autorizada.

### Standards

**P1 identificado e corrigido:** o primeiro commit exigia documento DISPONIVEL para encerrar qualquer custódia, mas entrega normal recebia somente código. Modelo/contrato não permitiam concluir a entrega mantendo a nova guarda. Correção em `c498e90`: ENTREGA usa prova estruturada de validação no evento ENTREGUE e encerra custódia/designação na mesma transação da liquidação; RETORNO exige documento do pedido e unidade autorizada. Sem código em claro ou documento fictício.

Revisão local complementar explicitou vínculo de documentos operacionais ao pedido e upload E/AO por finalidade, exposição das verificações reutilizadas ao analista, aceite da versão da política pelo entregador e erros de versão nos comandos novos. Standards reconferiu correção e conjunto cumulativo: **zero achados materiais restantes**. Heurísticas de código não se aplicam a esta rodada documental.

### Spec

**Zero achados materiais** na primeira revisão e na conferência do conjunto corrigido. D01–D12 propagadas, particular separado de subsídio, cadastro/segurança sem financiador obrigatório, 03A com aceite verificável de validação/bloqueio local e sem alegar cadastro completo ou marco funcional inteiro. Sem autorização indevida de implementação/deploy ou dependências fictícias.

Fechamento: Standards 1 P1 corrigido, 0 pendentes; Spec 0 achados. Validações documentais efetivas e publicação registradas em STATUS; nenhuma revisão substitui testes de aplicação ou operação real. Este registro e o status de fechamento recebem conferência local de diff/links antes do envio.
