# 07: Paciente solicita benefício e recebe decisão

**What to build:** Paciente/familiar envia comprovação, analista da instituição decide e paciente vê resultado e possibilidade de recurso.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 04 — Paciente concede e revoga representação, 05 — Entregador envia comprovações e recebe revisão

**Status:** implemented-partially (06/10/2026; aprovação operacional bloqueada)

- [x] Banco e REST cobrem IDADE, DEFICIENCIA e RENDA separadas, política versionada/snapshot, validade e recurso; painel web de acompanhamento permanece fatia seguinte.
- [x] Comprovação permanece privada e não é aprovada por CPF/e-mail/upload ou inspeção estrutural; decisão não cria saldo, financiamento ou contratação.
- [x] Analista atribuído só vê a instituição/caso, com MFA e impedimento de autoanálise; familiar depende de BENEFICIOS vigente e revogação/expiração afeta leituras.
- [ ] Critérios profissionais, responsáveis, percentuais/documentos aceitos e interface homologada de elegibilidade ainda não existem; não foram ativados defaults nem aprovação automática.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Reutiliza upload privado do ticket 05; não depende de um entregador específico aprovado.

## Ajuste D01–D12 — 30/09/2026

D01/D02: registrar verificações mínimas reutilizáveis com origem, política, validade e finalidade; recurso exige outro analista real, sem diagnóstico. Testar negativa ao analista original e reuso revogado/incompatível. Políticas versionadas/configuráveis, sem ativar 50%/25% ou conceder benefício real sem financiador.

- [x] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Evidência técnica — 06/10/2026

`BenefitFlowTest` executou HTTP real → Spring Boot → PostgreSQL 17.6 descartável via Testcontainers (1 teste, 0 falhas/erros/skips), aplicando V1–V7 em banco vazio. Cobriu isolamento entre pacientes/instituição, familiar sem escopo vigente, documento de outra conta, `412` por versão obsoleta sem sobrescrita, substituição/histórico, MFA, atribuição, autoanálise, recurso por segundo analista e `422 POLICY_UNDEFINED`. `BenefitCalculatorTest` cobre limites sintéticos 0–100 e arredondamento HALF_UP. A execução não homologa critérios operacionais.

Pendências: fluxo web visual completo e dispositivo mobile, política/responsáveis reais, armazenamento/expurgo/integrações e aprovação profissional do ticket 05. Não iniciar ticket 08, distribuir ou liberar operação.
