# Exame Perto

Etapa atual: planejamento técnico documental. Implementação e deploy exigem autorização posterior. Leia `docs/STATUS.md` para continuidade e `PROMPT-CODEX-EXAME-PERTO.md` para o escopo autorizado.

## Agent skills

- **Tracker:** Markdown local; ao criar/ler specs ou tickets, siga `docs/agents/issue-tracker.md`.
- **Domínio:** antes de explorar conceitos, leia `CONTEXT.md`; ao alterar relações/decisões, consulte `docs/agents/domain.md` e o índice `docs/DECISOES.md`.
- **Dados/API:** ao alterar contratos, confira juntos `docs/MODELO-DADOS.md`, `contracts/openapi.yaml` e `docs/SEGURANCA.md`; atualize os diagramas afetados.
- **Validação:** alterações documentais recebem validação de links, contrato e coerência; limites de testes futuros estão na spec local e no prompt, sem testes artificiais.
- **Git:** confira destino e branch antes de envio; nunca use o remoto das skills como remoto do aplicativo. Capture a base, faça commit local, revise o conjunto e só então envie, sem deploy automático.
- **Fechamento:** registre evidências, bloqueios e próxima ação em `docs/STATUS.md`. Preserve dados pessoais e segredos fora de Git e logs.
