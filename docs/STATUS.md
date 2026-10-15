# Status — atualizado em 14/10/2026

## Análise de benefícios e operação de privacidade — 15/10/2026

As telas de `/app/analise-beneficios` e `/app/operacao-privacidade` foram
separadas em fila e detalhe, sem UUID digitado. A análise de benefícios agora
mostra busca, filtro, paginação, dimensões independentes, evidências
autorizadas, histórico, pendências, política/critério retornados pelo
backend, solicitação de complemento, decisão e recurso. O complemento bloqueia
a revisão atual com a justificativa informada e o upload autorizado do titular
reabre uma revisão; nenhum percentual ou critério foi criado na interface.

Privacidade exibe responsável, histórico e os marcos distintos de resposta,
autorização, execução e verificação, além de conservação, pendências e
bloqueios. Resposta não é apresentada como exclusão; transformação parcial
não é apresentada como anonimização integral. As mutações continuam exigindo
papel, MFA, escopo institucional e impedimento de autoanálise no backend.

Verificações: build web e compilação Java passaram; `BenefitFlowTest` passou
1/1 e `PrivacyFlowTest` passou 4/4, ambos com PostgreSQL 17.6 descartável.
No Chromium/Spring/PostgreSQL real, sem interceptação, a jornada passou 1/1.
Foi confirmado por HTTP que a fila e o detalhe retornam 200, a atribuição,
inspeção e decisão retornam 200 e os GETs seguintes atualizam a tela; o banco
descartável confirmou a atribuição e a decisão. As recusas de autoanálise e
ausência de MFA também foram exercitadas. A causa do timeout original foi um
seletor do teste (`Motivo`) que não correspondia ao rótulo vigente
`Justificativa`; uma tentativa intermediária também encontrou Vite órfão em
`5187`, que foi limpo sem tocar no ambiente normal. O teste agora afirma
`ATRIBUIDA` após atribuição e inspeção. Capturas sem dados pessoais:
`/tmp/exameperto-analise-beneficios.png` e
`/tmp/exameperto-operacao-privacidade.png`. Não houve escrita no banco normal
nem deploy. Próxima ação: revisão e push do commit corretivo.

## Experiência da área operacional — 14/10/2026

`/app/operacao` foi separado em uma entrada operacional com abas de
`Entregadores` e `Veículos`, MFA em uma única área de segurança, fila com
filtro de situação, busca, paginação e detalhe independente. O detalhe não
expõe UUIDs: mostra a situação, evidência autorizada, estado do documento,
pendência e histórico; as ações de atribuição, inspeção e decisão continuam
chamando as APIs existentes, com permissões, quarentena, autoanálise e
política profissional decididas pelo backend.

O retorno de revisão passou a incluir somente metadados necessários à tela
(categoria, MIME, tamanho, estado do documento e data de criação). Não houve
alteração de regra de aprovação, criação de parceiro, habilitação automática
ou escrita no banco normal. A decisão profissional segue bloqueada quando a
política aplicável está indefinida; a interface informa que inspeção técnica
não equivale à aprovação.

Também foi corrigido o aviso React no `OrderPanel`: cada resumo de pedido
agora usa `order.id` como chave estável. O segredo TOTP deixa de permanecer
visível depois que a sessão é elevada.

Validação final pelo navegador, sem interceptação, com Spring e PostgreSQL
17.6 em Testcontainers descartável: MFA e atribuição nominal, inspeção de
evidência, recusa de autoanálise, recusa sem MFA, benefícios e privacidade.
Resultado: 1 teste aprovado em 15,0 s; o log não registrou o aviso de `key`.
O primeiro ensaio falhou apenas porque o fixture compartilhado selecionava o
primeiro registro (já atribuído por outro analista); o seletor foi corrigido
para o registro sintético correspondente e a repetição passou.

Capturas sintéticas, sem dados pessoais: `/tmp/exameperto-operacao-entregador.png`
e `/tmp/exameperto-operacao-veiculos.png`. Elas representam uma sessão de
teste elevada, não login exercitado da conta real. URLs: `/app/operacao`, com
as abas `Entregadores` e `Veículos`.

## Validação final da jornada do entregador — 14/10/2026

Partindo de `83e8ab5`, os testes de designação e custódia foram atualizados
para a interface atual: navegam por `/app/entregas`, usam as abas de ofertas e
entrega atribuída, abrem pedidos pelo detalhe e não dependem de UUID visível.
Os fixtures agora respeitam a FK de unidade da V19 e têm ordem temporal
determinística apenas no banco descartável.

O fluxo Chromium completo passou com Spring e PostgreSQL 17.6 descartável:
duas ofertas elegíveis foram aceitas pela interface, viraram entregas
atribuídas, uma seguiu retirada → custódia → início → recebimento → encerramento;
a outra seguiu retirada → ocorrência e permaneceu aberta. O destinatário emitiu
o código pela interface e o entregador o confirmou. Um terceiro entregador não
teve acesso ao código da entrega (`404`); o histórico de ocorrência permaneceu
visível ao destinatário. Nenhuma API foi interceptada.

A investigação encontrou uma regressão real: a API retornava o código de
recebimento, mas `/app/pedidos` mostrava somente a expiração. A tela agora
mostra o código apenas na sessão do destinatário. As demais falhas eram
seletores antigos, nome de região anterior e fixtures sem a unidade V19 ou com
ordenação não determinística.

Validação final: `AssignmentBrowserFlowTest` passou; `CustodyBrowserFlowTest`
passou após exercitar oferta → aceite → custódia → ocorrência/recebimento;
`DelivererBrowserFlowTest` e testes de evidência/veículo permanecem isolados e
sem dados pessoais. O teste ainda registra um aviso React de `key` no painel de
pedidos, sem falha funcional. O ambiente normal não recebeu registros.

## Experiência do entregador — 14/10/2026

`/app/entregador` foi reorganizado em visão geral, cadastro/documentos e
veículos/vínculos. A visão geral mostra separadamente acesso à área, cadastro
enviado, documentos analisados e habilitação para ofertas, além dos próximos
passos. O envio e a substituição continuam entrando em quarentena/análise; a
interface não aprova documentos nem libera ofertas automaticamente. A API de
documentos passou a devolver o motivo da última revisão quando existente, e a
interface orienta a substituição de um documento rejeitado.

`/app/entregas` separa ofertas, entrega atribuída e histórico. O detalhe mantém
as transições existentes de retirada, custódia, ocorrências e recebimento, com
versão, idempotência e permissões decididas pelo backend. Falha/bloqueio de
`/offers` não impede carregar atribuições, histórico, veículos e documentos
permitidos; não há UUID digitado nem oferta fictícia.

Validação: `DelivererBrowserFlowTest` em Chromium → Spring → PostgreSQL 17.6
descartável cobriu cadastro, upload, substituição e navegação; `DelivererEvidenceFlowTest`
e `VehicleLinkFlowTest` cobriram persistência, análise e migração V19; o build
web passou. Evidências visuais isoladas: `/tmp/exameperto-entregador.png` e
`/tmp/exameperto-entregas.png`, sem dados pessoais. O ambiente normal não
recebeu registros sintéticos.

Limitação operacional: ofertas permanecem bloqueadas até o backend confirmar
entregador aprovado, veículo vigente/aprovado, política ativa e demais
requisitos de cobertura/evidência. Conta autenticada sem cadastro pode acessar
a área, mas precisa completar as etapas; isso não equivale a estar habilitada.

## Finalização do catálogo e retomada de pedidos — 14/10/2026

O formulário de protocolo em `/app/catalogo` não expõe mais `regras` JSON.
Ele usa número, instruções de retirada, evidência de entrega, instruções de
retorno, indicação de retorno obrigatório, vigência e confirmação de cobertura.
O backend valida os campos e monta o JSON persistido em `protocolo_custodia`.
Cada unidade mostra separadamente cadastro, parceria, ativação, protocolo e a
lista de requisitos ainda pendentes para entrar no seletor do paciente.

A retomada da jornada de pedidos **não é persistência de domínio**: o código
atual salva o rascunho em `sessionStorage` do navegador (`exameperto-order-draft`).
Ele sobrevive a reload e navegação na mesma sessão/origem do navegador, mas não
é enviado ao backend, não aparece em `/orders`, não é compartilhado entre
dispositivos e pode ser removido ao limpar os dados da sessão. Só há pedido
quando a API de criação responde com sucesso; não há endpoint de rascunho
persistentemente retomável nesta etapa.

O teste isolado de navegador agora abre o catálogo via sessão sintética com
papel operacional, verifica o formulário sem JSON e depois verifica o catálogo
vazio e o bloqueio da jornada de pedidos no mesmo backend/PostgreSQL
descartável. Nenhuma unidade sintética foi criada no banco normal.

## Orçamento e detalhe da jornada de pedidos — 14/10/2026

Partindo de `af6ee0c`, `/app/pedidos` agora apresenta uma revisão antes da
criação do pedido e deixa explícito que a cotação só é solicitada depois que o
pedido persistido e a verificação da unidade permitem essa transição. O detalhe
mostra situação, próximo passo, histórico formado apenas por eventos retornados
pelas APIs, endereço protegido e composição da cotação: frete, desconto/subsídio
e total do paciente. Valores, rota, tarifa, política, cobertura e pagamento não
são inventados nem tratados como confirmados fora das respostas do backend.

O detalhe permite alterar endereços enquanto o domínio ainda permite essa
transição. A API `PUT /api/v1/orders/{id}/addresses` usa a versão do pedido,
substitui propostas/aceites afetados e retorna o pedido a `EM_VERIFICACAO`; a
interface preserva os campos em caso de erro e orienta a solicitar novo
orçamento. Expiração e aceite continuam sendo decididos pelo backend; aceite
fica bloqueado sem política de cancelamento ou após `expira_em`. Indisponibilidade
de rota, tarifa, cobertura ou pagamento aparece como bloqueio específico e não
cria pedido, preço, saldo ou confirmação falsos.

O rascunho é explicitamente local ao `sessionStorage` desta origem, não é
persistência de domínio, não aparece no backend e não é compartilhado entre
contas. Há ação `Descartar rascunho`; a chave é removida após criação confirmada
do pedido e no logout para impedir exposição no mesmo navegador. O reload foi
testado no navegador isolado: a etapa e os campos retornam; o descarte remove a
chave. Não existe endpoint de rascunho persistente nesta etapa.

Validação: build web, compilação backend, `OrderBrowserFlowTest` (Chromium →
Spring → PostgreSQL 17.6 descartável) e `QuoteAcceptanceFlowTest` (rota, tarifa,
expiração, versões, aceite, pagamento ausente, cobertura sintética e troca de
endereço) passaram após o fixture criar unidade/parceria/protocolo apenas no
banco de teste. O banco normal permanece sem unidades sintéticas e a conta/dados
locais foram preservados. A validação visual manual deve usar
`http://127.0.0.1:5173/app/pedidos` após login real; não foi feito deploy.

## Catálogo administrativo de unidades e protocolos — 14/10/2026

Partindo do commit `219da88`, foi implementado o catálogo operacional em
`/app/catalogo`, disponível somente para contas com papel global
`ANALISTA_OPERACIONAL`. A listagem é protegida no backend; mutações exigem
também MFA verificado na sessão. O fluxo separa cadastro da unidade, aceite
de parceria com referência de comprovação e habilitação do protocolo. Cada
transição grava `auditoria_unidade_retirada`; cadastrar uma unidade deixa o
registro `CADASTRADA` e o protocolo `SUSPENSO`, sem presumir hospital,
parceiro ou autorização institucional.

A migração V19 criou `unidade_retirada`, `aceite_unidade_retirada` e a
auditoria, vinculando protocolos ao catálogo sem reescrever pedidos existentes.
`GET /api/v1/order-options/units` só retorna unidade ATIVA com parceria ACEITA,
protocolo HABILITADO e cobertura de retorno vigente. A mesma regra é
revalidada na criação do pedido. O rascunho atual continua no navegador,
preserva campos quando o catálogo está vazio e não é confundido com pedido
persistido, orçamento ou aprovação.

Configuração local: com o backend e PostgreSQL ativos, um administrador
operacional acessa `http://127.0.0.1:5173/app/catalogo`, configura MFA, cadastra
a unidade com dados institucionais reais, registra a referência da
comprovação, ativa a unidade, cria o protocolo com regras JSON e marca a
cobertura de retorno somente quando confirmada. Depois habilita o protocolo;
somente então ele aparece em `/app/pedidos`. A unidade sintética não foi
inserida no banco normal.

Evidências: Flyway V19 aplicado no PostgreSQL local; `scripts/verify-local-environment.sh`
passou após reiniciar os serviços locais; build backend/web passou;
`OrderBrowserFlowTest` passou com navegador, Spring e PostgreSQL descartável,
confirmando que catálogo vazio bloqueia apenas a transição operacional e não
persiste pedido/autorização. O processo servido é o atual em
`127.0.0.1:5173` → `127.0.0.1:8080` → PostgreSQL `5433`.

## Painel do paciente e jornada de entrega — 14/10/2026

`/app` mantém o painel autenticado separado da home e de `/acesso`, com
saudação, próximo passo, pedidos recentes, estados vazios reais e acessos a
perfil, família e benefícios. `/app/pedidos` foi reconstruído como jornada em
seis etapas (paciente, unidade/retirada, destinatário, endereço, documento e
revisão), com seletores para paciente/destinatário autorizados, detalhe de
pedido, estados compreensíveis, orçamento/cobertura e acompanhamento ligados
às APIs existentes. UUIDs não são mais digitados ou exibidos na interface.

O rascunho da jornada fica em `sessionStorage` para retomada neste navegador;
isso não é tratado como persistência do domínio. O pedido só é declarado
criado após resposta de criação e recarga da lista.

Bloqueio confirmado no ambiente normal: não há endpoint/catálogo de unidades
de retirada, e não existe unidade/protocolo configurado que possa ser
selecionado. O backend exige `pickupUnitId` real; depois da criação exige
autorização da unidade, provedor de rota, tarifa, política e cobertura válidos
para orçamento. A UI mostra o requisito exato, preserva o preenchimento e
mantém o envio desabilitado; não inventa parceiro, preço ou aprovação.

Evidências: `npm run build` em `web` passou; `tests/registration.spec.ts`
passou 6/6; `git diff --check` passou; o marcador servido por
`127.0.0.1:5173` contém a versão nova. A regressão
`OrderBrowserFlowTest` passou com navegador, Spring e PostgreSQL descartável
(Flyway V18), confirmando que o paciente vem da API, a unidade fica sem opção
e o banco não recebe pedido nem autorização. A tela foi fotografada sem dados
pessoais em `/tmp/exameperto-app.png` e `/tmp/exameperto-pedidos.png`, usando
sessão preparada e APIs mockadas; não são prova de login real. O endpoint
normal `/api/v1/orders` sem sessão respondeu 401 com `correlationId` sanitizado.

URLs para avaliação local: `http://127.0.0.1:5173/app` e
`http://127.0.0.1:5173/app/pedidos` após login real. A conta existente, home,
acesso, banco e arquivos pessoais foram preservados; não houve deploy.

## Reenvio/cadastro duplicado no ambiente normal — 14/10/2026

A conta local cadastrada para login é `gregoriokayque352@gmail.com`; a senha
não foi lida, gerada, alterada ou registrada. O banco continua com uma única
conta `ATIVO`, confirmada, sem desafio de confirmação ativo e sem outbox
pendente.

A investigação separou as causas. As tentativas recentes com endereços fora
da allowlist falharam por `EMAIL_RECIPIENT_NOT_ALLOWED`; a reprodução normal
mais recente retornou HTTP 503, `correlationId=76da1b6b-3f1f-44f5-9abd-02c28d42e035`
e a exceção sanitizada no log foi a mesma categoria. O processo atual carrega
os valores do `.env.smtp.local` sem divergência: SMTP Gmail/587, gateway
habilitado, remetente presente e allowlist de uma entrada com remetente
permitido. A outbox está vazia de pendências e o transporte anterior foi
aceito; não há evidência de falha de autenticação ou conexão SMTP nesta
tentativa. A allowlist não foi ampliada.

Com o endereço autorizado já existente, a reprodução pela interface retornou
HTTP 202 como previsto para proteção contra enumeração, mas a UI antiga dizia
que uma confirmação havia sido enviada e levava a uma confirmação impossível.
Foi corrigida somente essa orientação: o 202 agora permanece no cadastro com
mensagem neutra e links explícitos para confirmação, login e recuperação; uma
falha de integração também orienta login/recuperação sem revelar existência
de conta. A resposta pública continua genérica.

Reprodução pós-correção no ambiente normal: HTTP 202, `/cadastro`, links de
Entrar/Recuperar visíveis e invariantes do banco inalterados (`1|ATIVO:1|1|0|0`).
Testes Chromium de regressão passaram 6/6 selecionados; build web passou;
`RegistrationTest` 2/2, `MailEmailGatewayTest` 3/3 e `AuthFlowTest` 11/11
passaram com PostgreSQL descartável. Próxima ação do titular: entrar com o
endereço acima ou usar recuperação; não usar outro endereço, pois o ambiente
normal só autoriza a caixa configurada.

## Reconstrução da experiência de acesso — 14/10/2026

`/acesso` foi separado do mural antigo: a rota agora monta uma moldura
dedicada de autenticação alinhada à identidade da home, com e-mail, senha,
mostrar/ocultar senha, estado de carregamento, erro junto ao formulário e
links para entrar, cadastro, recuperação e confirmação. Cadastro, confirmação
e recuperação usam a mesma camada pública; `/app` continua sendo a camada
autenticada e só monta navegação/áreas depois da sessão.

A lógica existente de autenticação não foi substituída: chamadas de login,
CSRF, refresh, logout, cookies, sessão e capacidades permanecem as mesmas.
O dashboard pós-login continua em `/app`; logout retorna a `/acesso` sem
conteúdo autenticado. A home pública não foi alterada.

Evidências: o processo local em `127.0.0.1:5173` serviu a versão nova e os
testes Chromium públicos passaram 5/5; build web passou; `AuthFlowTest` passou
com PostgreSQL 17.6 descartável. O teste de navegação login → dashboard →
logout usa sessão preparada e endpoints mockados, portanto não é login real.
As capturas `web/artifacts/screenshots/access-dedicated-desktop.png`,
`access-dedicated-mobile.png`, `app-prepared-session-desktop.png` e
`app-prepared-session-mobile.png` não contêm dados pessoais; as capturas do
dashboard são explicitamente de sessão preparada.

Arquivos pessoais e dados locais foram preservados; não houve deploy. Próxima
ação: revisão visual do autor e, se aprovado, homologação do login real com a
conta autorizada já existente.

## Investigação do reenvio e autenticação na conta existente — 14/10/2026

Foi retomada a pendência de homologação usando a conta já existente no
PostgreSQL local, sem criar outra conta, remover dados ou alterar a senha. A
cadeia real estava disponível em `127.0.0.1:5173` → API `127.0.0.1:8080` →
PostgreSQL 17.6 em `127.0.0.1:5433`; Flyway V18 estava aplicado e o gate de
restauração permaneceu `NORMAL`.

Evidências sanitizadas: o banco continua com uma única conta, em estado
`ATIVO`, com `email_verificado_em` preenchido; portanto a confirmação de
e-mail está registrada no backend/PostgreSQL. O reenvio público de confirmação
respondeu HTTP 202, sem criar novo desafio porque a conta já está confirmada.
O pedido real de recuperação respondeu HTTP 202, criou/atualizou a outbox e o
worker terminou em `ENVIADO`, com desafio de recuperação ativo. Isso confirma a
execução da cadeia API → transação PostgreSQL → worker → gateway SMTP; o
recebimento na caixa ainda depende do titular.

O login foi exercitado contra o endpoint real com origem correta e senha
deliberadamente inválida, retornando HTTP 401/`UNAUTHENTICATED`; a proteção de
origem sem o cabeçalho correto também retornou HTTP 403. A sessão existente
permaneceu preservada e o banco terminou sem sessões abertas. Login autenticado
e conclusão da recuperação ainda não podem ser declarados:
exigem que o titular leia o código da mensagem enviada, escolha a própria
senha e entre em `/acesso`. Próxima ação: titular usar o código em
`/recuperacao/nova-senha`, definir a senha, fazer login, recarregar, renovar e
sair; depois registrar a evidência observada sem expor o código ou a senha.

## Reconstrução da experiência de produto — 14/10/2026

Implementação iniciada conforme autorização explícita do autor, sem declarar
aceitação do produto inteiro. A web agora possui home pública em `/`, com
explicação honesta do serviço, etapas de solicitação/retirada/recebimento,
ações de criar conta/entrar/entregador e aviso de disponibilidade ainda
dependente de unidades, políticas e integrações reais. O acesso continua em
URLs separadas: `/acesso`, `/cadastro`, `/confirmacao`,
`/confirmacao/reenviar`, `/recuperacao` e `/recuperacao/nova-senha`.

Após cadastro, o e-mail usado fica apenas em `sessionStorage` para contexto da
jornada e a interface encaminha para confirmação; recuperação encaminha para a
escolha de senha. Páginas públicas não consultam CSRF sem cookie de refresh;
restauração só é tentada ao abrir diretamente uma rota protegida `/app`. O
backend, worker SMTP, banco, migrações e dados existentes não foram alterados.

O `/app` passou a iniciar em painel de paciente: consulta perfil, capacidades e
pedidos reais, mostra confirmação de e-mail, ação principal de solicitar
entrega, pedidos recentes e estados vazios, além de acessos a benefícios,
familiares e configurações. Áreas operacionais existentes continuam acessíveis
conforme capacidades do backend.

Evidências desta rodada: `npm run build` passou; testes públicos Playwright
atualizados passaram 4/4, incluindo axe sem violações na home, falha de
cadastro com resposta genérica, histórico de rotas e 404; `git diff --check`
passou; prontidão integrada já registrada pelo `scripts/start-local.sh`.
Capturas sem dados pessoais: `web/artifacts/screenshots/home-public-desktop.png`
e `web/artifacts/screenshots/access-mobile.png` (viewport Chromium Pixel 5).
Envio, recebimento e validação pessoal da conta autorizada continuam não
confirmados. Próxima ação: conferência manual do autor em ambiente local,
incluindo caixa autorizada, confirmação, recuperação, login, refresh, reload e
logout.

## Falha real de cadastro no ambiente local — 14/10/2026

O `correlationId=bdacd841-7e69-4d91-905c-8b2658ec02e5` chegou como `EMAIL_TRANSPORT_REJECTED`, mas a versão anterior descartava a exceção original; os logs disponíveis não permitem atribuir esse caso histórico especificamente a autenticação, STARTTLS, conexão ou destinatário. A investigação atual confirmou, sem imprimir valores privados, que o backend carregou host/porta/usuário/senha SMTP, remetente, allowlist, habilitação e origem exatamente da configuração local; remetente e usuário coincidem, o remetente está na allowlist e `controle_restauracao` está `NORMAL`. Uma verificação SMTP controlada confirmou conexão, STARTTLS e autenticação sem envio.

O `MailEmailGateway` agora registra somente o resumo estrutural da configuração (sem endereço, senha, token ou mensagem) e classifica futuras falhas como `SMTP_AUTHENTICATION`, `SMTP_CONNECTION`, `SMTP_SEND`, `SMTP_TRANSPORT`, `EMAIL_CONFIGURATION_MISSING`, `EMAIL_RECIPIENT_NOT_ALLOWED` ou `EXTERNAL_EFFECTS_BLOCKED`. A resposta pública continua genérica. Uma tentativa real controlada para a única caixa autorizada retornou HTTP 202; o ambiente aceitou o envio. Ela deixou uma conta local pendente de confirmação, preservada no banco para não apagar dados.

`start-local.sh` agora aguarda prontidão HTTP real em critérios independentes: backend responde `401` em `/api/v1/me` sem sessão, o endpoint CSRF responde `403/FORBIDDEN` sem cookie tanto sem `Origin` quanto com `Origin: http://127.0.0.1:5173`, e Vite responde `200`. O CSRF não é consultado em polling para não acionar o rate limit; em timeout, exibe diagnóstico sanitizado. Backend e frontend ficaram iniciados em `127.0.0.1:8080` e `127.0.0.1:5173`, PostgreSQL permanece em `127.0.0.1:5433` e o volume foi preservado.

Evidências do cadastro controlado, separadas: (1) cadastro aceito pela aplicação: HTTP 202; (2) aceitação do transporte SMTP: `JavaMailSender.send` concluiu sem exceção, após autenticação/STARTTLS controlados, portanto o servidor SMTP aceitou a transação; (3) recebimento: ainda não confirmado pelo autor; (4) login: ainda não concluído. `mail.debug` permaneceu `false`.

A conta pendente preservada corresponde à entrada autorizada após comparação interna do `email_busca`, sem imprimir o endereço; há um desafio `EMAIL` não consumido e dentro do prazo. A senha dessa conta foi gerada aleatoriamente pelo teste e não foi escolhida pelo autor, nem é recuperável. Para concluir sem criar conta ou remover dados, o autor deve abrir a caixa autorizada, usar o código/link recebido na tela `/confirmacao`, solicitar recuperação em `/recuperacao`, abrir o segundo e-mail, inserir o código em `/recuperacao/nova-senha`, escolher a própria senha e então entrar em `/acesso`. Se o primeiro desafio tiver expirado, usar apenas `/confirmacao/reenviar` para a mesma conta autorizada.

Testes pertinentes: `MailEmailGatewayTest` 3/3, `AuthFlowTest` 11/11, `RegistrationTest` 2/2, build Maven, `bash -n` dos scripts, cadastro real controlado HTTP 202 e prontidão integrada (`/me` 401, CSRF 403/FORBIDDEN sem/com Origin autorizado, Vite 200). `mail.debug` permaneceu `false`.

## Rodada técnica das sprints 6–9 — 14/10/2026

O 401 da entrega foi reproduzido e corrigido sem ampliar permissões. A causa combinava três defeitos de preparação/contrato: cookie de refresh `Secure` em origem HTTP do fixture, `If-Match` usando a versão da custódia em vez da versão do pedido e consulta de participação referenciando `autorizacao_escopo.id`, coluna inexistente. A aplicação mantém `security.cookie-secure=true` por padrão; somente o fixture descartável de navegador usa `false`. A interface do entregador agora conserva a custódia retornada após cada ação, para reload e próximo comando usarem a versão correta, e os tipos de ocorrência exibidos coincidem com o contrato do backend.

Evidência principal: `CustodyBrowserFlowTest` passou 1/1 com três contas sintéticas e dois pedidos isolados. O entregador fez retirada, início, ocorrência e entrega pela tela; o destinatário em outro contexto emitiu e usou o código; um terceiro tentou comando direto e recebeu 404. PostgreSQL confirmou `ENTREGUE`, `custodia.encerrada_em` preenchida, `OCORRENCIA`, custódia da ocorrência aberta e código usado uma única vez.

Validação da rodada: suíte Maven comum sem falhas/erros nos relatórios; suíte Chromium explícita agora inclui `IntegratedAcceptanceBrowserFlowTest` e passou 17/17 testes em 11 classes; `PrivilegedOperationsBrowserFlowTest` passou 1/1 cobrindo operação, benefícios, privacidade, MFA, autoanálise, ausência de papel, sessão sem MFA e ações pela interface; a aceitação integrada passou 1/1 com rotas públicas, todas as áreas autenticadas do ator sintético, histórico, reload, logout, perda do refresh, estados vazios, responsividade e axe; build web passou; Playwright público standalone 4/4, com 11 skips (os 10 skips condicionais anteriores mais o novo teste integrador, que é executado pelo fixture Java). A matriz técnica/operacional e URLs estão em [REVISAO-SPRINTS-5-9](REVISAO-SPRINTS-5-9.md).

Situação honesta: a aceitação técnica integrada da sprint 9 está validada no ambiente isolado; isso não declara homologação operacional/financeira externa nem aceite de dependências reais. Sprints 6–8 mantêm seus recortes técnicos locais validados e suas dependências externas separadas. Não houve deploy, dinheiro real, banco externo, fixtures na aplicação normal, alteração de `.vscode/`, `referencia.md` ou `sprints/`.

As três causas do antigo 401 foram independentes e ficam registradas separadamente: (1) o fixture HTTP emitia cookie de refresh com `Secure`, impedindo o navegador local em HTTP de renová-lo; (2) a interface enviava no `If-Match` a versão da custódia, embora o endpoint exigisse a versão do pedido; (3) a consulta SQL de participação usava `autorizacao_escopo.id`, coluna inexistente. A autorização não foi ampliada: depois da preparação correta, o destinatário autorizado concluiu o recebimento e o terceiro continuou recusado.

## Revisão do fechamento das sprints 5–9 — 13/10/2026

O fechamento anterior estava incorreto: os prompts originais 5–9 têm escopos distintos, embora os arquivos locais `sprints/6.md` a `sprints/9.md` tenham sido sobrescritos com uma cópia da sprint 5. A revisão direta de código e testes constatou que a sprint 5 não possuía jornada própria de cadastro/documentos/veículos no navegador; a sprint 6 não possuía teste das áreas privilegiadas; a sprint 8 validava aporte, mas não ações de repasse nem isolamento institucional no navegador; e a sprint 9 validava apenas aceite de orçamento. Portanto essas sprints não estavam concluídas. A matriz de evidência e URLs está em [REVISAO-SPRINTS-5-9](REVISAO-SPRINTS-5-9.md).

Entrega validada nesta rodada: a sprint 5 ganhou `DelivererBrowserFlowTest`/`deliverer-real.spec.ts` e passou com Chromium → Vite → API → PostgreSQL 17.6 descartável, incluindo cadastro, documentos, substituição de CRLV, veículo, bloqueio de oferta e histórico; commit `d48c177`. O painel financeiro recebeu uma jornada Playwright de MFA e solicitação de repasse em fixture controlado, passou e foi registrada em `bdb48ce`. Testes públicos de autenticação não são evidência de operação ou financeiro. Nenhum deploy foi realizado.

Situação honesta por escopo, URLs, jornadas, lacunas e dependências: [REVISAO-SPRINTS-5-9](REVISAO-SPRINTS-5-9.md). Sprints 6, 7 e 9 continuam não concluídas; sprint 8 permanece parcial até prova negativa de isolamento institucional no navegador. Não há declaração de aceitação do produto inteiro.

## Registro anterior dos prompts 1–9 — corrigido em 13/10/2026

Foi executado um conjunto inicial em `sprints/`, mas a declaração de que os prompts originais 5–9 eram idênticos era indevida. Os arquivos locais haviam sido sobrescritos e não representavam os originais. A entrega acrescentou rotas públicas e autenticadas com histórico/reload, 404, seletor de área por capacidades, apresentação pública do serviço, contexto explícito de representação, seleção de paciente/destinatário e formulários de benefício/pedido por etapas. Uploads de entregador e veículo exibem progresso; cadastro, documentos, veículos, ofertas, tarefas e histórico usam serviços existentes, sem conceder aprovação. Isso não constituiu, por si só, aceite das sprints 5–9.

`scripts/start-local.sh` verifica ferramentas/configuração, evita duplicação, inicia PostgreSQL/backend/web e chama a verificação integrada antes de anunciar prontidão. `stop-local.sh` encerra somente grupos registrados e preserva o volume. Diagnóstico real: CSRF sem cookie de renovação permaneceu `403` por segurança; cadastro deliberadamente fora da allowlist retornou `503` antes do transporte e sem conta; favicon agora responde `200`. Detalhes: [EXECUCAO-SPRINTS-1-9](EXECUCAO-SPRINTS-1-9.md).

Validação daquela entrega: build web e Playwright público passaram (4/4, incluindo URLs diretas, voltar e 404). `BrowserFlowTest`, `FamilyBrowserFlowTest` e `OrderBrowserFlowTest` com `-DbrowserTest=true` passaram, exercitando seus recortes específicos em Chromium → Vite → API → PostgreSQL 17.6 descartável. Eles não validaram operação privilegiada, repasse, isolamento financeiro nem aceitação integrada. A regressão corrigiu a corrida de preparação do mock de e-mail e expectativas antigas de navegação/semântica. `bash -n`, links Markdown e `git diff --check` passaram. Bloqueios reais: catálogos autorizados de política/unidade, participantes e responsáveis reais, rota/pagamento/repasse/antimalware e aparelho Android; backlog em [.scratch/sprints-1-9](../.scratch/sprints-1-9/README.md).

Fechamento Git: base e ponta remota foram confirmadas em `4fdd19f`; o conjunto foi revisado, commitado em `07af398` e enviado sem force push para `origin/docs/planejamento-tecnico`. `.vscode/`, `referencia.md` e os prompts locais em `sprints/` permaneceram fora do commit. O OpenAPI não foi alterado; a tentativa de revalidar o contrato encontrou `openapi_spec_validator` ausente neste ambiente, portanto nenhuma validação nova do YAML é alegada nesta rodada.

## SaaS local navegável e capacidades nominais — 11/10/2026

A web deixou de esconder o domínio atrás de `VITE_ENABLE_OPERATIONAL_TEST_PANELS`: após o login, um shell responsivo consulta `/api/v1/me/capabilities` e organiza visão geral, conta, paciente/família, benefícios, pedidos, privacidade, cadastro e trabalho do entregador, análise operacional, análise de benefícios, financeiro e operação de privacidade. A projeção apenas monta a navegação; serviços continuam revalidando vínculo, escopo, instituição, versão, MFA e atribuição. A interface passou a cobrir ofertas/designação, endereços de tarefa, retirada, custódia, entrega e ocorrência no navegador; GPS contínuo permanece explicitamente móvel. Benefícios agora incluem paciente representado, evidências e recurso; filas de entregador, veículo, benefício e privacidade têm estados vazios e ações reais, sem transformar bloqueio em sucesso.

A V18 separa `OPERADOR_PRIVACIDADE` de `ANALISTA_OPERACIONAL`, cria trilha append-only `auditoria_acesso_local` e corrige MFA/análise de benefício para reconhecer AB institucional sem conceder AO global. `scripts/provision-local-access.sh` fica desabilitado por padrão, exige PostgreSQL em loopback, opera somente sobre conta ativa criada normalmente, exige instituição existente para AB/GF e não tem endpoint público. Não cria conta, parceiro, política, programa, saldo, identidade ou aprovação; autoanálise e dupla revisão continuam exigindo pessoas distintas. `scripts/stop-local.sh` preserva o volume.

Validação concluída para esta etapa: `npm run build` passou; `BenefitFlowTest` e `PrivacyFlowTest` passaram com Testcontainers/PostgreSQL 17.6; `RepresentationFlowTest`, `VehicleLinkFlowTest` e `BackupRestoreFlowTest` passaram isoladamente após a atualização V18; `BrowserFlowTest -DbrowserTest=true` passou com Chromium → Vite → API → PostgreSQL descartável, incluindo reload, acessibilidade axe, 390×844/1280×900 e bloqueio `ACCOUNT_CONFIGURATION_MISSING`; `verify-local-environment.sh` passou no ambiente real após reinício, sem apagar o volume. Uma execução histórica da suíte de 67 testes expôs quatro expectativas/flakiness de contexto; as três incompatibilidades de contrato foram corrigidas e a execução serial foi interrompida por custo de reiniciar cada Testcontainers por classe, portanto não é apresentada como suíte final verde. Dependências restantes são reais: instituições/programas/políticas/unidades e responsáveis, segunda pessoa independente, provedor de rota/pagamento/repasse, expurgo habilitado, antimalware abrangente e GPS/aparelho. Nenhuma dessas foi simulada.

## Revisão integrada local e correção de acesso — 11/10/2026

A cadeia ativa foi inspecionada sem expor segredos: Vite `127.0.0.1:5173` encaminha à API `127.0.0.1:8080`, PostgreSQL está no Compose em loopback, Flyway V1–V17 está aplicado e o gate de restauração está `NORMAL`. O processo Java reiniciado carrega a configuração privada correta; SMTP Gmail/587, autenticação, STARTTLS obrigatório, verificação de hostname, remetente e allowlist única estão estruturalmente válidos, e uma autenticação SMTP sem envio foi aceita. Nenhuma mensagem real foi enviada nesta revisão e o banco local continua sem contas.

O acesso observado em `/auth/register` não era uma troca interna de login: a página abria em cadastro. Ela agora abre em login, só cadastra após escolha explícita, oculta formulários públicos durante sessão ativa, restaura refresh HttpOnly/CSRF após reload e oferece navegação entre perfil, privacidade e família. A origem canônica passou a ser `http://127.0.0.1:5173` em backend, scripts, configuração privada e documentação; `localhost` recebe 403, sem ampliar CORS. O cadastro ganhou logs sanitizados correlacionáveis para distinguir configuração ausente de rejeição do transporte sem registrar endereço, senha, token ou mensagem.

A [revisão integrada local](REVISAO-INTEGRADA-LOCAL.md) classifica os fluxos por perfil e registra superfícies somente mobile/API e dependências externas. Scripts novos iniciam a web sem duplicar porta e verificam Vite → backend → PostgreSQL, migrações, gate e estrutura da configuração. A regressão UI passou 3/3; `AuthFlowTest` passou 11/11; `BrowserFlowTest -DbrowserTest=true` passou após incluir reload de sessão e logout antes de novo cadastro. Build web e validação do ambiente real passaram. Próxima ação: o autor usar <http://127.0.0.1:5173/>, cadastrar somente a própria conta allowlisted, confirmar o código privado e percorrer login, perfil, recuperação, troca e logout; recebimento de e-mail continua dependendo dessa ação privada. Papéis privilegiados, segunda conta familiar, aparelho Android, políticas operacionais e provedores de rota/pagamento/repasse permanecem bloqueios reais.

## Roteiro local de interfaces — 10/10/2026

Foi mapeada a navegação existente para paciente, familiar, entregador, revisão operacional e financeiro em [ROTEIRO-INTERFACES-LOCAL](ROTEIRO-INTERFACES-LOCAL.md). A web V1 mantém perfil, privacidade e família; os painéis operacionais legados podem ser montados somente pelo Vite em desenvolvimento com `VITE_ENABLE_OPERATIONAL_TEST_PANELS=true`. A condição conjunta com `import.meta.env.DEV` preserva o build de publicação, e nenhuma autorização do backend foi alterada. A execução de ofertas, tarefa, custódia e GPS do entregador existe somente no aplicativo mobile; filas operacionais de cadastro/benefício e ações de privacidade privilegiadas não têm interface gráfica própria.

A configuração `.env.smtp.local` existente foi verificada apenas quanto a modo `0600`, presença das chaves necessárias e allowlist não vazia, sem imprimir endereço, senha, chaves ou tokens. Nenhuma conta, papel, política, programa, saldo, pedido, benefício, pagamento, repasse, entrega ou posição foi criado ou alterado. As portas locais estavam inativas, portanto nenhum serviço foi iniciado nesta rodada.

Bloqueios: a conta real só pode ser reutilizada se o volume local ainda existir; familiar exige uma segunda conta real e destinatário expressamente autorizado; AO/AB/GF dependem de provisionamento nominal ainda pendente; decisões profissionais/benefícios, rota/tarifa/protocolo, provedores financeiros, retenção de GPS e ensaio em aparelho permanecem indisponíveis. Próxima ação: iniciar backend e Vite nos dois terminais indicados pelo roteiro, entrar somente com a conta real existente e inspecionar as áreas sem submeter operações bloqueadas.

## Homologação local de e-mail — em preparação — 10/10/2026

O autor autorizou Gmail SMTP local e o roteiro controlado de cadastro, confirmação, login e recuperação, limitado ao mesmo remetente/destinatário sob seu controle. Foi adicionada uma allowlist obrigatória no gateway comum: sem `EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS`, o SMTP permanece indisponível; endereço alheio é recusado antes do transporte. A barreira cobre cadastro, recuperação, convites e worker da outbox. A configuração continua em loopback, com autenticação e STARTTLS obrigatório, sem deploy ou acesso público.

Foram criados os scripts `configure-local-email.sh` e `start-local-email-homologation.sh`. O primeiro recebe endereço e senha de app sem eco, gera chaves locais e grava somente `.env.smtp.local` ignorado pelo Git e com modo `600`; mantém `REGISTRATION_PRIVACY_APPROVED=false`. Nenhuma credencial, endereço real, token ou link foi versionado ou registrado na evidência. Os testes focados passaram 4/4 (allowlist e rollback em falha SMTP). A suíte Maven comum descobriu 67 testes: 59 executados sem falhas/erros e 8 opt-ins ignorados. Sintaxe dos scripts, geração descartável, modo do arquivo, configuração Gmail esperada, links locais e `git diff --check` também passaram; `shellcheck` não estava instalado.

Bloqueio atual: a política mínima de privacidade para o cadastro real de homologação ainda não foi aprovada. A decisão deve definir finalidade, dados mínimos, acesso, retenção/descarte e responsável apenas para o ensaio local. Por isso não houve envio real e não se declarou aceitação SMTP nem recebimento. Próxima ação: autor registrar essa decisão e inserir a senha de app pelo prompt local; então executar somente duas mensagens (confirmação e recuperação), registrar aceitação SMTP separadamente do recebimento e verificar uso único, login, troca de senha e revogação das sessões anteriores.

## Auditoria integrada de segurança — base `e11b1c1` — 09/10/2026

Foi criada a [auditoria de segurança e funcionamento integrado](AUDITORIA-SEGURANCA-FUNCIONAL.md). A revisão corrigiu leitura ilimitada de webhook público, limite de GPS após desserialização e polling/mutação da outbox durante restauração. Os limites agora são aplicados antes do MVC (`payment.max-event-payload-bytes=65536` e `tracking.max-payload-bytes=1024`), sem habilitar provedor.

Após a correção: a suíte final com todos os opt-ins (`mvn -DbrowserTest=true test`) passou 64/64, além dos testes focados (9/9), build web, opt-in de aceite no navegador e export/typecheck mobile. Banco novo aplica Flyway V1–V17 e o upgrade V5 é exercitado. Playwright comum passou 2 e manteve 8 opt-ins condicionais. O desalinhamento do Expo Doctor foi corrigido com `expo@~57.0.27` e `expo-location@~57.0.20`: agora 21/21 verificações passam; `npm audit` mantém 22 alertas transitivos mobile (15 altos/7 moderados). `adb`/emulador e Endor Labs ficaram indisponíveis (falta de aparelho, credencial e namespace), mas Endor é opcional e não bloqueia isoladamente. Nenhum e-mail, contratação, dinheiro, dado real ou deploy ocorreu.

Prontidão: V1 local está pronta apenas para homologação condicionada; operação completa permanece bloqueada por SMTP/chaves/origem/políticas, fornecedores, retenção, backup/RPO-RTO, antimalware, mobile físico, rate limit distribuído e alertas mobile. Próxima ação: autor fornecer SMTP/remetente/caixa de teste e autorizar envio local para o roteiro cadastro → confirmação → recuperação.

## Orçamento zero e ordem de homologação — 09/10/2026

R$ 0 passou a ser requisito eliminatório. A decisão registrada em [ORCAMENTO-ZERO-V1](ORCAMENTO-ZERO-V1.md) é homologar primeiro em `localhost`, com PostgreSQL/volume/backup locais e uma caixa SMTP já controlada pelo autor. Nenhuma conta externa foi criada, nenhum cartão/domínio foi cadastrado ou comprado, nenhuma cobrança foi habilitada, nenhum e-mail foi enviado e não houve deploy.

Foram conferidos em fontes oficiais atuais cota, expiração, suspensão e persistência de Brevo Free, Neon Free, Cloudflare Pages Free, Render Free e Koyeb Free. Brevo é candidato futuro para e-mail, sem cartão e com 300 envios/dia, mas pode suspender envios e apaga contas gratuitas inativas após quatro meses. Neon e Pages são candidatos parciais; o worker que consulta o banco a cada segundo impede scale-to-zero e o frontend atual exige mesma origem. Render foi rejeitado porque bloqueia SMTP, perde arquivos e expira o PostgreSQL em 30 dias. Koyeb foi rejeitado porque exige cartão/pré-autorização e admite cobrança, além de não oferecer volume web persistente grátis.

Não existe composição hospedada gratuita aprovada: faltam, sem enfraquecer controles, frontend/backend same-origin, adaptador de e-mail HTTPS para host que bloqueie SMTP, armazenamento privado durável, backup independente e adequação segura do worker/pool ao scale-to-zero. A próxima ação permanece privada e local: o autor indicar uma conta SMTP já existente, remetente/destinatário e autorização específica; depois configurar sem registrar segredos e executar o roteiro de cadastro, confirmação e recuperação.

## Acessibilidade V1 e preparação externa — base `b9b83be` — 09/10/2026

O recorte web autenticado foi fechado em perfil, solicitações/encerramento e gestão familiar. Cadastro de entregador, revisão de veículo, benefício, aporte, pedido/rastreamento e repasse permanecem no código legado e sob as permissões existentes do backend, mas deixaram de ser montados na navegação V1; nenhuma autorização de API foi ampliada. Foram corrigidos overflow de campos em 390 px, foco visível de inputs/select/textarea e landmarks complementares aninhados.

`BrowserFlowTest -DbrowserTest=true` passou com Chromium → API → PostgreSQL 17.6 descartável. O fluxo cobriu cadastro, confirmação, login, perfil, solicitações de acesso/encerramento, renovação/logout e recuperação; verificou teclado/foco inicial, foco após validação inválida, rótulos, alertas, ausência dos painéis excluídos, ausência de overflow e Axe sem violações na página autenticada inteira em 390×844 e 1280×900. `FamilyBrowserFlowTest -DbrowserTest=true` também passou e repetiu Axe/overflow nos dois tamanhos depois de perfil, convite, aceite, confirmação e revogação. Axe não comprova acessibilidade completa: leitor de tela humano, zoom/reflow adicional, alto contraste, movimento reduzido, outros navegadores/dispositivos e uso prolongado por teclado continuam pendentes.

`PORTFOLIO-V1` agora concentra a checklist externa com os nomes realmente aceitos pelo código, separa decisões/credenciais do autor das ações locais do projeto e registra o roteiro de homologação de cadastro, confirmação e recuperação. A implementação atual de objetos é POSIX por `EXAME_PERTO_PRIVATE_ROOT`; S3/objeto externo não foi declarado configurável. A regressão Maven descobriu 60 testes: 52 executados, 0 falhas/erros e 8 opt-ins ignorados; build web e os dois Playwright comuns passaram. OpenAPI 3.0.3 foi validado com `openapi-spec-validator 0.7.2`; links Markdown locais, padrões de segredo e `git diff --check` passaram. Nenhum provedor foi contratado, nenhuma mensagem externa foi enviada, nenhuma política nova foi habilitada e não houve deploy.

Próxima ação: o autor escolher o provedor SMTP e fornecer por canal privado remetente verificado, caixa de homologação e autorização do envio, junto das decisões de HTTPS/origem, cofre de segredos, PostgreSQL/volume privado, backup/RPO/RTO e política/responsáveis mínimos. Então executar o roteiro controlado de e-mail e registrar aceitação SMTP e recebimento separadamente.

## Interfaces web V1 — base `4827096` — 08/10/2026

Implementados painel de perfil próprio (nome/telefone com ETag e e-mail somente leitura), lista/registro de solicitações de privacidade e acompanhamento visual do encerramento, além da gestão familiar já existente. A API ganhou `GET/PUT /me/profile`, `GET /me/privacy-requests` e rota explícita 404 para perfis alheios; regras de autorização, retenção e execução não foram alteradas. A interface diferencia `purgeStatus`, `closureStatus` e `anonymizationStatus`, exibe `TRANSFORMADA_NAO_INTEGRAL`, resíduos e bloqueios por custódia/obrigações/política.

Evidência: `AuthFlowTest` (11/11) cobriu perfil próprio, edição otimista, conflito de versão e tentativa de rota alheia; `PrivacyFlowTest` (4/4) permaneceu aprovado. `BrowserFlowTest -DbrowserTest=true` passou após instalar o Chromium de teste: fluxo navegador→API→PostgreSQL criou conta sintética, confirmou e-mail, editou perfil, registrou/acompanhou acesso e encerramento, verificou residual, viewport 390×844 e Axe nos painéis novos, e continuou login/logout/recuperação. O build web, YAML OpenAPI e scripts de validação passaram.

Limite de acessibilidade: o Axe foi deliberadamente escopado a `.profile-panel` e `.privacy-panel`; a análise de toda a página revelou pendências de labels em painéis legados de veículo/pedido e não permite declarar acessibilidade integral. O ensaio usou somente PostgreSQL/Testcontainers, mailbox mock de teste e Chromium local; não homologa SMTP externo, deploy ou operação.

O recorte para entrevistas e visita está documentado em [PORTFOLIO-V1](PORTFOLIO-V1.md). Cadastro/confirmação de e-mail continuam condicionais a SMTP, chaves, origem HTTPS e política homologados. Entrega, benefício, rastreamento, pagamento e repasse permanecem bloqueados e desabilitados por padrão. Mobile continua separado.

## Registro histórico do portfólio V1 — base `f0db46f` — 08/10/2026

Este registro descreve o estado anterior à rodada `4827096`; a seção de interfaces V1 acima é a referência atual. Naquele estado, a interface ainda não expunha perfil básico nem acompanhamento de solicitações/encerramento. O recorte atual mantém as mesmas condições de SMTP, chaves, origem HTTPS e política de privacidade, mas acrescenta as telas e evidências descritas acima.

Entrega, benefício, rastreamento, pagamento e repasse ficam bloqueados e desabilitados por padrão. O plano lista banco, objetos privados, backup/restore, diário e custos a pesquisar sem escolher fornecedor, criar dados fictícios, contratar ou fazer deploy. Mobile continua uma pendência separada da publicação web.

## Encerramento e transformação controlada — base `a97ffef` — 08/10/2026

Implementada V17 para solicitação autorizada, acompanhamento e conclusão verificável. O fluxo revoga sessões, tokens, MFA, concessões, convites, papéis e conexões locais; remove recursos não financeiros já abrangidos pelo expurgo e transforma perfil, CPF/HMAC, nascimento e endereços para preservar somente as invariantes necessárias. `encerramento_conta` é retomável/idempotente e fica `BLOQUEADA` quando há custódia, operação financeira não terminal, obrigação de repasse ou reserva ativa; nenhuma responsabilidade é apagada por cascata.

`PrivacyFlowTest` usa apenas PostgreSQL/armazenamento descartáveis e cobre terceiro/familiar sem autorização, sessão/conexão ativa, referências indiretas, bloqueio por obrigação, retomada e reexecução, ausência residual e preservação financeira/deduplicação. `BackupRestoreFlowTest` reaplica o diário independente em uma cópia anterior, inclusive a transformação de perfil/endereço, antes de liberar o acesso. A transformação é explicitamente `TRANSFORMADA_NAO_INTEGRAL`: UUIDs, FKs, diário, auditoria, chaves e referências financeiras impedem alegar anonimização integral.

Limites: fornecedores, dispositivos, e-mails já recebidos, logs/observabilidade, PITR, chaves fora do ambiente e retenções financeiras ainda dependem de política/contrato aprovados. Não houve dado real, contratação, operação externa, deploy ou anonimização integral. Ver [especificação](ENCERRAMENTO-CONTA.md), [modelo](MODELO-DADOS.md) e [prontidão](PRONTIDAO-CONSOLIDADA.md).

## Backup/restauração com reaplicação — base `a8fbdc2` — 08/10/2026

Implementada V16 e a operação local em `ops/recovery/`: dump consistente sob suspensão confirmada de escritas, cópia dos objetos privados, manifesto de formato/versões/dependências, SHA-256, diário mínimo de expurgos em PostgreSQL independente, gate persistente, recibos idempotentes e liberação explícita. Enquanto o gate não está `NORMAL`, HTTP retorna `503 RESTORE_BLOCKED` e envio de e-mail falha fechado; pagamento/repasse atuais dependem das rotas bloqueadas. O procedimento exige ainda isolamento de rede e efeitos externos desligados.

`BackupRestoreFlowTest` passou com três PostgreSQL 17.6 e objetos sintéticos: criou dados/documentos/GPS/financeiro, autorizou expurgo V15, fez backup anterior, executou/verificou/capturou o expurgo, restaurou em outro container, interrompeu com gate `BLOQUEADO`, retomou e repetiu idempotentemente, verificou ausência de documento/objeto/GPS/outbox, preservou documento/operação/outbox financeira e chaves de negócio/deduplicação, confirmou bloqueio mesmo sem a linha do gate, reaplicou encerramento de perfil/endereço, liberou separadamente e recusou dump corrompido. Na execução focal final, a classe levou 15,03 s; isso não é RTO. A suíte comum descobriu 59 testes: 51 executados, 0 falhas/erros e 8 opt-ins ignorados.

Limites: checksum não autentica contra atacante e o bundle não cifra a si próprio; diário/captura/alerta, armazenamento e chaves reais, grande volume, rotação, PITR, perda de host/fornecedor/região, falha simultânea e RPO/RTO continuam pendentes. GPS, backups, evidências financeiras e conteúdo privado mantêm suas categorias; nenhum prazo foi promovido. Anonimização integral, retenção financeira/deduplicação e fornecedores continuam bloqueios separados. Ver [procedimento](BACKUP-RESTAURACAO.md) e [prontidão consolidada](PRONTIDAO-CONSOLIDADA.md).

Próxima ação: selecionar infraestrutura/gestão de chaves autorizada, automatizar e monitorar a captura do diário e ensaiar escala/falhas reais com RPO/RTO aprovados. Até lá, manter dados reais e publicação operacional bloqueados; esta rodada não autoriza deploy nem anonimização integral.

## Revisão consolidada a partir de `3b78b64` — 08/10/2026

Código, migrações, configuração e testes foram conferidos diretamente. A suíte comum descobriu 57 casos: 49 executados, 0 falhas/erros e 8 opt-ins; a execução `-DbrowserTest=true` dos oito opt-ins passou após corrigir a mensagem web que não explicitava “indisponível”. O foco de privacidade, sessões/WebSocket, financeiro, custódia, designação e upgrade também passou. A correção foi somente de texto sanitizado em `web/src/main.tsx`.

O recorte local é verificável com PostgreSQL/Testcontainers e objetos descartáveis. A versão de portfólio pode ser publicada apenas com limitações explícitas, sem contas fictícias ou saldo simulado. Operação real permanece bloqueada pela infraestrutura real de backup/diário/cifragem e seus RPO/RTO, anonimização integral da conta, retenção financeira, homologações externas, aparelho móvel e revisão de configuração/test-only. Mobile mantém 23 alertas transitivos (16 altos/7 moderados) e incompatibilidade de patch Expo; não há scanner CVE JVM configurado. Nenhum dado real foi excluído, nenhum dinheiro movimentado e nenhum deploy ocorreu.

Ver [revisão consolidada](PRONTIDAO-CONSOLIDADA.md) para a matriz 01–15, composição histórica 50/7 → 53/7 → 57/8, lacunas técnicas, tickets P0/P1 e recortes local/portfólio/operação. Próxima ação: revisão humana das políticas e dos tickets de seguimento; não declarar prontidão produtiva.

## Ticket 15 — titular, retenção e expurgo (implementação independente parcial, 08/10/2026)

Base autorizada: `880b457`; implementação local `dfad651`; branch `docs/planejamento-tecnico`; `referencia.md` preservada, não rastreada e sem alteração.

Implementada V15 com solicitações próprias de ACESSO/CORRECAO/EXCLUSAO, protocolo, descrição/resposta cifradas, acompanhamento e autorização proporcional. Familiar sem concessão não consulta o protocolo. Resposta (`RESPONDIDA`/`NEGADA`) não conclui exclusão; a sequência `EXPURGO_SOLICITADO` → `VERIFICADA` exige operador nominal, MFA, política validada e configuração explícita. O expurgo é idempotente sob lock, produz tombstone sem conteúdo e cobre o registro de recursos para banco, objeto, versão, temporário, fila, cache, referência, fornecedor e backup. FINANCIAMENTO/COMPROVANTE e chaves de deduplicação não são removidos.

Contrato, modelo, segurança e diagrama foram atualizados. `privacy.purge-enabled=false` continua o padrão; prazos propostos D08 não foram promovidos a política. Retenção financeira permanece pendente. Fornecedores, dispositivos, backups/restauração e RPO/RTO exigem procedimento/contrato e não são declarados comprovados. Não houve exclusão de dados reais, operação externa ou deploy.

Verificações locais desta rodada: suíte Maven comum com 57 testes descobertos, 49 aprovados, 0 falhas/erros e 8 opt-ins ignorados; os 8 opt-ins de navegador passaram separadamente. `PrivacyFlowTest` passou 3 cenários com PostgreSQL 17.6/objetos isolados, e Flyway cobriu banco novo V1–V15 e upgrade V5→V15. Web build e 2 Playwright comuns passaram; mobile typecheck/export Android passaram sem aparelho. OpenAPI 3.0.3 (126 operações e 1.095 referências), 104 links locais, 17 diagramas, diff e padrões de segredos foram conferidos. Web auditou sem vulnerabilidades; mobile mantém 23 nós sinalizados e incompatibilidade Expo (`expo-location`/patch do Expo), logo exige correção compatível e novo ensaio antes de publicação.

Bloqueios: não há política/responsável operacional real, inventário integral por categoria, scheduler, anonimização integral da conta, infraestrutura para ensaio de backup/restore, contrato/evidência de fornecedores ou homologação externa/mobile física. Retenção financeira e descarte de evidências/deduplicação continuam pendentes e preservados, sem retenção infinita aprovada. Próxima ação: revisão humana do conjunto e decisões de política/infraestrutura; deploy continua sem autorização. Ver [relatório de prontidão](PRONTIDAO-TICKET-15.md). O projeto não está pronto para produção.

## Ticket 14 — repasse e conciliação (implementação independente, 07/10/2026)

Base autorizada: `719357e`; branch `docs/planejamento-tecnico`; `referencia.md` preservada, não rastreada e sem alteração.

Implementada V14 com PostgreSQL: entrega comprovada apura `SERVICO_COMPLETO` pelo frete aceito e registra `LIQUIDACAO`/lançamentos balanceados e obrigação de repasse na mesma transação. A obrigação é distinta de `SOLICITADO`, `CONFIRMADO`, `FALHOU`, `INCERTO` e `DIVERGENTE`; outbox, referência estável `repasse:{pedido}`, eventos externos deduplicados e auditoria append-only preservam histórico. Timeout não cria referência nova: consulta/conciliação usa a operação existente e valida referência, valor, moeda e destinatário; eventos duplicados/antigos não pagam duas vezes. Cancelamento, ocorrência e serviço parcial permanecem bloqueados sem política aprovada.

Criadas fronteiras REST para painel institucional, consulta da parcela, solicitação e conciliação. A solicitação/conciliação exige GF nominal da instituição, vínculo ativo e MFA verificado; entregador/paciente consultam apenas sua própria parcela. O adaptador padrão retorna `503 INTEGRATION_UNAVAILABLE`; somente os testes usam `@MockBean` controlado, sem homologar transferência bancária. A tela web apresenta obrigações, divergências, incertezas e disponibilidade da integração, sem dados bancários.

Evidências: `PayoutFlowTest` executou 3 cenários via HTTP/API e PostgreSQL 17.6/Testcontainers real isolado, cobrindo obrigação/apuração, timeout e confirmação tardia, consulta sem nova referência, eventos fora de ordem/duplicados, divergência de moeda, concorrência de solicitação, MFA, instituição alheia, rollback sem lançamentos parciais e integração ausente. O ensaio opt-in `browserRendersRealFinancialPanel` passou com Chromium → Vite → API → PostgreSQL reais, sem interceptar a API. `mvn -q -Dtest=PayoutFlowTest test` passou (3/3); a regressão completa passou com 53 testes, 0 falhas e 7 opt-in ignorados; `mvn -q -DskipTests compile` e `npm run build --prefix web` passaram. Não houve dinheiro real, contratação, deploy ou chamada bancária.

Pendências: provedor, credenciais, beneficiários e homologação externa; responsáveis institucionais/dupla revisão; retenção financeira D08; regras aprovadas de cancelamento, ocorrência e serviço parcial; classificação contábil/fiscal. Mobile/GPS e retenção de posições permanecem pendências separadas do ticket 13. O ticket 14 não é marcado completo enquanto essas dependências e a validação operacional não existirem.

## Ticket 13 — rastreamento de tarefa (implementação técnica parcial, 07/10/2026)

Base autorizada: `d40eb5f`; branch `docs/planejamento-tecnico`; `referencia.md` preservada, não rastreada e sem alteração.

Antes do rastreamento, o ensaio opt-in de custódia foi completado em Chromium com API Spring e PostgreSQL 17.6 reais, sem interceptação: retirada, ocorrência sem encerrar custódia, código visível apenas ao destinatário autorizado, recebimento e encerramento. Os dois pedidos e todas as contas/evidências foram sintéticos e isolados; o banco confirmou custódia aberta na ocorrência e custódia encerrada/código consumido na entrega.

Implementada V13 e a fatia independente de rastreamento: captura foreground no app Expo após permissão explícita do sistema; horário, coordenadas e precisão; ingestão HTTP exclusiva do entregador/designação/tarefa/finalidade ativos; PostgreSQL com sequência única; distribuição STOMP privada; reautorização no envio, SUBSCRIBE e publicação; e interrupção de acesso após revogação, logout, expiração ou encerramento, inclusive para conexões já abertas. A web exibe horário, precisão, baixa precisão e desatualização; sem ponto, exibe indisponibilidade e nenhum marcador. Token não entra na URL e localização não é registrada em logs da aplicação.

Parâmetros D09 estão explícitos: captura 15 s, `stale` 60 s, buffer máximo oito pontos/dois minutos, corpo 1 KiB, servidor oito pontos/dois minutos, futuro 30 s e baixa precisão acima de 100 m. Reconexão faz GET autorizado e nova assinatura, portanto ponto antigo continua marcado como desatualizado. `EXAME_PERTO_TRACKING_ENABLED=false` por padrão: retenção/expurgo de GPS, prazo em backup, base/finalidade e responsável ainda não foram aprovados; não há job de expurgo e dados reais não devem ser habilitados.

Evidências: `TrackingFlowTest` passou com 2 cenários em HTTP, STOMP/WebSocket e PostgreSQL 17.6 reais, cobrindo publicação/assinatura alheias, familiar sem escopo e revogado com conexão aberta, logout, sessão expirada, tarefa encerrada, coordenadas inválidas, ponto antigo/futuro, duplicata idempotente/conflitante, fora de ordem, reconexão/desatualização e limites. `CustodyBrowserFlowTest` passou com 1 ensaio Chromium/API/PostgreSQL e dois pedidos. Coordenadas foram sintéticas somente no banco descartável. A suíte Maven completa passou com 50 testes, 0 falhas e 7 opt-in ignorados; Playwright comum teve 2 aprovados/7 opt-in ignorados; web build, mobile typecheck e export Android passaram (590 módulos, 1,5 MB). OpenAPI 3.0.3 passou em `openapi-spec-validator 0.7.2`; 101 links Markdown locais e 16 diagramas Mermaid foram validados.

Pendências: não há aparelho/emulador nesta rodada, logo captura GPS física, bateria, perda de sinal real, retomada prolongada e segundo plano em development build permanecem sem comprovação; os parâmetros continuam hipóteses até esse ensaio. O `npm audit --omit=dev` mantém 23 avisos transitivos do toolchain Expo (16 altos e 7 moderados, em `braces`, `node-forge` e `uuid`); a correção automática sugere downgrade incompatível e não foi aplicada. Ticket 13 não está completo. Não houve rastreamento de terceiros, operação física, contratação, deploy ou início do ticket 14.

## Ticket 12 — retirada, ocorrência e entrega comprovada (implementação parcial, 06/10/2026)

Base autorizada: `b50a6f9`; branch `docs/planejamento-tecnico`; `referencia.md` preservada e não rastreada.

Implementada V12 com PostgreSQL: custódia vinculada à designação/protocolo, retirada somente por entregador designado com autorização vigente e evidência `COMPROVANTE` aprovada, início de entrega, ocorrências auditáveis e confirmação de recebimento por código secreto do destinatário. Código usa hash, prazo de 30 minutos, cinco tentativas, uso único e locks; não aparece ao entregador, em logs ou em idempotência. Web acompanha custódia/ocorrências; app oferece retirada, início e entrega sem abrir/fotografar o envelope.

Retorno, reentrega e cancelamento após designação/retirada permanecem bloqueados por ausência de políticas aprovadas de destino, custo, cobertura, responsabilidade e apuração. Não foi implementada liquidação/repasse externo, entrega física ou rastreamento.

Evidência automatizada: `CustodyFlowTest` (2 testes) usa PostgreSQL 17.6/Testcontainers e HTTP real, cobrindo entregador alheio, transições, código incorreto/reutilizado, idempotência, ocorrência e ausência de efeitos parciais. Suíte Maven final: 47 testes descobertos, 0 falhas, 6 opt-in ignorados na execução comum. Web build passou; mobile typecheck e export Android passaram (581 módulos, 1,5 MB), sem `adb`/emulador, portanto export não é execução em aparelho.

## Ticket 11 — ofertas e designação (implementação parcial, 06/10/2026)

Base autorizada: `f7449ad`; branch `docs/planejamento-tecnico`; remoto confirmado `https://github.com/Kayquebrigadeiro/Exameperto.git`. `referencia.md` permaneceu fora do Git e sem alterações.

Implementada a fatia independente de ofertas/designações: migração V11, API REST, locks transacionais, idempotência, controle de versão, histórico, limite de tarefas simultâneas, revalidação de cobertura/aprovação no aceite, privacidade de oferta e acesso restrito ao endereço após designação. A oferta exige orçamento aceito vigente, cobertura de retorno confirmada, pagamento particular CONFIRMADA quando aplicável ou reserva subsidiada integralmente disponível; benefício aprovado isoladamente não libera oferta. Cancelamento e mudança de endereço antes da designação são idempotentes e liberam cobertura conforme regra existente; após designação permanecem bloqueados por política ainda indefinida.

Web exibe o acompanhamento correspondente e o app do entregador consulta ofertas, aceita e acompanha designações sem expor dados de saúde, renda, benefício ou endereço exato antes do vínculo. Contrato, modelo, segurança, diagramas e ticket 11 foram atualizados. Não foram criados seeds de produção: sem política profissional, protocolo/custódia, capacidade e cobertura reais, a operação permanece indisponível.

Evidências isoladas: PostgreSQL 17.6/Testcontainers com suíte Maven `45` testes, `0` falhas, `6` opt-in ignorados na execução comum; `AssignmentFlowTest` (3 testes) e os ensaios opt-in `OrderBrowserFlowTest`, `AcceptanceBrowserFlowTest` e `AssignmentBrowserFlowTest` passaram com navegador → API → banco real descartável. Web build passou e Playwright comum teve 2 aprovados/6 opt-in ignorados. Mobile typecheck e export Android passaram (581 módulos); não há `adb`, emulador ou `xcrun`, portanto não se declara execução em dispositivo. Evidência de navegador/API/banco não é homologação financeira externa.

Bloqueios: aprovações profissionais vigentes dos tickets 05–06; política/capacidade, unidade e protocolo de custódia reais; adaptadores e confirmação financeira externa; tratamento de suspensão/cancelamento após designação; homologação em dispositivo. Não houve movimentação financeira externa, contratação, retirada, rastreamento, deploy ou início do ticket 12. O ticket 11 permanece parcialmente concluído até essas dependências serem fornecidas e autorizadas.

Situação atual: tickets 03–04 implementados localmente e ticket 05 em continuidade técnica parcial; entrega real de e-mail e aprovação profissional continuam não homologadas. Registros abaixo preservam o histórico; ver a seção final para continuidade.

## Etapa e artefatos

Planejamento técnico documental concluído e publicado para revisão humana; aplicação, migrações e deploy não iniciados.

- [Modelo físico](MODELO-DADOS.md): campos/tipos, FKs, índices, estados, dinheiro exato, concorrência, idempotência e reconciliação.
- [OpenAPI](../contracts/openapi.yaml): contrato HTTP proposto com autenticação, DTOs, erros, paginação, versões e comandos idempotentes; STOMP descrito em segurança.
- [Segurança](SEGURANCA.md): matriz por ação/registro, riscos, controles e retenção pendente.
- [Glossário](../CONTEXT.md), [configuração de agentes](agents/issue-tracker.md), [spec e 15 tickets locais](../.scratch/planejamento/README.md), diagramas e índice de ADRs atualizados.

## Git e origem

Workspace recebido com `.git` vazio; remoto confirmado pelo usuário: `https://github.com/Kayquebrigadeiro/Exameperto.git`, consultado sem refs. Não havia workflow de deploy local nem histórico remoto. Criada branch documental `docs/planejamento-tecnico` e base preservando os cinco arquivos originais: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`. Essa base substitui a ausência de HEAD anterior para a revisão da etapa.

Skills exigidas não estavam instaladas. Fontes e templates consultados em cópia temporária do fork indicado, SHA `6654f6b60cd9d5be8b54c6fafe44346dabeb3b76`; configuração registrada, sem alegar instalação permanente. Histórico das escolhas em [domain](agents/domain.md). Repositório das skills nunca foi configurado como remoto do aplicativo.

## Verificações

- Contrato corrigido passou em `openapi-spec-validator 0.9.0` (OpenAPI 3.0.3): 69 operações. Comando: `/tmp/exame-perto-validation/bin/python -m openapi_spec_validator contracts/openapi.yaml`.
- Validação complementar local: YAML sem chaves duplicadas; 823 referências internas resolvidas, operationIds únicos, parâmetros de path/permissão e paginação conferidos; 57 links Markdown locais existentes; 15 tickets com critérios e dependências sem ciclos. Script temporário `/tmp/exame-perto-validate.py`; estes números são históricos da primeira sessão.
- Sete blocos Mermaid passaram em `mermaid.parse`, versão 12.0.0, ambiente temporário com jsdom. Não houve renderização/inspeção visual dos diagramas.
- `git diff --check` passou; nenhum código de aplicação/workflow foi criado.
- Consultas Git e inspeção documental inicial executadas; base original preservada.
- [Revisão em dois eixos](REVISAO.md): na retomada, dois revisores confirmaram as quatro correções até `63e033d`. Standards identificou novo P2 no vínculo da evidência de conciliação; correção documental commitada em `84631aa` e reconferida pelos dois revisores no conjunto cumulativo. Standards e Spec encerrados com zero achados pendentes.
- Nenhum teste de aplicação, PostgreSQL, Android, provedor, segurança ou desempenho foi executado: não existe implementação nesta etapa.

## Retomada e análise do prompt

O prompt da raiz autoriza concluir modelo, contrato, permissões, backlog e publicação documental; não autoriza implementação ou deploy. Setup, spec e tickets já produzidos foram preservados. A continuidade correta é fechar a revisão antes do envio, sem executar o backlog.

HEAD recebido: `63e033d93e081c812e1a2c99aa1de321481c2447`, árvore limpa. `origin` permanece no aplicativo Exameperto; consulta remota na retomada sem refs. Não há workflows locais.

Validações refeitas após ajustar a conciliação: OpenAPI 3.0.3 válido com `openapi-spec-validator 0.9.0`; 69 operações, 823 referências resolvidas, YAML sem duplicatas, operationIds/permissões/paths conferidos, 61 links locais existentes e 15 tickets com critérios/dependências sem ciclos. Sete diagramas passaram novamente em `mermaid.parse 12.0.0` com jsdom; sem inspeção visual. `git diff --check` passou. Ferramentas e scripts em `/tmp`, sem dependências adicionadas ao aplicativo.

## Pendências e próxima ação

Revisão técnica e primeiro push concluídos. Próxima ação humana: revisar o backlog concreto e decidir as políticas materiais dos tickets 01–02; autorizar explicitamente as próximas fatias antes de implementação. Política de retenção, comprovações, custeio, cancelamento, acesso privilegiado, representação e fornecedores continuam abertas, detalhadas nos artefatos correspondentes.

Push executado com sucesso para `https://github.com/Kayquebrigadeiro/Exameperto.git`, branch `docs/planejamento-tecnico`, até `84631aa4244120c8ddfa6dea00355fa8e8fde594`, após revisão. Este registro de fechamento segue em commit documental separado. Sem force push, workflows de deploy ou alterações de aplicação. Nenhum bloqueio técnico documental remanescente; políticas materiais e autorização da próxima etapa continuam pendentes.

## Rodada atual — proposta para os tickets 01–02

Base recebida: `629ad36a405bb4a290165ffdd08dae6ae789c03d`, árvore limpa; branch local e remota `docs/planejamento-tecnico` no mesmo SHA. Remoto conferido: `https://github.com/Kayquebrigadeiro/Exameperto.git`; nenhum workflow local de deploy. Os registros anteriores permanecem como histórico.

Produzida a [proposta de decisões pendentes](DECISOES-PENDENTES.md), com 12 decisões, recomendações/justificativas, impactos no produto/segurança/custo/implementação e dependências externas. Tickets 01–02 e índice de decisões apontam para a proposta; critérios não foram marcados, e o backlog continua `ready-for-human`. Regras anteriores reaproveitadas; LGPD e orientação da ANPD consultadas em fontes oficiais, sem declaração de conformidade ou homologação de fornecedor.

Modelo, OpenAPI, matriz de segurança e diagramas foram confrontados para registrar lacunas futuras (convite, custódia, garantia/remuneração, retenção e MFA); seus contratos e fluxos não foram alterados. Nenhuma aplicação, migração, contratação ou deploy foi iniciado.

Validações desta rodada: OpenAPI válido com `openapi-spec-validator 0.9.0`; YAML sem duplicatas, 69 operações, 823 referências resolvidas, permissões/paths conferidos, 80 links Markdown locais existentes e 15 tickets com critérios/dependências sem ciclos. Sete diagramas passaram em `mermaid.parse 12.0.0`, sem inspeção visual. `git diff --check` passou. Ferramentas temporárias preexistentes em `/tmp`, sem dependências adicionadas ao projeto. Nenhum teste de aplicação, provedor ou aparelho foi executado. Revisão local contra `629ad36` conferiu cobertura dos tickets, distinção entre proposta/lei/capacidade externa e lacunas frente ao modelo/API. Identificada pergunta redundante em D12 sobre bloqueios já estabelecidos; substituída por decisão aberta sobre o recorte do piloto. Conjunto cumulativo reconferido após a correção. Push da proposta e correção concluído até `80ed3a975d9c9426aa4d572ed0d221f642fdb004` em `origin/docs/planejamento-tecnico`, no remoto do aplicativo; SHA remoto conferido após envio. Este fechamento segue em commit documental separado, revisado antes do envio, sem force push ou deploy.

Bloqueios: decisão humana por ID; responsáveis institucionais/privacidade; unidades participantes; custeio real, tarifa e cláusulas de remuneração em falha; prazo financeiro validado; contratos, credenciais e homologações externas. Próxima ação: responder às perguntas da proposta, validar as dependências externas e então revisar os artefatos técnicos afetados. Implementação continua dependente de autorização posterior.

## Rodada D01–D12 — 30/09/2026

Verificação inicial: tarefa **parcial**, não concluída. HEAD/base `7a8077fa6a7b80e8ba642d3fe2838bd299859ba3`; seis documentos já modificados localmente, preservados e completados. Remoto `https://github.com/Kayquebrigadeiro/Exameperto.git`, branch `docs/planejamento-tecnico`; SHA remoto conferido igual à base. Sem aplicação/workflow de deploy.

Direções D01–D12 incorporadas em decisões, glossário/arquitetura, modelo, segurança, OpenAPI 0.2.0, diagramas, spec e dependências dos tickets. Convite adulto com aceite/confirmação, evidência mínima/reuso/recurso independente, proxy reautorizado, unidade/protocolo/custódia, apuração de cancelamento e cobertura de retorno explícitos. Particular separado da dependência de benefício/aporte; cadastro/segurança sem programa obrigatório. Políticas e integrações continuam com guardas distintas, sem valores, responsáveis ou contratos fictícios.

Criada proposta [03A](../.scratch/planejamento/issues/03a-cadastro-local.md): formulário web/REST e PostgreSQL local isolado, validação, negação de privilégios e bloqueio de cadastro por ausência de e-mail sem efeitos parciais. Critérios concretos disponíveis para autorização; cadastro completo permanece em 03. Nenhuma implementação, compra, dado real ou deploy executado.

Skills domain-modeling, to-spec e to-tickets consultadas em `/tmp/exame-skills`, SHA `6654f6b60cd9d5be8b54c6fafe44346dabeb3b76`; setup preservado, sem instalação permanente. Preferências explícitas do usuário mantêm tracker local e `ready-for-human`. Ferramentas temporárias de validação em `/tmp`, sem dependências do aplicativo.

Validações: OpenAPI 3.0.3 válido com openapi-spec-validator 0.9.0; YAML sem duplicatas, 77 operações únicas, 910 referências resolvidas, paths/permissões conferidos, 86 links locais existentes e 16 tickets com critérios/dependências sem ciclos. Erro de sintaxe de sequência Mermaid causado por ponto e vírgula identificado e corrigido; dez diagramas passaram em mermaid.parse 12.0.0 com jsdom, sem renderização/inspeção visual. `git diff --check` passou. Não houve testes de aplicação, PostgreSQL, aparelho ou provedor, pois esta etapa é documental.

Próxima ação desta rodada: commit local, revisão do conjunto contra a base capturada e push documental. Bloqueios operacionais: documentos/critérios profissionais, valores/financiador/modelo comercial, unidades/protocolo/cobertura real, responsáveis, retenção validada e prazo financeiro, procedimentos/fatores MFA, provedores e ensaios GPS. Próxima autorização solicitável: somente implementação da fatia 03A; deploy posterior. Resultado da revisão e envio será registrado no fechamento.

### Fechamento D01–D12

Commit inicial `543279c`; revisão code-review em Standards e Spec, com delegação explicitamente autorizada no prompt. Standards apontou P1 no encerramento de custódia por entrega normal: corrigido em `c498e90`, distinguindo evento estruturado de recebimento por código de prova documental de retorno. Revisão local também completou vínculo/autorização das evidências operacionais, leitura de verificações reutilizadas, aceite de política pelo entregador e erros de versão. Ambos os revisores reconferiram o conjunto: zero achados materiais pendentes. Evidências detalhadas em REVISAO.

Validações finais após correções: OpenAPI válido; 77 operações, 917 referências resolvidas, YAML sem duplicatas, paths/permissões válidos, 87 links locais existentes, 16 tickets sem ciclos e dez diagramas Mermaid válidos. `git diff --check` cumulativo passou; sem inspeção visual ou testes de aplicação/aparelho/provedor.

Push concluído para `origin/docs/planejamento-tecnico`, remoto do aplicativo confirmado, até `c498e90e5f31aa554bbf21367ab171dcd9c84df3`; SHA remoto conferido após envio. Este registro final e REVISAO seguem em commit documental de fechamento, com revisão local antes de envio, sem force push ou deploy. Tarefa documental concluída; políticas/integrações reais continuam pendentes. Próxima ação humana: autorizar, se desejado, somente 03A conforme seus critérios de aceite; não reabrir D01–D12 como decisões técnicas ainda sem resposta.

## Implementação 03A — 30/09/2026

Autorização recebida a partir de `67f5e113241dcec733a46c5bdc47e1730f8b2401`. Implementada a primeira fatia local: backend Spring Boot 3.5.16/Java 21 com Flyway/PostgreSQL, fronteira REST `POST /api/v1/auth/register`, validação server-side, rejeição de propriedades desconhecidas (`role`, `status` e equivalentes), limite de tentativas, CORS de origem definida e respostas seguras. A operação não possui sucesso falso: sem política de privacidade validada retorna `422 POLICY_UNDEFINED`; com política de teste mas sem adaptador real de e-mail retorna `503 INTEGRATION_UNAVAILABLE` antes de qualquer escrita. Nenhum usuário, sessão, desafio ou outbox é criado nesse caminho.

Implementado frontend React/TypeScript/Vite com formulário acessível, mensagens compreensíveis, estado ocupado e feedback de indisponibilidade/falha sem afirmar cadastro ou envio. Configuração local do PostgreSQL está em [infra/compose.yaml](../infra/compose.yaml); o banco usa somente credencial de desenvolvimento local e não é configuração de produção. O adaptador de e-mail desta fatia é explicitamente indisponível; não há simulador de entrega, seed ou conta de demonstração.

Verificações executadas: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -DskipTests compile` passou; a suíte Maven passou com Testcontainers/PostgreSQL 17.6, HTTP real e bancos descartáveis, verificando `422 POLICY_UNDEFINED` e `503 INTEGRATION_UNAVAILABLE` sem linhas em `usuario`, `sessao`, `desafio_conta` e `outbox`, além de `400` para tentativa de privilégio sem persistência e limite aplicado antes das decisões. `npm run build --prefix web` passou; Playwright passou em duas verificações: apresentação do erro com POST interceptado e Axe sem violações básicas. A integração navegador→backend real e a validação visual do formulário ainda não foram ensaiadas. A primeira execução de Playwright foi bloqueada pelo sandbox/porta 5173 e repetida em porta local livre autorizada. Nenhum dado pessoal real foi usado.

Limitações: não há caminho de cadastro bem-sucedido, confirmação de e-mail, login ou recuperação nesta fatia; isso permanece no ticket 03. Teste de entrega real de e-mail não foi executado nem alegado. Docker/Testcontainers foi usado somente com dados sintéticos descartáveis. Não houve deploy.

Próxima ação proposta: ticket 03 — completar confirmação de e-mail, sessão e recuperação com provedor real configurado e testes correspondentes, mediante nova autorização; não iniciado.

Fechamento 03A: revisão em dois eixos contra `67f5e11` confirmou zero achados materiais após corrigir a confiança em `X-Forwarded-For`, restringir o Compose ao loopback, tornar a senha local configurável, aplicar limite antes das decisões e verificar o bloqueio de política pela fronteira REST. O conjunto foi commitado em `142f97f39f05e939abf33baec47a67fc3e21af87` e enviado para `origin/docs/planejamento-tecnico`; SHA remoto conferido. Sem deploy.

## Ticket 03 — implementação autorizada e retomada

Base conferida: `604487b9040d78aff89a1e68c2edc6f079a39a89`, HEAD recebido nessa base, branch `docs/planejamento-tecnico`, remoto `https://github.com/Kayquebrigadeiro/Exameperto.git`; SHA remoto inicialmente igual. Havia alterações locais incompletas de autenticação, preservadas e corrigidas. Nenhum workflow de deploy local. Autorização atual abrange implementação, commits/revisão e push desta fatia; não autoriza deploy nem próximo ticket.

Não havia provedor de e-mail configurado nas variáveis relevantes do processo ou arquivos locais de ambiente. Implementado adaptador SMTP real, desabilitado por padrão; requisitos exatos e procedimento de homologação em [CONTA-EMAIL](CONTA-EMAIL.md). Nenhuma mensagem real enviada, serviço contratado ou credencial incluída no Git/chat. Privacidade permanece bloqueada por padrão.

Implementados confirmação/reenvio com tokens aleatórios de 256 bits, hash/prazo/consumo único; sessão JWT com consulta de revogação no banco, refresh rotativo e revogação da família em replay/logout; recuperação genérica em fila sem tokens persistidos e troca de senha com revogação de todas as sessões. Locks de conta serializam consumo/renovação/reset; revogação por replay persiste apesar do 401. Origin/CSRF web, separação mobile/cookie, limites de IP/conta, cifra e HMAC com chaves externas independentes. Cadastro preserva rollback sem efeitos parciais na ausência/falha SMTP e bloqueio por política de 03A. Web contém os fluxos e explicita que e-mail confirmado não equivale a identidade, elegibilidade ou entregador/veículo verificados.

Verificações executadas nesta rodada:

- Suíte `mvn -f backend/pom.xml test`: 16 testes executados, zero falhas; BrowserFlowTest é opt-in e fica ignorado nessa execução. PostgreSQL 17.6/Testcontainers real, bancos sintéticos descartáveis. Confirmação e recuperação concorrentes/expiradas/reutilizadas, refresh concorrente/reutilizado/expirado, access expirado, logout, reset versus refresh, CSRF inválido/ausente e Origin, reenvio limitado, rate limit e rejeição de privilégios. Falha de transporte e respostas genéricas verificadas; testes 03A preservados.
- `PersistenceRestartTest`: encerrou e reiniciou aplicação contra o mesmo PostgreSQL isolado; conta confirmada continuou autenticável. `SmtpFailureTest`: adaptador JavaMail real contra conexão local indisponível retornou 503, sem conta/desafio/sessão/outbox parcial.
- Build TypeScript/Vite passou. Dois testes Playwright de apresentação do erro e Axe passaram; o primeiro intercepta o POST e não comprova integração.
- `mvn -f backend/pom.xml -Dtest=BrowserFlowTest -DbrowserTest=true test`: passou com um ensaio Chromium → Vite → backend HTTP real → PostgreSQL descartável, sem interceptar API. Cadastro, confirmação, login web, cookie Secure/HttpOnly, renovação, logout, recuperação, nova senha e indisponibilidade passaram. Fronteira de e-mail substituída exclusivamente em código de teste, com capturas temporárias removidas no fechamento. Não é homologação de entrega real.
- OpenAPI válido; 79 operações, 925 referências internas resolvidas, YAML sem duplicatas, 94 links locais verificados. Diagramas Mermaid e revisão final registrados no fechamento. `git diff --check` passou.

Durante a retomada foram corrigidos caminho duplicado no teste existente, rollback indevido da revogação, ausência de serialização e CSRF superficial. No primeiro ensaio de navegador, a herança de stdout do subprocesso corrompeu o protocolo do Surefire apesar do teste passar; saída foi redirecionada a artefato ignorado e a execução repetida com sucesso. Contextos Spring agora fecham antes de remover bancos de teste, evitando consultas do agendador após teardown. Nenhum desses ensaios utilizou dados de pessoas reais.

Bloqueio externo: SMTP **ainda não homologado**. Faltam configuração privada, remetente autorizado, política pertinente validada, endereço controlado pelo autor e autorização específica para envios. 202 de recuperação/reenvio significa solicitação recebida, não e-mail enviado; falhas ficam RECONCILIAR. Aceitação SMTP tampouco comprova recebimento. Limites por processo, retenção/expurgo, rotação operacional de chaves e observação de bounces estão explicitados em CONTA-EMAIL; não há declaração de prontidão para produção.

### Fechamento do ticket 03 — 02/10/2026

Revisão local contra `604487b` concluída sem achados materiais pendentes; critérios verificáveis do ticket foram marcados. A suíte Maven foi repetida em Java 21 com PostgreSQL 17.6/Testcontainers: 17 testes descobertos, 16 executados com sucesso e o `BrowserFlowTest` opt-in ignorado nessa execução. Build web passou; Playwright comum teve dois testes aprovados e o ensaio completo ignorado por desenho. O ensaio opt-in foi então executado separadamente e passou: Chromium → Vite → backend HTTP → PostgreSQL real descartável, sem interceptação da API da aplicação.

OpenAPI 3.0.3 passou em `openapi-spec-validator 0.7.2`; YAML sem chaves duplicadas, 79 `operationId` únicos, 925 referências internas resolvidas e 96 links Markdown rastreados existentes. Onze blocos Mermaid passaram em `mermaid.parse` 12.0.0 com jsdom, sem inspeção visual. `git diff --check` passou. Todos os dados dos testes foram sintéticos e isolados.

Bloqueio remanescente: entrega real de e-mail não homologada. Para o ensaio próprio ainda são necessários provedor SMTP escolhido, host/porta, credencial privada, remetente verificado, três chaves privadas independentes, política pertinente habilitada, origem web, PostgreSQL autorizado, endereço controlado pelo autor e autorização específica do envio. Valores permanecem fora do Git e do chat. Aceitação SMTP e recebimento devem ser comprovados separadamente conforme [CONTA-EMAIL](CONTA-EMAIL.md).

Próxima ação permitida: somente configurar e autorizar o ensaio de e-mail real do ticket 03. Ticket 04 e deploy não foram iniciados.

### Complemento de validação do ticket 03 — 02/10/2026

O teste Maven opt-in antes contabilizado como ignorado foi identificado como `BrowserFlowTest`. Ele não é cobertura separada do Playwright opt-in: orquestra PostgreSQL/Testcontainers, backend real e Vite, então executa `web/tests/account-real.spec.ts`. O spec Playwright se ignora fora desse ambiente quando `TEST_MAILBOX` não foi fornecido. Comando e dependências estão documentados em [CONTA-EMAIL](CONTA-EMAIL.md).

Criado `./scripts/verify-ticket-03.sh` como entrada única para instalar dependências web do lockfile, executar suíte Maven, build, Playwright de UI, ensaio integrado com API/PostgreSQL reais e conferir o pacote de produção. O script remove variáveis SMTP do seu processo e não faz envio externo. A confirmação e a recuperação do ensaio usam `@MockBean EmailGateway` em `src/test`, arquivos temporários `confirmation`/`recovery` e variável `TEST_MAILBOX` entregue apenas ao subprocesso Playwright. Não há capturador, endpoint auxiliar ou retorno de token em `src/main`; o diretório é apagado ao final e o JAR é verificado contra classes/identificadores de teste.

O comando completo foi executado nesta rodada e passou: suíte Maven com 17 testes descobertos, 16 aprovados e o opt-in ignorado; build web; dois testes Playwright de UI aprovados; `BrowserFlowTest` opt-in executado separadamente com um teste aprovado e nenhum ignorado; pacote Spring Boot gerado e inspecionado sem classes ou identificadores do mecanismo de captura. A execução usou dados sintéticos, Docker local e PostgreSQL 17.6 descartável. Nenhuma conexão SMTP externa foi tentada.

Validação documental final: OpenAPI 3.0.3 válido; 98 links Markdown locais rastreados existentes; onze blocos Mermaid válidos em `mermaid.parse` 12.0.0; `bash -n` no novo script e `git diff --check` aprovados. A primeira invocação do validador Mermaid falhou somente na preparação temporária do jsdom sob Node.js 24; a atribuição incompatível foi removida e os onze blocos passaram sem alteração do repositório.

Próximo ticket lido, sem implementação: [04 — paciente concede e revoga representação](../.scratch/planejamento/issues/04-paciente-familiar.md). Escopo: perfil de paciente com identidade pendente e autorização temporária/revogável de familiar adulto por convite privado, aceite do destinatário, confirmação reautenticada do paciente e escopos explícitos. Aceite sozinho não concede acesso; familiar não delega; identidade/CPF não aprova benefício; menores e representação legal permanecem fora do recorte.

Critérios previstos: persistência/API/UI do perfil e das concessões; expiração e seleção segura da conta; negação de paciente alheio, autoconcessão, delegação e concessão expirada/revogada; ponto efetivo e teste PostgreSQL da revogação concorrente; consumo único do convite e perda de acesso REST/STOMP após revogação. Dependências: ticket 03 localmente validado, decisão humana do ticket 01 sobre responsável e prazos do convite/concessão, política de dados reais e canal privado habilitado para a entrega do convite.

Sem SMTP homologado podem avançar, após autorização própria do ticket 04: migrações e estado do perfil/convite/concessão; autorização por titular/registro/escopo; listagem e revogação; bloqueios por política/canal ausente; UI; e testes isolados de expiração, uso único, aceite sem concessão e concorrência. O envio real do convite e sua homologação permanecem bloqueados pelo canal privado. Nenhuma parte do ticket 04 foi implementada nesta rodada.

## Ticket 04 — implementação autorizada e fechamento — 02/10/2026

Autorização recebida para implementar a partir de `b33acfa19655cc77fd20315c429e732994813732`, na branch `docs/planejamento-tecnico`, sem deploy e sem iniciar o ticket 05. A implementação local foi concluída preservando `referencia.md` fora do Git.

Implementados perfil mínimo de paciente com identidade pendente e atualização exclusiva do titular; convite privado vinculado ao destinatário por HMAC, token aleatório com somente o hash persistido, expiração e uso único; aceite pela própria conta autenticada; confirmação do paciente com reautenticação; escopos explícitos, prazo máximo, revogação imediata e reavaliação no banco em cada operação protegida. A interface web permite consultar e administrar perfil, convites e concessões com mensagens acessíveis, sem expor token/link. Falha ou indisponibilidade do canal retorna `503 INTEGRATION_UNAVAILABLE` e não deixa convite utilizável nem concessão ativa.

Os pontos de integração futuros (pedidos, benefícios, documentos e assinaturas/STOMP) estão documentados em arquitetura, segurança e diagramas; nenhum módulo futuro foi presumido como implementado. Parentesco, idade, deficiência, menores e representação legal não concedem acesso neste recorte.

Evidências de validação:

- `./scripts/verify-ticket-04.sh` passou integralmente: suíte Maven com 25 testes descobertos (23 na execução principal e 2 opt-in executados separadamente), zero falhas; build web; dois testes Playwright de UI; fluxo integrado do ticket 03; fluxo integrado do ticket 04; empacotamento e inspeção do JAR sem classes/identificadores do capturador.
- `RepresentationFlowTest` passou com 7 testes, incluindo acesso indevido, destinatário divergente, convite expirado/reutilizado/encaminhado, concorrência, escopos e revogação com sessão aberta. O fluxo de navegador usou Chromium → Vite → backend HTTP real → PostgreSQL 17.6/Testcontainers, com dados sintéticos e sem interceptação da API.
- `contracts/openapi.yaml` passou em `openapi-spec-validator 0.9.0`; `bash -n` dos scripts e `git diff --check` passaram. O capturador de convite/confirmação existe somente em `src/test`, usa caixa temporária descartável e não homologa entrega externa de e-mail.

Limitações e bloqueios: SMTP real continua desabilitado e não homologado; não houve envio externo, deploy ou uso de dados pessoais reais. A próxima ação humana, se desejada, é revisar/homologar o canal privado de e-mail conforme `docs/CONTA-EMAIL.md`; não iniciar o ticket 05.

## Ticket 05 — implementação parcial e fechamento da rodada — 02/10/2026

Autorização recebida a partir de `966c0b2ef74e3bc0dbbaa1e1ebd3cc38f9a02354`, na branch `docs/planejamento-tecnico`, preservando `referencia.md`, sem deploy e sem iniciar o ticket 06.

Implementados: app Expo/React Native do entregador; cadastro persistido em `RASCUNHO`; migração V4 para entregador, documentos, histórico e revisões; API multipart autenticada; armazenamento privado local configurável; quarentena, nomes/chaves gerados, SHA-256, limite de 10 MiB e validação por assinatura real de PDF/JPEG/PNG; substituição com nova revisão e histórico mínimo; download apenas por proxy autenticado, `no-store`, após estado `INSPECAO_APROVADA`; painel web para cadastro, upload e acompanhamento. Cadastro, inspeção e aprovação profissional permanecem estados distintos.

O analista só consulta a fila com papel nominal `ANALISTA_OPERACIONAL`; atribuição/decisão exigem MFA verificado no servidor. Como o verificador MFA, a inspeção oficial, os critérios profissionais e integrações externas não estão disponíveis, essas ações permanecem bloqueadas (`MFA_REQUIRED`/`POLICY_UNDEFINED`), sem aprovação simulada, conta administrativa padrão ou revisor fictício. Foto operacional não é promovida automaticamente.

Evidências:

- `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 test`: 25 testes existentes, zero falhas, 2 opt-in ignorados; migração V4 validada em PostgreSQL 17.6/Testcontainers.
- `DelivererEvidenceFlowTest` passou com PostgreSQL/Testcontainers, HTTP real e armazenamento temporário isolado: cadastro, upload real, quarentena, proxy bloqueado/aprovado, acesso indevido por ID, tipo inválido, arquivo excessivo, substituição, fila e bloqueio MFA.
- `PrivateObjectStoreTest` passou escrevendo, lendo e removendo objeto real com chave gerada. `npm run build --prefix web`, compilação Maven, OpenAPI 3.0.3 e `git diff --check` passaram.
- Não houve execução em emulador Expo nem aparelho físico; o app foi compilado/inspecionado como código, e não se declara validação mobile ponta a ponta. Dados de documentos foram sintéticos e isolados.

Conclusão: ticket 05 **parcial**, não pronto para aprovação operacional. Dependências para concluir: armazenamento oficial escolhido/homologado, mecanismo de inspeção, critérios profissionais publicados, analista real atribuído, MFA verificável e política de retenção/expurgo. Não apresentar a análise local como consulta oficial. Não iniciar ticket 06 nem deploy.

## Ticket 05 — continuidade técnica autorizada — 05/10/2026

Base `fd670c8` confirmada em `docs/planejamento-tecnico`, inicialmente igual a `origin/docs/planejamento-tecnico`; remoto do aplicativo confirmado. `referencia.md` permaneceu fora do Git. Ticket 06 não foi iniciado; não houve deploy, contratação ou envio externo de mensagens.

Entregas técnicas: migração V5 e TOTP do AO com segredo cifrado, confirmação/anti-reuso, cinco falhas em 15 minutos, elevação de cinco minutos vinculada à sessão e revogação efetiva; atribuição e inspeção estrutural exigem MFA na própria API. O teste cobre chamada direta sem MFA (403), acesso por conta comum (403), decisão pelo proprietário (403), bloqueio profissional por política ausente e chamada após logout (401); o status desse bloqueio foi alinhado para 422 na consolidação abaixo. Não existe bypass, recuperação automática, conta privilegiada padrão ou analista criado em produção; contas/papéis sintéticos aparecem somente no banco descartável de teste.

A inspeção estrutural local verifica fechamento mínimo de PDF/JPEG/PNG e recursos ativos conhecidos de PDF. Ela é uma inspeção limitada de segurança do **arquivo**, não comprovação de autenticidade do **documento** e não aprovação **profissional**. A decisão profissional continua invariavelmente em `POLICY_UNDEFINED` até existirem critérios publicados e responsável real; o status, antes registrado como 503 nesta rodada histórica, foi corrigido para 422 na consolidação abaixo. Um fornecedor de autenticidade não foi presumido: integração externa só passa a ser obrigatória se um requisito concreto for aprovado.

Armazenamento local: raiz/quarentena isoladas, chave gerada e caminho normalizado, traversal/symlink recusados, diretórios `0700` e objetos `0600` em POSIX, proxy com autorização por proprietário e `no-store`, além de exclusão física testada. O ensaio local comprova esse adaptador, não operação externa, backups/restauração, retenção/expurgo ou varredura antimalware abrangente. Ausência de objeto externo não invalida o teste local.

Classificação das pendências:

- **Implementação ausente:** decisão profissional/transições de aprovação, projeção de foto aprovada, processo de autenticidade documental e orquestração de retenção/expurgo; provisionamento/recuperação independente de papéis privilegiados.
- **Implementação existente, não testada em dispositivo:** app Expo, SecureStore/refresh, seleção e upload nativos, erros de rede, quarentena e acompanhamento visual. Typecheck/export não substituem esse ensaio. `npm audit --omit=dev --prefix mobile` encontrou 29 alertas transitivos (21 altos, 8 moderados, 0 críticos); as correções sugeridas implicam mudanças incompatíveis de Expo/React Native, portanto não foram aplicadas sem migração e ensaio em dispositivo.
- **Dependência externa para operação real:** critérios profissionais aprovados e responsáveis reais; infraestrutura/backup/restauração/antimalware conforme requisitos de deploy; eventual fonte oficial de autenticidade apenas se formalmente exigida. SMTP continua dependência do cadastro real, sem afetar os ensaios sintéticos.

Evidências desta rodada:

- `./scripts/verify-ticket-05.sh`: passou integralmente. A suíte comum descobriu 28 testes, executou 26 com sucesso e ignorou os 2 opt-in; esses dois foram então executados explicitamente, 1/1 aprovado em cada comando, sem skips. Também passaram build web, 2 testes Playwright, typecheck móvel, export Android (568 módulos, bundle de 1,77 MB) e package Maven.
- `mvn -f backend/pom.xml -Dtest=DelivererEvidenceFlowTest test`: 2 testes, 0 falhas/erros/skips, com PostgreSQL 17.6, API HTTP e filesystem reais isolados; inclui limite persistente de MFA, sessão/revogação, upload/erros/quarentena/inspeção/acesso e bloqueio profissional.
- Opt-in de conta: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -Dtest=BrowserFlowTest -DbrowserTest=true test`; finalidade Chromium → Vite → API → PostgreSQL para conta/sessão; 1 executado, 0 falhas, 0 ignorados.
- Opt-in familiar: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -Dtest=FamilyBrowserFlowTest -DbrowserTest=true test`; finalidade autorização/revogação com sessão aberta pelo mesmo caminho; 1 executado, 0 falhas, 0 ignorados.

Não havia `adb`, `emulator` ou `xcrun` no ambiente. O app passou em TypeScript e export Android, mas não foi instalado/executado; o roteiro reproduzível está em `mobile/README.md` e a validação mobile → API → PostgreSQL → armazenamento permanece pendente. O fluxo API → PostgreSQL → armazenamento foi exercitado separadamente e não será apresentado como ponta a ponta móvel.

Fechamento: implementação, testes, contrato e documentação foram revisados no commit `d632eeb698cdbb29612972a837b77c3c744b6fa4`, enviado para `origin/docs/planejamento-tecnico`; o SHA remoto foi conferido. Sem force push ou deploy. Próxima ação técnica do ticket 05: migrar as dependências Expo/React Native com os alertas do audit e executar o roteiro em emulador/aparelho. A conclusão operacional continua dependente de critérios profissionais e responsáveis reais; manter a decisão bloqueada. Não iniciar o ticket 06.

## Ticket 05 — dependências mobile e consolidação — 05/10/2026

Base recebida `fd186a4`, igual a `origin/docs/planejamento-tecnico`; remoto do aplicativo e branch conferidos. `referencia.md` permaneceu fora do Git. Não houve deploy, distribuição ou início do ticket 06.

Dependências mobile migradas incrementalmente conforme documentação oficial: Expo SDK 53 → 54 → 55 → 56 → 57. O estado final usa Expo 57.0.26, React Native 0.86.3 e módulos compatíveis. A opção removida `newArchEnabled` saiu do `app.json`; SDK 55+ exige a Nova Arquitetura. O doctor no SDK 56 detectou a regressão conhecida do Hermes e recomendou SDK 57, que passou em 21/21 verificações. O lockfile npm v3 foi regenerado por instalação normal, sem `npm audit fix --force`, `--legacy-peer-deps` ou overrides.

O [relatório de dependências mobile](AUDITORIA-DEPENDENCIAS-MOBILE.md) classifica individualmente os 21 nós altos e 8 moderados iniciais. Eram dois nós diretos (`expo`, `react-native`) e 27 transitivos; os advisories alcançavam o toolchain de CLI/Metro/prebuild/Jest, sem caminho identificado ao JavaScript executado pelo usuário final. Isso é análise de exposição, não prova de inexplotabilidade. O audit final reporta 23 nós (16 altos, 7 moderados, zero críticos) que convergem para `braces`, `node-forge` e `uuid`; não há versão corrigida compatível indicada. Os alertas permanecem abertos e a sugestão automática de downgrade para Expo 44/RN 0.72 foi rejeitada por incompatibilidade, não tratada como correção.

`POLICY_UNDEFINED` foi alinhado à semântica transversal: decisão profissional sem critérios/responsáveis agora retorna `422` na implementação, contrato e teste. `503 INTEGRATION_UNAVAILABLE` continua reservado à capacidade técnica externa/integração indisponível. A decisão profissional continua invariavelmente bloqueada; nenhuma aprovação foi simulada.

Evidências desta rodada:

- `./scripts/verify-ticket-05.sh` passou integralmente: suíte comum com 28 testes descobertos, 26 aprovados e 2 opt-in ignorados; os opt-in foram executados separadamente com 1/1 aprovado cada. Também passaram build web, 2 testes Playwright, typecheck/export Android e package Maven.
- `npm ci` no mobile concluiu instalação reproduzível de 494 pacotes; `npx expo install --check` informou dependências atualizadas; `npx expo-doctor` passou 21/21.
- `npm run typecheck` passou com TypeScript 6.0.3. `expo export --platform android` passou com 581 módulos e bundle Hermes de 1,4 MB. Export não equivale a instalação ou uso.
- `DelivererEvidenceFlowTest`: 2 testes, zero falhas/erros/skips, com PostgreSQL 17.6/Testcontainers, HTTP e filesystem reais isolados; confirmou `422 POLICY_UNDEFINED`. A primeira tentativa foi impedida pelo socket Docker no sandbox e repetida com acesso local autorizado.
- `npm audit --omit=dev --json`: 16 altos, 7 moderados, zero críticos remanescentes, documentados sem supressão.
- `contracts/openapi.yaml` passou em `openapi-spec-validator 0.7.2`; links locais alterados, `bash -n` e `git diff --check` passaram.

Não há `adb`, `emulator` ou `xcrun` neste ambiente. O fluxo instalado UI móvel → API → PostgreSQL → armazenamento permanece pendente; [o roteiro Android](../mobile/README.md) agora inclui pré-requisitos, development build, cenários de SecureStore/upload/rede/refresh/logout e evidência mínima. A inspeção estrutural continua descrita somente como inspeção limitada de segurança do arquivo, não autenticidade documental nem varredura antimalware.

Pendências consolidadas: ensaio em dispositivo; critérios e responsável profissional reais; decisão/transições e foto operacional projetada; decisão de requisito/fornecedor para autenticidade; retenção/expurgo; armazenamento, backup, restauração e antimalware de operação; provisionamento/recuperação privilegiados; e atualização upstream dos três advisories do toolchain. A aprovação profissional e a conclusão operacional permanecem bloqueadas. Próxima ação: executar o roteiro Android na máquina do autor e resolver as políticas/dependências operacionais antes de distribuição. Não iniciar ticket 06 nem deploy.

Fechamento Git: o conjunto foi commitado localmente em `6c6575a`, revisado contra `fd186a4` e enviado com sucesso para `origin/docs/planejamento-tecnico`; a ponta remota anterior foi confirmada em `fd186a4`. A revisão cumulativa confirmou lockfile/manifests coerentes, contrato/teste/implementação em 422 e ausência de alterações em `referencia.md`. Este registro de resultado segue em commit documental separado, também sem force push ou deploy.

## Ticket 06 — implementação parcial autorizada — 05/10/2026

Base autorizada: `caff6af42346c286b3668b60e1046db48b559b59`, branch `docs/planejamento-tecnico`; `referencia.md` foi preservada sem alteração. Implementadas migração V6 e API pública de veículos/vínculos: placa, marca/modelo, cor, anos, tipo próprio/alugado/autorizado, validade, CRLV, fotos e autorização de uso. O veículo não é presumido como propriedade do entregador. A API restringe leitura/escrita a vínculos próprios, rejeita campos privilegiados por contrato estrito, rejeita reutilização de documento de outra conta e aplica versão otimista `412`.

Documentos reaproveitam `documento` e `PrivateObjectStore`: quarentena, nomes/chaves privadas, inspeção estrutural, proxy `no-store` e download autorizado por revisão. `vinculo_documento` mantém substituições; cada troca bloqueia revisão pendente/atribuída e cria snapshot corrente em `revisao_vinculo_*`. Atribuição e inspeção de AO exigem MFA, impedem autoanálise e não liberam ofertas. Decisão continua `422 POLICY_UNDEFINED`; `503 INTEGRATION_UNAVAILABLE` permanece reservado à integração indisponível. O contrato OpenAPI, modelo, segurança, arquitetura e diagrama foram alinhados; não há consulta oficial simulada nem mecanismo paralelo de aprovação.

Validações: compilação Maven, build web, typecheck mobile, export Android e YAML OpenAPI passaram. O novo `VehicleLinkFlowTest` cobre interfaces HTTP de contas distintas, documento de terceiro, propriedades privilegiadas, concorrência, substituição, revisão obsoleta, MFA, download autorizado, decisão bloqueada e logout. A execução não pôde iniciar porque o ambiente não fornece Docker/Testcontainers (`Could not find a valid Docker environment`); portanto os resultados de integração PostgreSQL são pendentes para a máquina do autor. Não há `adb`, emulador ou `xcrun`; export não substitui instalação/uso e o fluxo mobile ponta a ponta segue pendente.

Pendências preservadas do ticket 05: ensaio em dispositivo/emulador; aprovação profissional e responsáveis/critério; dependências mobile remanescentes (16 altos/7 moderados no audit final, convergindo para `braces`, `node-forge` e `uuid`); retenção/expurgo, storage/backup/restore e antimalware operacional; eventual fornecedor oficial somente se requisito for aprovado. Ticket 06 fica parcial e aprovação operacional bloqueada. Não iniciar ticket 07, distribuir aplicativo, contratar ou fazer deploy.

### Fechamento de verificação PostgreSQL do ticket 06 — 05/10/2026

Docker foi verificado disponível (`28.5.2+dfsg4`) e o Testcontainers encontrou o socket Unix local. `mvn -q -Dtest=VehicleLinkFlowTest test` executou PostgreSQL 17.6 descartável com HTTP real, Flyway, filesystem privado e contas sintéticas: **2 testes, 0 falhas, 0 erros, 0 skips**.

Além do fluxo público de vínculo, o teste confirmou acesso/alteração entre contas, documento de terceiro rejeitado, `412 VERSION_MISMATCH` sem sobrescrita, histórico de substituição, snapshots e revisão obsoleta, atribuição/MFA/autoanálise, download autorizado, logout e `422 POLICY_UNDEFINED`. O segundo caso migrou schema vazio até V5, executou V6 e confirmou `vinculo_veiculo` e Flyway em V6; o schema público da aplicação também foi criado do zero e aplicado até V6. O container foi descartado pelo lifecycle do Testcontainers.

O teste mobile em dispositivo/emulador permanece uma pendência independente, assim como os alertas de dependências mobile, critérios/responsável profissional e demais bloqueios operacionais do ticket 05. Nenhum build amplo foi repetido, não houve deploy, distribuição, liberação de ofertas ou início do ticket 07. `referencia.md` permanece fora do Git.

## Ticket 07 — implementação parcial autorizada — 06/10/2026

Base `a7443bf` confirmada na branch `docs/planejamento-tecnico`; `referencia.md` preservada fora do Git. V7 adiciona instituição/política versionada, solicitação com snapshot, dimensões independentes IDADE/DEFICIENCIA/RENDA, evidência privada com substituição/histórico e revisões inicial/recurso. A API exige paciente ou familiar com BENEFICIOS vigente; fila e decisão exigem membro da instituição, atribuição, MFA e impedem autoanálise/revisor do recurso anterior. Política ausente retorna `422 POLICY_UNDEFINED`; não há percentuais, documentos aceitos ou responsáveis reais ativos e decisão não reserva financiamento.

`BenefitFlowTest` passou com PostgreSQL 17.6 descartável via Docker/Testcontainers: HTTP real → Spring Boot → Flyway V1–V7 → PostgreSQL, 1 teste, 0 falhas/erros/skips. Cobriu acesso entre pacientes, escopo familiar revogado/expirado, documento de outra conta, conflito `412` sem sobrescrita, substituição/histórico, MFA/atribuição/autoanálise, recurso com segundo analista e bloqueio `422`. `BenefitCalculatorTest` cobre cálculo sintético, limite 100 e arredondamento. Não substitui homologação de critérios profissionais.

O painel web ganhou acompanhamento/criação explícita de solicitação e `npm run build --prefix web` passou. `BrowserFlowTest -DbrowserTest=true` passou (navegador → API → PostgreSQL); o cenário de benefício usa política UUID sintética e não constitui homologação de elegibilidade.

Contrato, modelo, segurança, arquitetura, diagramas e ticket foram alinhados. Pendentes: teste em dispositivo mobile; critérios/responsável profissional, integrações, armazenamento/expurgo e alertas do ticket 05. Aprovação profissional/operacional permanece bloqueada; ticket 08 não iniciado, sem distribuição, contratação ou deploy.

## Ticket 08 — implementação parcial autorizada — 06/10/2026

Base `2299e5e` confirmada na branch `docs/planejamento-tecnico`; `referencia.md` preservada fora do Git. V8 adiciona referência administrativa de programa, aporte BRL com valor exato, evidência privada, estados PENDENTE/CONFIRMADO/REJEITADO, idempotência, auditoria e lançamentos confirmados. A aplicação não cria instituição/programa, não semeia saldo e não consulta banco externo.

Gestores financeiros exigem papel global nominal, vínculo ativo à instituição e MFA. Registro exige Idempotency-Key e permanece pendente. Revisão exige outro GF da mesma instituição, MFA, `If-Match`, evidência de conciliação própria aprovada e motivo; confirmação cria exatamente um lançamento. Disponibilidade soma somente `lancamento_aporte`; rejeições e pendências não alteram saldo.

`FundingFlowTest` passou com PostgreSQL 17.6/Testcontainers e HTTP real: isolamento entre instituições, MFA/papel, auto-confirmação, evidência de outra conta, valores inválidos, versão obsoleta, rejeição, replay e concorrência sem duplicação. `FundingBrowserFlowTest` também passou: Chromium → Vite → API real → PostgreSQL 17.6 descartável, com armazenamento privado temporário e sem interceptação; registrou/listou aporte pendente, confirmou disponibilidade zero, trocou para outro GF, exigiu MFA, inspecionou conciliação e confirmou, atualizando o painel para `CONFIRMADO`/`100.5`. O painel permite a revisão prevista no contrato. Automação de navegador é evidência funcional, não verificação visual manual ou acessibilidade; estas permanecem não executadas.

Permanecem bloqueados: responsáveis e procedimento financeiro reais, acordo/programa habilitado por governança, comprovação de transferência, eventual integração bancária, retenção/expurgo e liberação de subsídio. Pendências do ticket 05 e teste mobile continuam separadas. Sem movimentação externa, contratação, deploy ou ticket 09.

Ticket 09 permanece **não iniciado** (`ready-for-human`). Escopo atual: solicitação de entrega por paciente/familiar com origem/destino e comprovação de retirada; orçamento particular com rota, tarifa, distância, tempo/trânsito conhecidos, validade, centavos e arredondamento rastreáveis; reorçamento quando endereço mudar; privacidade de endereços/autorizações e escopo familiar `PEDIDOS`. Dependências: decisões D04/D05 e critérios de unidade/protocolo, destinatário autorizado, rota/provedor e modelo comercial ainda não aprovados; controles privados e revisão operacional do ticket 05; representação do ticket 04; e as políticas/capacidades de custeio do ticket 01. Não foram alterados arquivos do ticket 09, nem habilitado financiamento institucional ou subsídio.

## Ticket 09 — implementação parcial autorizada — 06/10/2026

Base `474cf951` confirmada na branch `docs/planejamento-tecnico`; `referencia.md` preservada. V9 adiciona pedido com endereços cifrados, autorização de retirada privada, escopos PEDIDOS/RECEBIMENTO, tarifa versionada, adaptador de rota, orçamento com composição/validade/origem da rota, idempotência e substituição por mudança de endereço. A unidade não é criada nem presumida como aceita; sem aceite o pedido fica `EM_VERIFICACAO`.

`OrderFlowTest` passou com PostgreSQL 17.6/Testcontainers e HTTP real: isolamento, familiar sem escopo/revogado, destinatário, evidência, endereço inválido, rota inválida/timeout, ausência de tarifa (`422`), cálculo sintético, replay, `412` e substituição. `OrderBrowserFlowTest` passou com Chromium → Vite → API → PostgreSQL real, armazenamento privado temporário e sem interceptação; anexou evidência, criou/acompanhou o pedido e exibiu o bloqueio de cotação sem aceite da unidade. Não é validação visual manual nem acessibilidade.

O adaptador padrão de rota permanece indisponível (`503 INTEGRATION_UNAVAILABLE`); respostas controladas são exclusivas dos testes e não homologam fornecedor. A tarifa de teste é sintética e não é política comercial. Permanecem bloqueados unidade/protocolo e retorno reais, provedor contratado, política tarifária/comercial, custódia, cobrança, reserva, designação e execução. Pendências do ticket 05, teste mobile e aprovação operacional permanecem separadas. Não iniciar ticket 10.

## Ticket 10 — implementação técnica parcial autorizada — 06/10/2026

Base autorizada `2cc16fd`, branch `docs/planejamento-tecnico`, inicialmente igual a `origin/docs/planejamento-tecnico`; `referencia.md` permaneceu não rastreada e sem alteração. O recorte não iniciou designação/ticket 11, liquidação, repasse, deploy, contratação ou movimentação financeira externa.

V10 implementa aceite append-only protegido também por trigger, com snapshot de valores, rota, tarifa, política apresentada, ator e versões do pedido/orçamento. Usuário/escopo é reavaliado; orçamento vencido/substituído, pedido alterado, valores/política divergentes e familiar revogado são recusados. Mudança de endereço substitui o orçamento e exige novo aceite; antes de qualquer designação libera somente a reserva local ainda não consumida e envia eventual cobrança para reconciliação, sem inventar política de cancelamento/estorno.

Particular não consulta instituição financiadora. Parcela positiva exige adaptador de pagamento habilitado, cria obrigação/outbox única e permanece `PENDENTE_PAGAMENTO`; somente evento autenticado e validado pode tornar a cobertura confirmada. O frontend não possui comando de confirmação. O adaptador padrão está indisponível e retorna `503`; não foi implementada chamada financeira externa. O contrato do adaptador recebe bytes/headers originais e devolve evento normalizado apenas após autenticação; ID+hash deduplicam, valor/moeda/destinatário divergentes são rejeitados, timeout/resultado incerto fica `INCERTA` e evento tardio não regride confirmação nem ressuscita orçamento substituído.

Subsidiado separa elegibilidade de disponibilidade: cotação e aceite exigem decisão aprovada ainda vigente, política ativa da mesma instituição e programa `HABILITADO`. `conta_programa` recebe apenas aportes confirmados e a reserva usa atualização condicional no PostgreSQL; falta de saldo retorna `409 FUNDING_INSUFFICIENT`. Paciente + subsídio permanece igual ao frete em banco e no snapshot. Reserva, aceite, operação e outbox pertencem à mesma transação; falha final testada reverte todos os efeitos.

Evidências executadas nesta rodada:

- `QuoteAcceptanceFlowTest` em PostgreSQL 17.6/Testcontainers: 3 testes, zero falhas/erros. Cobriu vencido, substituído, conflito de versão, familiar revogado, provedor/política ausentes, replay, corrida de dois aceites/reservas no último saldo, saldo insuficiente, liberação por novo endereço, rollback forçado sem efeito parcial, assinatura controlada ausente, duplicação, payload divergente, valor divergente, resultado incerto e evento tardio fora de ordem.
- Suíte Maven comum final: 40 testes descobertos, 35 aprovados e 5 opt-in de navegador ignorados. Uma execução intermediária expôs uma asserção histórica que calculava `max(version)` como texto e esperava V8; ela foi corrigida para inteiro/V10 antes da execução final verde. Após o último endurecimento de destinatário/evento, `QuoteAcceptanceFlowTest` passou novamente com 3/3.
- `AcceptanceBrowserFlowTest -DbrowserTest=true`: Chromium → Vite → API HTTP real → PostgreSQL 17.6 descartável, 1 teste aprovado. A interface aceitou as condições e mostrou `PENDENTE_PAGAMENTO`; banco confirmou um aceite, uma operação pendente e nenhuma confirmação de cobertura. É evidência funcional local, não homologação de adquirente/provedor financeiro.
- Build web e compilação Maven passaram. OpenAPI 3.0.3 passou em `openapi-spec-validator 0.7.2`; a validação também corrigiu três parâmetros de path preexistentes nas rotas V7 de recurso/revisão. `git diff --check` passou.

Conclusão separada: **particular tecnicamente parcial** — aceite/outbox/eventos e bloqueio sem integração concluídos, cobrança externa não homologada; **subsidiado tecnicamente parcial** — benefício/programa/saldo/reserva concorrente concluídos, sem programa ou responsáveis financeiros reais habilitados; **dependências externas bloqueadas** — provedor/credenciais/formato definitivo de evento, política de cancelamento/estorno, unidade/protocolo/retorno, tarifa comercial e homologação financeira. O ticket 10 inteiro permanece aberto. Próxima ação humana é revisar o conjunto e resolver essas dependências; não iniciar ticket 11.

Revisão Git concluída contra `2cc16fd`: commits `87b74bd` (implementação, testes e contrato) e `4997dcc` (documentação e ticket), sem inclusão ou alteração de `referencia.md`. Push sem force concluído para `origin/docs/planejamento-tecnico` até `4997dcc`; a ponta remota anterior foi confirmada em `2cc16fd`. Este fechamento documental segue em commit separado, sem deploy ou workflow acionado no repositório.

## Homologação local de e-mail — 10/10/2026

Política mínima aprovada pelo autor exclusivamente para ensaio local: dados mínimos, acesso por loopback e descarte posterior dos dados/volume descartáveis. O remetente/destinatário ficou restrito ao endereço autorizado por `EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS`; nenhuma mensagem foi permitida a terceiros. A senha usada foi senha de app inserida pelo autor no prompt privado e permanece somente em `.env.smtp.local` (não rastreado, modo 0600).

O SMTP Gmail em `smtp.gmail.com:587` com autenticação e STARTTLS foi aceito pelo serviço em respostas HTTP `202`, separadamente da confirmação de recebimento na caixa. O backend isolado usou `127.0.0.1:18080` e PostgreSQL descartável em `127.0.0.1:5433`; um processo preexistente em `:8080` não foi alterado.

Evidências executadas sem registrar códigos, tokens, links ou credenciais: cadastro aceito pelo SMTP e confirmação recebida pelo autor; confirmação de e-mail concluída (`200 CONCLUIDA`); tentativa de reutilização rejeitada (`401 UNAUTHENTICATED`); login móvel aceito (`200`); recuperação aceita pelo SMTP (`202`), recebida e concluída (`200 CONCLUIDA`); a troca de senha revogou a sessão anterior (banco: uma sessão revogada e nenhuma ativa). Códigos expirados e entradas inválidas foram rejeitados conforme esperado. O recebimento foi confirmado pelo autor, sem copiar conteúdo sensível para este relatório.

Limitações: o novo login após a recuperação não foi repetido automaticamente porque a nova senha foi digitada somente pelo autor; a aceitação SMTP e o recebimento foram mantidos como evidências distintas. Não houve envio a terceiros, cadastro público, contratação, cobrança, deploy ou habilitação logística. Próxima ação: preservar este registro, encerrar o backend e remover somente o PostgreSQL/volume descartável e demais artefatos temporários do ensaio; manter `.env.smtp.local` privado para configuração local futura.

## Fechamento da reconstrução integrada — 15/10/2026

Base: `41028cd`, branch `docs/planejamento-tecnico`. Home, acesso, painel e pedidos aprovados foram preservados. A validação reutilizou evidências recentes e cobriu a integração pendente.

Correções demonstradas: seletor ambíguo de `Financeiro` restrito ao `h1` estrutural; barra lateral desktop tornou-se rolável para as onze áreas, mantendo o carrossel horizontal no mobile; contraste de `card-kicker` ajustado para AA; asserções antigas alinhadas aos textos aprovados `Solicitar cadastro` e `Olá, <nome>.`.

`IntegratedAcceptanceBrowserFlowTest -DbrowserTest=true` passou na execução final, com Chromium → Vite → Spring Boot → PostgreSQL 17.6 descartável, sem interceptação. Verificou navegação pública/autenticada, áreas, rota direta, voltar/avançar, reload, logout, limpeza de cookies, responsividade 390/1280 px, overflow horizontal e Axe nos dois tamanhos. É uma jornada integrada 1/1, não cobertura completa de papéis, políticas ou combinações de dados.

Build web e compilação Maven passaram. O ciclo `start → verify → stop → start` passou com backend 401 sem sessão, CSRF 403/FORBIDDEN, web 200, Flyway V19 e gate `NORMAL`; volume `exame_perto_postgres` e dados normais foram preservados. Não foram criadas contas, unidades, políticas ou saldos no banco normal.

Screenshots sintéticas: `web/test-results/reconstrucao-financeiro-sanitizado.png` e `web/test-results/reconstrucao-operacao-privacidade-sanitizado.png`. As áreas novas ainda não têm aprovação visual pessoal registrada: Operação, Catálogo/unidades, Análise de benefícios, Financeiro, Operação de privacidade e fluxos de entregador. Evidência automatizada não substitui aprovação. Dependências externas não homologadas: SMTP/rota/provedor financeiro reais, critérios profissionais de elegibilidade e operação móvel em dispositivo.

Comandos: `scripts/start-local.sh`, `scripts/verify-local-environment.sh`, `scripts/stop-local.sh`. Nenhum deploy foi realizado.

## Áreas de atuação independentes — 15/10/2026

Partindo de `ab9517b`, o shell autenticado deixou de montar um mural único. O backend agora só anuncia `PACIENTE` quando existe perfil de paciente e `FAMILIAR` quando existe autorização vigente; as áreas de entregador, operação, benefícios, financeiro e privacidade operacional continuam dependentes das capacidades/papéis retornados. A troca de área apenas muda o contexto de navegação; APIs, MFA, escopos, vínculo institucional e isolamento continuam sendo revalidados pelo backend.

Mapa canônico: `/app/paciente` (dashboard, `/app/paciente/pedidos`, `/app/paciente/beneficios` e autorizações); `/app/familiar` (representados, `/app/familiar/pedidos`); `/app/entregador` (dashboard, `/app/entregador/cadastro`, `/app/entregador/entregas`); `/app/operacao` e `/app/operacao/catalogo`; `/app/beneficios` e `/app/beneficios/fila`; `/app/financeiro`; `/app/privacidade/operacao`; configurações compartilhadas em `/app/conta` e privacidade da própria conta em `/app/privacidade`. Rotas antigas de pedidos, entregas, análise e operação de privacidade redirecionam para os destinos canônicos.

Entrada: uma única área é encaminhada diretamente ao dashboard; múltiplas áreas exibem `Onde você quer atuar agora?` e um seletor discreto `Trocar área`. Menus são compostos somente pelas rotas da área ativa. O contexto familiar mostra o paciente representado; áreas institucionais mostram a instituição retornada pelo backend e a nota de revalidação. Nenhum papel é concedido pelo frontend.

Validação: `IntegratedAcceptanceBrowserFlowTest -DbrowserTest=true` passou com Chromium → Vite → Spring → PostgreSQL 17.6 descartável, sem interceptação. O cenário isolado incluiu paciente, familiar com escopos `PEDIDOS`/`BENEFÍCIOS`, entregador, operação, análise de benefícios, financeiro e operador de privacidade; verificou seleção das sete áreas, menus contextuais, rota legada `/app/pedidos`, URL canônica, voltar/avançar, reload, logout, responsividade 390/1280 px, overflow e Axe. Regressões `BenefitFlowTest`, `PrivacyFlowTest`, `FundingFlowTest` e `PayoutFlowTest` passaram. Build web e compilação Maven passaram.

Capturas sintéticas sem dados pessoais: `web/test-results/reconstrucao-paciente-sanitizado.png`, `web/test-results/reconstrucao-familiar-sanitizado.png`, `web/test-results/reconstrucao-entregador-sanitizado.png`, `web/test-results/reconstrucao-financeiro-sanitizado.png` e `web/test-results/reconstrucao-operacao-privacidade-sanitizado.png`. O teste integrado é 1/1 e não substitui matriz completa de revogação por escopo, múltiplas instituições, dispositivo móvel ou aprovação visual humana. Não houve escrita no banco normal, alteração silenciosa da conta real ou deploy.

## Integração funcional — catálogo de políticas e onboarding explícito — 15/10/2026

O formulário de benefícios deixou de exigir UUID digitado. Foi criado `GET /api/v1/benefit-policies`, protegido por sessão autenticada, que retorna somente políticas ativas, vigentes e de instituições habilitadas; o contrato OpenAPI foi atualizado. O backend continua validando paciente/escopo, instituição e vigência no envio. O carregamento do catálogo foi separado da fila de solicitações para que uma falha auxiliar não esconda uma política autorizada. Em `Conta e segurança`, uma conta sem perfil pode iniciar explicitamente seu perfil de paciente; isso não concede verificação nem outro papel.

Evidências: `BenefitFlowTest` passou com PostgreSQL 17.6/Testcontainers e confirmou catálogo, isolamento familiar, revogação/expiração, evidências, MFA, autoanálise, decisão, recurso e versão obsoleta. `IntegratedAcceptanceBrowserFlowTest -DbrowserTest=true` passou com Chromium → Vite → Spring → PostgreSQL 17.6 descartável, sem interceptação; confirmou a rota `/app/paciente/beneficios`, o seletor de política vindo da API, as sete áreas, rotas/menus, reload, logout, responsividade, overflow e Axe. Build web, compilação Maven e `git diff --check` passaram. O cenário browser é 1/1 e não cobre todas as combinações de instituição/escopo.

Ainda dependem de decisão/homologação externa: critérios profissionais de elegibilidade, provedores de rota/pagamento/repasse, operação GPS em aparelho e políticas reais de retenção/expurgo. Não houve migração, escrita no banco normal, alteração de conta real ou deploy.

## Integração de rota rodoviária — 15/10/2026

Base: `fd5fc2374ccf3213d9faa9eb8e4d2df3020e0aa2`. O adaptador server-side de rota foi conectado ao openrouteservice/HeiGIT no perfil `driving-car`, preservando `RouteProvider`, o cálculo tarifário, cobertura, validade, idempotência e o histórico já existentes. A chamada envia somente as coordenadas confirmadas do pedido; o orçamento persiste `openrouteservice`, referência hash, metros, segundos, instante e `transito_incluido=false`. Não há fallback em linha reta.

Guardas adicionadas: chave e endpoint somente em configuração do servidor, HTTPS fora de testes locais, timeout de 3 s, corpo limitado a 64 KiB, no máximo uma repetição para 429/502/503/504, coordenadas válidas, distância até 6.000 km, duração positiva e validação do resumo JSON. Rota inexistente, resposta inválida, limite ou timeout bloqueiam a cotação sem alterar pedido, tarifa ou reserva. A duração continua estimativa de rede viária, não trânsito ao vivo nem hora garantida.

Coordenadas continuam sendo uma etapa explícita e confirmada do contrato; sem latitude/longitude o backend retorna `INVALID_ADDRESS` e não consulta o provedor. Não foi criada geocodificação automática. A solução futura escolhida, caso autorizada com tela de confirmação, é o Pelias do mesmo provedor; a etapa permanece pendente por não haver confirmação humana nem decisão de retenção dos resultados.

Validação: `ConfiguredRouteProviderTest` passou 5/5 com servidor HTTP local controlado, cobrindo resposta válida/unidades, rota inexistente, JSON inválido, limite, 429/repetição, timeout e coordenadas ausentes. `OrderFlowTest` passou 1/1 após alinhar seu fixture à V19; `OrderBrowserFlowTest` passou 1/1 com Chromium → Vite → Spring → PostgreSQL 17.6 descartável, sem interceptação da API. `QuoteAcceptanceFlowTest` passou 4/4 e `npm run build` passou. A execução browser usa resposta controlada apenas no teste e não homologa o fornecedor externo.

O script `verify-local-environment.sh` agora distingue adaptador configurado de provedor consultado; não faz consulta externa automática. A variável `EXAME_PERTO_ROUTE_API_KEY` não estava presente nesta máquina, portanto nenhuma consulta real ao HeiGIT foi realizada. Homologação externa depende de o responsável criar/configurar privadamente uma chave openrouteservice Standard, respeitar 2.000 Directions/dia e 40/min e executar um ensaio separado com coordenadas públicas. O ambiente normal permanece com orçamento dependente de rota bloqueado até essa configuração.

## Fechamento de integrações entre jornadas — 15/10/2026

Base: `1050e2e`. A comparação do escopo com o código e as evidências encontrou uma lacuna de interface na ligação benefício + financiamento: o backend já aceitava `programId` e executava a validação de benefício vigente, reserva concorrente e liberação, mas a tela de pedidos sempre enviava `null`. Foi implementado `GET /api/v1/order-options/benefit-programs?patientId=...`, com autorização de pedidos, instituição, política vigente, benefício aprovado e programa habilitado; a tela passou a selecionar o registro autorizado e enviá-lo ao orçamento. O contrato OpenAPI foi atualizado; não houve migração.

O fixture de `CustodyFlowTest` também foi corrigido para criar uma unidade sintética antes do protocolo, preservando a FK introduzida por V19. Nenhuma asserção foi removida e nenhum timeout foi aumentado.

Testes desta rodada: `mvn -q -f backend/pom.xml clean test -Dtest=QuoteAcceptanceFlowTest` passou com PostgreSQL 17.6/Testcontainers; confirmou catálogo de programa por paciente/benefício e preservou os cenários de aceite, reserva concorrente, revogação, idempotência, pagamento incerto e rollback. `CustodyFlowTest` passou após o fixture ser alinhado à V19. `npm run build` passou. A regressão combinada de `FundingFlowTest,PayoutFlowTest,CustodyFlowTest,PrivacyFlowTest` foi executada; antes da correção, somente Custody falhou pelo fixture sem unidade e a execução foi repetida isoladamente com sucesso. O erro inicial de `QuoteAcceptanceFlowTest` foi de classe compilada stale (`RequestBodyLimitFilter$Limit`) e foi resolvido por `clean`, sem alteração de asserções.

As integrações internas já exercitadas nas evidências anteriores permanecem: benefício → orçamento/aceite → reserva; endereço/cancelamento pré-designação → liberação; designação → retirada → custódia → recebimento; entrega comprovada → obrigação → repasse/conciliação; revogação familiar e bloqueio de encerramento durante custódia/obrigação. A nova ligação foi verificada em API/Spring/PostgreSQL sintético, mas ainda não há uma jornada browser focada que conclua pagamento externo, provedor de rota ou repasse real.

Dependências abertas: provedor de pagamento bloqueia confirmação de cobrança e cobertura; rota bloqueia cálculo quando indisponível; provedor de repasse bloqueia confirmação, deixando solicitação/incerteza/conciliação internos disponíveis; unidade/protocolo e habilitação reais bloqueiam oferta/retirada fora dos fixtures controlados. Não foram criados dados no banco normal, alteradas contas pessoais ou realizado deploy.

### Validação browser da seleção de programa — 15/10/2026

Partindo de `0fb6102`, foi adicionada a jornada isolada `BenefitCoverageBrowserFlowTest` com PostgreSQL 17.6 descartável, Spring em porta aleatória e Chromium/Vite reais, sem interceptação da API. O fixture usa somente paciente, pedido, benefício aprovado, política vigente e conta sintéticos no container de teste; o provedor de rota é controlado no teste e nenhum pagamento ou repasse externo é executado.

O cenário aprovado percorre login pela interface, abertura do pedido pelo menu do paciente, seleção do programa autorizado no seletor, cálculo e conferência do orçamento, aceite, cobertura exibida e reload. As consultas finais no banco confirmaram o benefício e programa vinculados ao orçamento aceito, uma única `reserva_subsidio`, disponibilidade `0.00`, reservado `38.00` e ausência de duplicação. O mesmo teste tentou programa de outra instituição e um segundo pedido sem saldo; ambos foram recusados sem orçamento aceito ou reserva parcial.

Durante a investigação, uma primeira execução revelou `403` apenas no login direto do fixture porque ele usava cliente `WEB` sem CSRF/origem; foi corrigido para `MOBILE`, mantendo o login browser real. Depois, o teste revelou dois seletores desatualizados (`Entrar` como botão e `Pedidos` em vez de `Meus pedidos`) e uma asserção Playwright estrita sobre duas opções; foram alinhados aos elementos reais sem aumentar timeout ou remover asserções. Não houve defeito de persistência nem falha de autorização do produto.

Resultado: `BenefitCoverageBrowserFlowTest` 1/1 aprovado; `QuoteAcceptanceFlowTest` e build web serão a regressão focal desta entrega. A evidência cobre integralmente esta combinação sintética de paciente/pedido/programa e os bloqueios de programa externo e saldo insuficiente, não todas as instituições, escopos familiares, MFA ou provedores externos já tratados por testes anteriores.
