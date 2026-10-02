# Conta e e-mail — ticket 03

Implementação autorizada sobre `604487b9040d78aff89a1e68c2edc6f079a39a89`, mantendo os bloqueios de 03A. Nenhum provedor SMTP estava configurado no ambiente inspecionado. O adaptador real usa Spring Mail/SMTP; **integração ainda não homologada**. Não houve contratação, envio a endereço real ou deploy.

## Configuração necessária

Configure no ambiente do processo, gerenciador de segredos ou arquivo privado fora do Git. Não envie os valores pelo chat.

| Variável | Conteúdo necessário |
|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | PostgreSQL autorizado; URL JDBC, usuário e senha |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | Servidor e porta fornecidos pelo provedor escolhido |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | Credencial SMTP autorizada pelo provedor |
| `EXAME_PERTO_EMAIL_FROM` | Remetente autorizado/verificado pelo provedor |
| `EXAME_PERTO_EMAIL_ENABLED` | `true` somente após configuração e autorização de envio |
| `EXAME_PERTO_DATA_KEY` | 32 bytes aleatórios, Base64; cifra AES-256-GCM dos contatos e nomes |
| `EXAME_PERTO_SEARCH_KEY` | Outros 32 bytes aleatórios, Base64; HMAC de busca e CSRF |
| `EXAME_PERTO_SIGNING_KEY` | Outros 32 bytes aleatórios, Base64; assinatura HS256 dos access tokens |
| `REGISTRATION_PRIVACY_APPROVED` | `true` apenas com responsáveis/retenção pertinentes validados para dados reais |
| `REGISTRATION_ALLOWED_ORIGIN` | Origem exata da web; padrão `http://localhost:5173` |

As três chaves devem ser independentes, estáveis entre reinícios e guardadas com backup restrito. Não trocar a chave de cifra/busca sem migração: os dados existentes ficariam ilegíveis/inacessíveis. O envelope da cifra identifica a versão `1`; rotação operacional de chaves ainda depende de procedimento de migração. Não há chaves padrão na aplicação.

STARTTLS é habilitado e obrigatório, com verificação do nome do servidor e timeouts de conexão/leitura/escrita de 5 segundos. Para provedores com TLS implícito, configure `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true` e ajuste STARTTLS conforme o serviço; não desative validação de certificado. Debug SMTP e logging de corpos/credenciais devem permanecer desligados. Referência: [Spring Boot — envio de e-mail](https://docs.spring.io/spring-boot/3.5/reference/io/email.html).

Cookie web: HttpOnly, Secure, SameSite=Lax, Path `/api/v1/auth`. Desenvolvimento usa localhost/loopback (exceção de contexto seguro do Chromium, verificada no ensaio); uso fora do loopback exige HTTPS e origem autorizada. O proxy Vite mantém a origem da web. A aplicação ignora `X-Forwarded-For`; implantação atrás de proxy exige desenho posterior de confiança/rate limit.

## Comportamento e limites

- Cadastro: política → integração/chaves → transação com serialização por e-mail → usuário pendente/desafio → tentativa SMTP → commit. Falha de envio retorna 503 e rollback, sem conta/desafio/outbox parcial. Conta existente recebe resposta genérica sem substituir senha nem enviar novamente; reenvio é operação separada.
- Confirmação: 32 bytes aleatórios (256 bits), somente SHA-256 persistido, prazo padrão 30 minutos, consumo condicional atômico. E-mail confirmado **não** significa identidade, benefício, entregador ou veículo aprovados.
- Reenvio/recuperação: 202 `PENDENTE` significa solicitação recebida, jamais entrega. Conta ausente/inapta tem a mesma resposta. Sem configuração, 503 para qualquer endereço; sem política, 422. Conta apta enfileira somente seu UUID e o tipo, com intervalo mínimo de um minuto e uma chave por conta/tipo. Reenvio invalida o desafio anterior quando processado.
- Worker transacional: seleciona trabalho pendente e bloqueia conta, gera token em memória, persiste hash e tenta SMTP. `ENVIADO` significa aceitação pelo transporte SMTP, **não recebimento na caixa de entrada**. Falha gera `RECONCILIAR`, incrementa tentativas e invalida o novo token; um novo pedido, após o intervalo, permite nova tentativa. Não há retry automático infinito. Timeout ou queda entre SMTP e commit pode produzir mensagem com código inválido: não existe atomicidade banco/SMTP, e um novo pedido é necessário. Nenhum erro bruto do provedor é registrado.
- Observação operacional, por acesso restrito ao banco: `SELECT tipo,estado,count(*) FROM outbox WHERE tipo IN ('EMAIL','RECUPERACAO') GROUP BY tipo,estado;`. Nenhum destinatário/token deve ser incluído na evidência. Alertas de worker usam apenas códigos `ACCOUNT_MAIL_QUEUE_UNAVAILABLE` / `ACCOUNT_MAIL_TRANSACTION_FAILED`. Monitoramento operacional e tratamento de bounces dependem de implantação/provedor.
- Login: Argon2id, parâmetros da biblioteca Spring Security 5.8 (salt 16 bytes, hash 32, paralelismo 1, memória 16 MiB, duas iterações). Conta pendente/bloqueada não autentica; conta inexistente também verifica hash fictício apenas para equalizar custo. Não há criação de papéis administrativos.
- Access: JWT HS256, emissor `exame-perto`, audiência `exame-perto-api`, validade padrão 15 minutos; assinatura e estado/expiração/revogação da sessão no PostgreSQL conferidos a cada pedido. Não contém e-mail, nome ou permissões. Access web só em memória.
- Refresh: 256 bits, hash no banco, validade absoluta da família de 30 dias; rotação não estende esse limite. Login/refresh/logout/reset usam lock da conta; reutilização revoga toda a família e esse efeito é commitado mesmo com resposta 401. Logout revoga a família, inclusive sucessor concorrente. Troca de senha revoga todas as sessões e demais desafios de recuperação da conta.
- CSRF: login WEB exige Origin exato. `GET /auth/csrf` (ou POST equivalente para web same-origin) retorna HMAC associado ao refresh HttpOnly após conferir Origin; refresh/logout WEB exigem esse token em `X-CSRF-Token` e Origin. Valor inventado ou anterior à rotação não serve. MOBILE não aceita cookies nem Origin, e não pode renovar refresh WEB pelo corpo. API não usa sessão HTTP implícita.
- Limites: 5 pedidos/minuto por IP/rota, 100 globais/minuto e 5 tentativas de login por chave de conta/minuto por processo; caches limitados a 10 mil entradas e sem endereço em claro. Cadastro conserva seu limite próprio de 03A. Os limites em memória reiniciam com o processo e não coordenam réplicas; implantação distribuída permanece fora desta fatia. Reenvio tem limite persistido adicional.

Os prazos acima são parâmetros técnicos, não prazos legais de retenção. Política de retenção/expurgo e fatores MFA para privilégios continuam pendentes. Nenhuma rota privilegiada foi habilitada; rotas não implementadas são negadas por padrão.

## Verificação reproduzível

```sh
./scripts/verify-ticket-03.sh
```

Java 21, Maven, Docker, Node.js 22.12 ou superior, npm e Chromium do Playwright são pré-requisitos. Instale o navegador com `cd web` e `npx playwright install chromium` se necessário. O script instala exatamente as dependências do lockfile, executa a suíte Maven, build web, os dois testes Playwright de UI, o ensaio integrado e empacota a aplicação. Ao final, confere que classes e identificadores do capturador de teste não entraram no JAR. Ele remove do próprio processo as variáveis SMTP antes de testar; a única conexão SMTP exercitada aponta para `127.0.0.1:1` e comprova indisponibilidade sem envio externo.

### Dois níveis opt-in do ensaio integrado

`BrowserFlowTest` é o teste Maven opt-in, ignorado na suíte comum pela condição `@EnabledIfSystemProperty`. Sua finalidade é orquestrar a evidência navegador → backend → PostgreSQL: cria PostgreSQL 17.6 descartável com Testcontainers, inicia o Spring Boot em porta aleatória, disponibiliza uma fronteira de e-mail exclusiva de teste e chama o processo Playwright. Dependências adicionais: Docker acessível, dependências de `web` instaladas, Chromium e porta loopback 5187 livre. Execução isolada:

```sh
mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 \
  -Dtest=BrowserFlowTest -DbrowserTest=true test
```

`web/tests/account-real.spec.ts` é o Playwright opt-in chamado pelo teste Maven. Ele se ignora quando `TEST_MAILBOX` não existe. Quando chamado pelo orquestrador, recebe `API_TARGET` apontando para o backend real e `TEST_MAILBOX` apontando para o diretório temporário. O Vite somente encaminha `/api`; não há interceptação da API. Rodar esse spec diretamente não cria backend, PostgreSQL ou caixa de captura e, por isso, não substitui o comando Maven acima.

O capturador é um `@MockBean EmailGateway` compilado somente em `backend/src/test`. Ele extrai os códigos das chamadas internas do gateway e grava arquivos `confirmation` e `recovery` no diretório temporário; o Playwright apenas os lê para preencher a tela. O teste apaga arquivos e diretório no fechamento. `TEST_MAILBOX`, nomes dos arquivos e código do mock não existem em `backend/src/main` ou `web/src`; nenhum endpoint auxiliar expõe token ou caixa. Na configuração normal, existe somente `MailEmailGateway`, desabilitado por padrão, e sem SMTP configurado os fluxos permanecem indisponíveis. Assim o ensaio comprova o fluxo da aplicação, mas não entrega de e-mail.

Os demais testes Maven usam PostgreSQL descartável e dados sintéticos. `SmtpFailureTest` usa o adaptador JavaMail real contra a porta local indisponível. `PersistenceRestartTest` encerra e reinicia a aplicação contra o mesmo banco isolado e autentica a conta persistida.

O teste de UI original intercepta o POST somente para verificar apresentação do erro; seu resultado não é evidência de integração. O par `BrowserFlowTest` + `account-real.spec.ts` forma a evidência navegador → HTTP → PostgreSQL. Nenhum deles comprova entrega real de e-mail. Logs/resultados gerados ficam em `backend/target` e `web/test-results`, ignorados pelo Git.

## Homologação de e-mail pendente

Antes de qualquer envio real: configurar provedor/remetente/chaves/política e registrar autorização específica do autor, incluindo endereço sob seu controle e escopo dos envios (cadastro, reenvio, recuperação). O endereço e credenciais ficam somente no ambiente privado. A autorização de implementar não foi tratada como autorização para enviar mensagens reais.

No ensaio autorizado, conferir recebimento na caixa controlada (inclusive spam), remetente, conteúdo, prazo, confirmação única, recuperação e inutilização das sessões antigas. Registrar data, provedor, resultado e referência não sensível da evidência; não publicar endereço, cabeçalhos privados, token ou captura contendo dados pessoais. Aceitação SMTP e recebimento devem ser registrados separadamente. Falha, timeout ou bounce não permite declarar homologação. Até esse ensaio: **SMTP implementado, entrega real não homologada**.
