# 06: Entregador comprova vínculo com veículo

**What to build:** Entregador cadastra veículo próprio, alugado ou autorizado e recebe decisão rastreável do analista.

**Blocked by:** 05 — Entregador envia comprovações e recebe revisão

**Status:** implemented-partially

- [x] App, REST, persistência e painel cobrem placa, modelo, cor, anos, CRLV, fotos e prova do vínculo.
- [x] Veículo não precisa pertencer ao entregador; ausência de autorização/evidência impede aprovação.
- [x] Novo vínculo/revisão não herda estado aprovado indevidamente; entregador só consulta/submete os próprios vínculos.
- [ ] Testes de autorização e revisão observam API pública, inclusive tentativa de reutilizar documento de outro entregador.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D01/D10/D11: reaproveitar evidência válida sem cópias desnecessárias, preservar critérios profissionais pendentes e MFA; comprovação de vínculo não substitui situação atual oficial.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Implementação autorizada — 05/10/2026

Implementados backend/API, migração V6, painel web e app mobile para placa, marca/modelo, cor, anos, CRLV, fotos e evidência de vínculo. Tipos próprio, alugado e autorizado não presumem propriedade. Documentos usam armazenamento/quarentena existentes, referência privada por UUID, proxy de download autorizado e inspeção estrutural explicitamente limitada. Versão otimista retorna `412`; substituições preservam histórico, bloqueiam revisão em curso e criam novo snapshot. Fila de veículo exige papel AO, atribuição, MFA e impede autoanálise; decisão continua `422 POLICY_UNDEFINED`.

Testes públicos foram adicionados para acesso entre contas, reutilização de documento de terceiro, campos privilegiados, concorrência, substituição, MFA, sessão revogada e revisão desatualizada. A execução Testcontainers não iniciou neste ambiente por ausência de Docker; typecheck/build web, typecheck/export Android e compilação Maven passaram. O teste em aparelho/emulador permanece pendente conforme ticket 05.

Bloqueios mantidos: aprovação profissional e liberação de ofertas continuam proibidas; não há consulta oficial simulada. Dependências mobile continuam com alertas documentados (16 altos/7 moderados no relatório final), e a instalação/uso ponta a ponta em dispositivo não foi alegada. Não iniciar ticket 07, distribuir ou fazer deploy.
