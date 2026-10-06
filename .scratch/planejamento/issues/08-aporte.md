# 08: Instituição registra aporte e confirma disponibilidade

**What to build:** Gestores de uma instituição registram recurso comprovado, outro revisor concilia e o painel mostra saldo real.

**Blocked by:** 01 — Aprovar comprovações, representação e custeio, 02 — Definir privacidade, acesso privilegiado e provedores, 03 — Cadastrar, autenticar e recuperar conta, 05 — Entregador envia comprovações e recebe revisão

**Status:** implemented-partially (06/10/2026; confirmação operacional bloqueada)

- [x] Banco/API/painel distinguem aporte PENDENTE, CONFIRMADO e REJEITADO; programa é referência pré-existente e não há criação/seed pela aplicação.
- [x] Registro sem conciliação não aumenta saldo; revisor exige GF distinto, mesma instituição e MFA.
- [x] Revisão preserva comprovante original e evidência de conciliação separada, revisor, data, motivo e auditoria; replay não substitui decisão.
- [x] Replay/concorrência creditam uma única vez e disponibilidade soma somente lançamentos confirmados em PostgreSQL real isolado.
- [x] Instituição/programa alheios são isolados; não há integração bancária presumida.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

Reutiliza upload privado; não exige benefício aprovado para registrar recurso.

## Ajuste D01–D12 — 30/09/2026

D02/D10/D11: MFA e duas pessoas reais distintas para registro/revisão; sem revisor independente não confirmar. Nenhum programa/financiador fictício, conta padrão, saldo inicial inventado ou contrato presumido.

- [x] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Evidência técnica — 06/10/2026

`FundingFlowTest` executou HTTP → Spring Boot → Flyway V1–V8 → PostgreSQL 17.6 descartável via Testcontainers. Cobriu falta de MFA, papel/atribuição, auto-confirmação, evidência de outra conta, valores inválidos, `412` obsoleto, instituições isoladas, rejeição sem saldo, replay idempotente e duas confirmações concorrentes com um único lançamento. Dados e instituição/programa foram sintéticos e criados somente no banco de teste.

Critérios ainda bloqueados: procedimento/acordo real, responsáveis financeiros reais, evidência de transferência e eventual integração bancária; portanto nenhum saldo real, subsídio ou confirmação operacional foi liberado.
