# 07: Paciente solicita benefício e recebe decisão

**What to build:** Paciente/familiar envia comprovação, analista da instituição decide e paciente vê resultado e possibilidade de recurso.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 04 — Paciente concede e revoga representação, 05 — Entregador envia comprovações e recebe revisão

**Status:** ready-for-human

- [ ] Fatias de banco, REST e web cobrem IDADE, DEFICIENCIA e RENDA separadas, política versionada, validade e recurso.
- [ ] Comprovação não é aprovada por CPF/CID isolado; decisão favorável não cria saldo ou contratação.
- [ ] Analista atribuído só vê sua instituição/caso; familiar depende de BENEFICIOS e revogação tem efeito nas leituras.
- [ ] Interface pública de elegibilidade testa aniversário de 60 anos, validade/ausência de prova, renda independente e teto de 100%, usando apenas política aprovada.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Reutiliza upload privado do ticket 05; não depende de um entregador específico aprovado.

## Ajuste D01–D12 — 30/09/2026

D01/D02: registrar verificações mínimas reutilizáveis com origem, política, validade e finalidade; recurso exige outro analista real, sem diagnóstico. Testar negativa ao analista original e reuso revogado/incompatível. Políticas versionadas/configuráveis, sem ativar 50%/25% ou conceder benefício real sem financiador.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
