# 01: Catálogos autorizados de política e unidade

**What to build:** contratos e consultas por registro para que paciente/familiar selecione política institucional e unidade de retirada habilitadas sem digitar UUID. Incluir estados vazios e isolamento institucional.

**Blocked by:** decisão de quem cadastra/habilita unidades e políticas; atualização conjunta de modelo, OpenAPI e segurança.

**Status:** ready-for-human

- [ ] Benefício lista somente políticas ativas aplicáveis ao paciente e informa instituição/versão.
- [ ] Pedido lista somente unidades habilitadas com protocolo vigente.
- [ ] UUID interno não é exigido quando há opção selecionável.
- [ ] Ausência de catálogo bloqueia somente a etapa dependente, com motivo observável.
