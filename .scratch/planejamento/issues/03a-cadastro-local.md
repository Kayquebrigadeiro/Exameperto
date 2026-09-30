# 03A: Cadastro local com bloqueio verificável

**What to build:** Primeira fatia web → REST → PostgreSQL isolado: formulário básico de cadastro, validação segura e indisponibilidade explícita quando não houver e-mail habilitado, sem criar conta parcial ou fingir verificação. Preparar apenas o ambiente necessário a essa fatia. É um incremento verificável do marco local, não o cadastro completo do ticket 03.

**Blocked by:** Nenhum ticket ou dependência externa. Aguarda somente autorização explícita de implementação desta fatia; esta rodada é documental.

**Status:** ready-for-human

- [ ] Java 21/Spring Security, PostgreSQL/Flyway e React/TypeScript/Vite em ambiente local reproduzível; versões fixadas, instruções de execução e sem secrets/seeds/contas privilegiadas.
- [ ] Formulário de nome/e-mail/senha chama POST /auth/register; validação de entradas e campos extras por REST, rejeitando role/status e sem promoção de privilégios.
- [ ] Sem provedor habilitado retorna 503 INTEGRATION_UNAVAILABLE para entrada válida, inclusive conta já existente; UI explica indisponibilidade sem afirmar envio, cadastro concluído ou login disponível.
- [ ] Em ambiente isolado com configuração de privacidade de teste, verificar via REST e PostgreSQL real que falha de integração não cria usuário, desafio, sessão ou envio na outbox. Sem política para dados reais, negar com 422 POLICY_UNDEFINED antes de persistir.
- [ ] Limitação de tentativas, origem web permitida e erros sem senha/e-mail/tokens nos logs. Nenhuma rota privilegiada ou integração simulada habilitada; rotas protegidas negam acesso anônimo.
- [ ] Testes de comportamento usam dados sintéticos apenas em banco isolado/descartável. Banco da aplicação inicia vazio; não solicitar dados pessoais reais para demonstração. Verificação manual da UI limita-se à validação e mensagem de bloqueio.
- [ ] Build e testes locais documentados, limitações registradas e mudanças revisadas. Sem implantação, programa, unidade, pagamentos, biometria ou compras.

Critérios de aceite não executados nesta rodada. O sucesso completo de cadastro, e-mail, autenticação e recuperação permanece no ticket 03; não expor endpoint alternativo que marque e-mail como verificado nem imprimir token para contornar provedor. A proposta de limite de teste permanece REST/autorização já acordada; PostgreSQL isolado verifica a ausência de efeitos na falha, sem teste de detalhes privados.
