# 05: Entregador envia comprovações e recebe revisão

**What to build:** Entregador usa app para enviar cadastro/documentos privados e acompanha decisão; analista atribuído revisa no painel.

**Blocked by:** 02 — Definir privacidade, acesso privilegiado e provedores, 03 — Cadastrar, autenticar e recuperar conta

**Status:** parcialmente implementado — inspeção profissional, MFA e critérios oficiais bloqueiam conclusão

- [x] Upload privado com quarentena, limite/tipo real, objeto gerado e download autorizado funciona do app ao armazenamento real isolado local.
- [x] Persistência, API e telas distinguem cadastro, inspeção do arquivo e aprovação profissional; falta de provedor não aprova automaticamente.
- [ ] Analista atribuído revisa sem autoaprovação; outras contas não obtêm documentos por ID/URL; foto operacional aprovada é projeção separada.
- [x] Troca de foto/documento cria revisão pendente antes de nova oferta; histórico anterior permanece auditável.

Implementação parcial autorizada sobre a base `966c0b2`; conclusão depende de armazenamento/inspeção oficial, critérios profissionais e MFA verificável.

## Ajuste D01–D12 — 30/09/2026

D01/D09–D11: revisão humana com evidência mínima, critérios profissionais validados, MFA e segregação. Download/foto via proxy com reautorização/no-store; revogação bloqueia novos acessos. Biometria adiada, integração oficial indisponível não se torna aprovação.

- [x] Verificar os comportamentos e bloqueios independentes acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Comments

02/10/2026 — Implementado cadastro do entregador em `RASCUNHO`, API multipart autenticada, migração V4, armazenamento privado local configurável, quarentena, nomes gerados, validação de assinatura PDF/JPEG/PNG, limite de 10 MiB, hash, histórico de substituição, proxy `no-store` e painel web; app Expo adicionado para cadastro/upload/acompanhamento.

O ensaio PostgreSQL 17.6/Testcontainers + HTTP real + armazenamento local isolado usou apenas documentos sintéticos e verificou quarentena, download bloqueado antes de inspeção, download aprovado por proxy, acesso indevido, tipo inválido, excesso, substituição e fila de revisão. O teste de armazenamento real escreve/lê/remove o objeto em diretório temporário. Não houve emulador Expo nem aparelho físico.

Bloqueio de conclusão: não há mecanismo de inspeção oficial, critérios profissionais publicados, integração externa de armazenamento ou verificador MFA habilitado. A API mantém qualquer arquivo não inspecionado bloqueado e retorna `403 MFA_REQUIRED`/`503 POLICY_UNDEFINED` para ações privilegiadas; não cria aprovação fictícia, conta administrativa padrão ou revisor fictício. Não apresentar esta análise como consulta oficial. Não iniciar ticket 06 nem deploy.
