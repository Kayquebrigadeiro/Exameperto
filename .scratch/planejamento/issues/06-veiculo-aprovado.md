# 06: Entregador comprova vínculo com veículo

**What to build:** Entregador cadastra veículo próprio, alugado ou autorizado e recebe decisão rastreável do analista.

**Blocked by:** 05 — Entregador envia comprovações e recebe revisão

**Status:** ready-for-human

- [ ] App, REST, persistência e painel cobrem placa, modelo, cor, anos, CRLV, fotos e prova do vínculo.
- [ ] Veículo não precisa pertencer ao entregador; ausência de autorização/evidência impede aprovação.
- [ ] Novo vínculo/revisão não herda estado aprovado indevidamente; entregador só consulta/submete os próprios vínculos.
- [ ] Testes de autorização e revisão observam API pública, inclusive tentativa de reutilizar documento de outro entregador.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D01/D10/D11: reaproveitar evidência válida sem cópias desnecessárias, preservar critérios profissionais pendentes e MFA; comprovação de vínculo não substitui situação atual oficial.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
