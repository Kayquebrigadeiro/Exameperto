# Orçamento zero — homologação local e composição V1

Levantamento conferido em 09/10/2026. **R$ 0 é requisito eliminatório**: não cadastrar cartão, não comprar domínio, não selecionar plano pago, não aceitar cobrança por excedente e não tratar crédito promocional como gratuidade. Cotas e termos de terceiros mudam; devem ser reconferidos nas fontes oficiais imediatamente antes de qualquer criação de conta. Esta análise não autoriza cadastro em fornecedor, deploy ou envio de e-mail.

## Decisão atual

1. Homologar primeiro no computador do autor, em `localhost`, com PostgreSQL e armazenamento POSIX duráveis locais e uma conta SMTP que o autor já controle. A aplicação continua usando os adaptadores existentes (`EmailGateway`/Spring Mail, JDBC PostgreSQL e `EXAME_PERTO_PRIVATE_ROOT`); nenhum controle de cookie, Origin/CORS/CSRF, TLS, chave ou permissão é removido.
2. Não aprovar ainda uma composição hospedada gratuita. As opções examinadas isoladamente têm utilidade, mas a composição completa exigiria adaptadores e ensaios que ainda não existem: entrega de e-mail por HTTPS quando a hospedagem bloqueia SMTP, objetos S3 privados, frontend/backend na mesma origem e backup independente.
3. Se o autor não possuir uma caixa com SMTP autorizado, parar antes do envio. Criar conta Brevo gratuita é uma alternativa futura a avaliar, não uma ação autorizada nesta rodada.

O custo financeiro incremental local pode ser zero, mas computador, disco, energia, internet, manutenção e trabalho humano continuam sendo recursos reais. A homologação local não é disponibilidade pública nem operação contínua.

## Homologação local com e-mail real

Usar somente recursos já pertencentes ao autor:

- PostgreSQL local com volume durável, usuário exclusivo e as variáveis `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD`;
- raiz privada POSIX fora do repositório em `EXAME_PERTO_PRIVATE_ROOT`, com permissão mínima;
- cópia cifrada em um segundo destino local controlado pelo autor, seguida de restore isolado pelos scripts existentes; não confundir o volume principal com backup;
- três chaves independentes geradas localmente e guardadas fora de Git/logs: `EXAME_PERTO_DATA_KEY`, `EXAME_PERTO_SEARCH_KEY` e `EXAME_PERTO_SIGNING_KEY`;
- origem `http://localhost:5173`, já exercitada no Chromium como contexto seguro de loopback, sem expor portas à internet;
- caixa/remetente SMTP já existente, com TLS e credencial específica quando o provedor oferecer. As variáveis exatas permanecem na checklist de [PORTFOLIO-V1](PORTFOLIO-V1.md).

Uma conta Gmail pessoal existente pode servir apenas para poucos envios de homologação se o autor a escolher: o Google limita contas pessoais a 500 mensagens ou destinatários por dia e pode bloquear novos envios por 1 a 24 horas; senha de aplicativo requer verificação em duas etapas e é revogada quando a senha da conta muda. Isso não transforma Gmail pessoal em canal transacional aprovado, não fornece SLA nem resolve bounce/observabilidade. Fontes: [limites de envio do Gmail](https://support.google.com/mail/answer/22839), [senhas de app](https://support.google.com/mail/answer/185833).

Nenhuma credencial, endereço real, token ou cabeçalho de mensagem entra no Git, chat ou evidência. `EXAME_PERTO_EMAIL_ENABLED` e `REGISTRATION_PRIVACY_APPROVED` ficam `false` até configuração privada, política mínima aprovada e autorização específica para cadastro/confirmação/recuperação.

## Provedores e limites gratuitos

| Opção | Cota e expiração | Suspensão e persistência | Compatibilidade/decisão |
|---|---|---|---|
| Brevo Free, somente e-mail | 300 envios/dia, sem acumular; até 1.000 transacionais excedentes aguardam em fila e os seguintes não são entregues. O plano não tem prazo e não exige cartão. | Conta gratuita inativa por quatro meses pode ser apagada definitivamente, após avisos; atividade transacional conta como uso. Conta/envios podem ser suspensos por anomalia, comprometimento, qualidade ou termos. Logs de atividade de conta duram 90 dias e não substituem evidência própria. | O SMTP genérico existente é compatível em princípio, após o provedor fornecer parâmetros e verificar remetente. Candidato para autorização futura; nenhuma conta criada. [plano e preço](https://help.brevo.com/hc/en-us/articles/208589409-About-Brevo-s-pricing-plans), [limites](https://help.brevo.com/hc/en-us/articles/208580669-FAQs-What-are-the-limits-of-the-Free-plan), [inatividade](https://help.brevo.com/hc/en-us/articles/4410311028626-Can-my-Free-Plan-account-be-deleted-due-to-inactivity-), [suspensão](https://help.brevo.com/hc/en-us/articles/360017299259-Why-have-my-account-or-email-campaigns-been-suspended), [retenção de logs](https://help.brevo.com/hc/en-us/articles/33135225696914-View-your-account-activity-logs). |
| Neon Free, somente PostgreSQL | Em 02/10/2026, anunciou 1 GB por projeto, 100 CU-h/mês, até 2 CU, 10 branches e restore instantâneo de 6 horas. A fonte atual não anuncia expiração fixa do projeto; isso deve ser reconfirmado antes de usar. | Compute reduz a zero após cinco minutos sem atividade e volta ao conectar; a persistência está separada do compute. Restore de 6 horas não atende sozinho backup/RPO/RTO. Comportamento após ultrapassar cada cota e política de inatividade precisam ser confirmados no cadastro, sem inserir cartão. | JDBC/Flyway são compatíveis em princípio, mas o `AccountMailWorker` consulta a fila a cada segundo e impediria ociosidade; não configurar enquanto polling, pool, recuperação e backup externo não forem ensaiados. [cotas publicadas](https://neon.com/blog/neon-free-plan-1-gb-per-project), [compute e scale-to-zero](https://neon.com/docs/manage/endpoints/). |
| Cloudflare Pages Free, somente frontend | 500 builds/mês, um build simultâneo, timeout de 20 minutos, 20.000 arquivos, 25 MiB por arquivo, 100 projetos e 100 domínios personalizados por projeto. O projeto recebe `*.pages.dev`, portanto não exige compra de domínio. A documentação consultada não define expiração fixa. | É hospedagem estática; não persiste PostgreSQL nem objetos privados da aplicação. Limites antiabuso e termos continuam aplicáveis. | O frontend atual usa `/api`, `credentials: same-origin`, cookie `SameSite=Lax` e Origin exata. Separá-lo do backend quebra a sessão; não afrouxar CORS/cookie. Só reavaliar com proxy same-origin ou frontend empacotado, ambos testados. [limites](https://developers.cloudflare.com/pages/platform/limits/), [subdomínio fornecido](https://developers.cloudflare.com/pages/configuration/custom-domains/). |
| Render Free, web + PostgreSQL | Web: 750 horas/mês e suspensão até o mês seguinte ao esgotar; dorme após 15 minutos e pode levar cerca de um minuto para voltar. PostgreSQL: 1 GB, expira em 30 dias, fica 14 dias em carência e depois é apagado. | Sistema de arquivos web é efêmero e se perde em restart/redeploy/suspensão; não há disco persistente grátis. PostgreSQL grátis não tem backup. Sem meio de pagamento, excedentes suspendem serviços/builds em vez de cobrar. | **Rejeitado para a V1:** as portas SMTP 25/465/587 são bloqueadas, objetos POSIX seriam perdidos e o banco expira. Não remover e-mail/backup para encaixar. [limites oficiais](https://render.com/docs/free). |
| Koyeb Free, web + PostgreSQL | Uma instância web: 512 MB, 0,1 vCPU, 2 GB SSD, uma região entre Frankfurt/Washington e scale-to-zero após uma hora; banco: 1 GB e apenas 5 horas ativas/mês. | Web grátis não aceita volume persistente e não é indicada para produção. A plataforma exige cartão, faz pré-autorização de US$ 29 e o plano inicial pode gerar cobrança; ainda não oferece limite de gastos. | **Rejeitado pelo requisito R$ 0:** cadastrar cartão/ativar cobrança é proibido, mesmo que a instância `free` isolada custe zero. [instância](https://www.koyeb.com/docs/reference/instances), [banco](https://www.koyeb.com/docs/databases), [cobrança/cartão](https://www.koyeb.com/docs/faqs/pricing). |

Os limites acima são tetos de fornecedor, não dimensionamento aprovado. Plano gratuito pode mudar, suspender ou encerrar; por isso nenhum dado real deve depender de uma única cópia, e nenhum serviço é aprovado apenas por ter preço nominal zero.

## Composição gratuita avaliada, não aprovada

Uma composição tecnicamente possível para nova avaliação seria frontend e backend na mesma origem, backend Java gratuito, PostgreSQL gratuito e e-mail transacional gratuito. **Hoje ela não fecha com o código e os controles existentes**:

- Pages separado quebra a sessão same-origin; empacotar o frontend no backend ou criar proxy exige implementação e regressão de Origin/CORS/CSRF/cookies;
- Render bloqueia SMTP; usar Brevo por HTTPS exigiria um novo adaptador de `EmailGateway`, desabilitado por padrão, com timeout, respostas genéricas, reconciliação e testes de falha;
- a implementação de objetos aceita apenas POSIX; hospedagens gratuitas avaliadas não oferecem volume durável adequado. Um adaptador S3 privado exige autorização, contrato explícito, cifra/permissões, deleção, restore e testes;
- o polling de e-mail a cada segundo impede o banco Neon de reduzir compute a zero; mudar o agendamento exige preservar prazo, concorrência, `SKIP LOCKED`, falha fechada e observabilidade;
- restore curto do provedor não substitui `pg_dump`, cópia de objetos, diário de expurgo e restore isolado. O backup deve ficar em destino independente controlado pelo autor.

Consequentemente, o recorte hospedado continua **não pronto**. Não haverá deploy, compra de domínio, cadastro de cartão nem criação de contas externas até decisão posterior. Integrações logísticas, financeiras, mapa/GPS, benefício e políticas ainda não aprovadas permanecem bloqueadas.

## Gate antes de qualquer ativação gratuita

O autor precisa escolher entre SMTP já existente e avaliação posterior do Brevo Free; confirmar remetente e destinatário de homologação; aprovar política mínima/responsáveis; e autorizar expressamente cada envio. O projeto consegue gerar chaves, preparar o ambiente local, verificar que segredos não estão no Git, executar migração, backup/restore isolado e o roteiro de cadastro/confirmação/recuperação. Só depois da homologação local deve ser aberto um ticket documental separado para escolher a composição hospedada e reconferir cotas, termos, regiões, retenção e ausência de cobrança.
