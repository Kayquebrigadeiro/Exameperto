# 03: Cadastrar, autenticar e recuperar conta

**What to build:** Permitir que uma pessoa use a web para criar conta real, confirmar e-mail, entrar, sair e recuperar acesso.

**Blocked by:** 02 — Definir privacidade, acesso privilegiado e provedores

**Status:** ready-for-human

- [ ] Fatias incluem persistência/migração, REST e tela; fixar versões da stack e ambiente local reproduzível neste primeiro fluxo.
- [ ] Cadastro não aceita promoção de papel; e-mail/provedor ausente retorna indisponível e UI explica o bloqueio.
- [ ] Rotação/reutilização de refresh, logout e recuperação revogam sessões conforme contrato; CSRF/Origin e rate limit verificados pela interface pública.
- [ ] Dados sintéticos só em testes isolados; reinício preserva conta em ambiente de desenvolvimento autorizado, sem seeds predefinidos.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.
