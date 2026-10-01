# 03A: Cadastro local com bloqueio verificável

**What to build:** Primeira fatia web → REST → PostgreSQL isolado: formulário básico de cadastro, validação segura e indisponibilidade explícita quando não houver e-mail habilitado, sem criar conta parcial ou fingir verificação. Preparar apenas o ambiente necessário a essa fatia. É um incremento verificável do marco local, não o cadastro completo do ticket 03.

**Blocked by:** Nenhum ticket ou dependência externa. Aguarda somente autorização explícita de implementação desta fatia; esta rodada é documental.

**Status:** concluído em `03A` — implementação local; operação real continua bloqueada sem e-mail.

- [x] Java 21/Spring Security, PostgreSQL/Flyway e React/TypeScript/Vite em ambiente local reproduzível; versões fixadas, instruções de execução e sem secrets/seeds/contas privilegiadas.
- [x] Formulário de nome/e-mail/senha chama POST /auth/register; validação de entradas e campos extras por REST, rejeitando role/status e sem promoção de privilégios.
- [x] Sem provedor habilitado retorna 503 INTEGRATION_UNAVAILABLE para entrada válida, inclusive conta já existente; UI explica indisponibilidade sem afirmar envio, cadastro concluído ou login disponível.
- [x] Em ambiente isolado com configuração de privacidade de teste, verificado via REST e PostgreSQL real que falha de integração não cria usuário, desafio, sessão ou envio na outbox. Sem política para dados reais, serviço nega com 422 POLICY_UNDEFINED antes de persistir.
- [x] Limitação de tentativas, origem web permitida e erros sem senha/e-mail/tokens nos logs. Nenhuma rota privilegiada ou integração simulada habilitada; rotas protegidas negam acesso anônimo.
- [x] Testes de comportamento usam dados sintéticos apenas em banco isolado/descartável. Banco da aplicação inicia vazio; não solicitar dados pessoais reais para demonstração. Verificação da UI cobre apresentação da indisponibilidade e acessibilidade; a integração navegador→backend real e a validação visual ficam para o próximo ensaio.
- [x] Build e testes locais documentados, limitações registradas e mudanças revisadas. Sem implantação, programa, unidade, pagamentos, biometria ou compras.

Os critérios desta fatia foram executados. O sucesso completo de cadastro, e-mail, autenticação e recuperação permanece no ticket 03; não expor endpoint alternativo que marque e-mail como verificado nem imprimir token para contornar provedor. A proposta de limite de teste permanece REST/autorização já acordada; PostgreSQL isolado verifica a ausência de efeitos na falha, sem teste de detalhes privados.

## Comments

30/09/2026 — Implementada a fronteira local. `POST /api/v1/auth/register` rejeita JSON com propriedades desconhecidas, valida DTO no servidor, aplica limite de tentativas e bloqueia antes de qualquer persistência quando a política de privacidade ou o adaptador de e-mail não estiver habilitado. A API não possui caminho de sucesso nesta fatia: confirmação de e-mail e criação de conta pertencem ao ticket 03. Formulário React acessível, com feedback seguro e teste Axe; o Playwright usa interceptação controlada para verificar a apresentação do erro, não comprova a comunicação com backend real. Testes REST + PostgreSQL/Testcontainers passaram com dados sintéticos. O serviço controlado de teste não foi usado para simular entrega.
