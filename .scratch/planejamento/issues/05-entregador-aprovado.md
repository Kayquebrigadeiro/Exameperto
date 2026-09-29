# 05: Entregador envia comprovações e recebe revisão

**What to build:** Entregador usa app para enviar cadastro/documentos privados e acompanha decisão; analista atribuído revisa no painel.

**Blocked by:** 02 — Definir privacidade, acesso privilegiado e provedores, 03 — Cadastrar, autenticar e recuperar conta

**Status:** ready-for-human

- [ ] Upload privado com quarentena, limite/tipo real, objeto gerado e download autorizado funciona do app ao armazenamento real habilitado.
- [ ] Persistência, API e telas distinguem cadastro, inspeção do arquivo e aprovação profissional; falta de provedor não aprova automaticamente.
- [ ] Analista atribuído revisa sem autoaprovação; outras contas não obtêm documentos por ID/URL; foto operacional aprovada é projeção separada.
- [ ] Troca de foto/documento cria revisão pendente antes de nova oferta; histórico anterior permanece auditável.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.
