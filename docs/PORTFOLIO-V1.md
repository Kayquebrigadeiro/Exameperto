# Escopo concreto do portfólio V1

Este recorte descreve o que um visitante poderá executar com uma conta real, sem dados de demonstração, deploy ou integração simulada. A publicação só pode ocorrer depois da configuração privada e homologação descritas abaixo; no ambiente atual, o SMTP continua desligado. O orçamento é estritamente R$ 0: a decisão vigente é homologar localmente com recursos já controlados pelo autor e apenas depois reavaliar a composição gratuita documentada em [ORCAMENTO-ZERO-V1](ORCAMENTO-ZERO-V1.md).

## Matriz curta

| Fluxo | Estado para portfólio | Dependência para habilitar | Evidência disponível / pendência de teste |
|---|---|---|---|
| Cadastro de conta básica | Utilizável condicionalmente: cria conta pendente e não concede identidade ou papel | PostgreSQL, três chaves independentes, `REGISTRATION_PRIVACY_APPROVED=true`, SMTP habilitado e remetente autorizado | `Registration*`, `AuthFlowTest` e `BrowserFlowTest` local; falta homologar entrega numa caixa do autor |
| Confirmação/reenvio de e-mail | Utilizável condicionalmente; resposta genérica não confirma entrega | Mesmo SMTP, endereço controlado pelo autor, HTTPS/origem autorizada e monitoramento de falhas | `SmtpFailureTest` prova falha fechada; ensaio real de recebimento, spam, expiração e bounce ainda pendente |
| Login, renovação e logout | Utilizável após confirmação; sessão web usa cookie HttpOnly e access token em memória | Chaves estáveis, HTTPS, origem exata e banco disponível | `AuthFlowTest`, `PersistenceRestartTest` e Playwright local; rate limit distribuído e observabilidade ainda pendentes |
| Perfil da conta | Utilizável: consulta e edição de nome/telefone próprios; e-mail/estado somente leitura | PostgreSQL, chaves estáveis, sessão ativa e contrato de campos permitidos | `AuthFlowTest` cobre leitura, edição, ETag, conflito e rota alheia; browser cobre edição; campos futuros continuam fora |
| Autorização familiar | Utilizável condicionalmente; gestão existente respeita escopos, expiração e revogação | SMTP privado homologado e decisão sobre representação legal/incapacidade | `RepresentationFlowTest`/`FamilyBrowserFlowTest` local; recebimento real do convite ainda pendente |
| Solicitações de acesso/correção/exclusão | Utilizável na web para criar e acompanhar protocolos próprios | Política por categoria, MFA/papel para etapas privilegiadas, banco e objetos privados | `PrivacyFlowTest` cobre idempotência/terceiro; browser percorre criação e lista; resposta/expurgo operacional continuam sem painel |
| Encerramento da conta | Utilizável na web para solicitar e acompanhar; execução continua dependente de operador/política | Política aprovada por categoria, custódia/obrigações e verificação operacional | Browser mostra solicitação, estado e residual; `PrivacyFlowTest` cobre bloqueado/permitido e revogação; restore permanece backend |
| Entrega, benefício, GPS, pagamento e repasse | Bloqueados; devem aparecer como indisponíveis, sem saldo, tarifa, rota ou localização fictícios | Políticas, responsáveis, fornecedores, homologações, dispositivos e infraestrutura correspondentes | Testes locais sintéticos existem, mas não são evidência de operação real |

## Acessibilidade do recorte V1

A navegação V1 autenticada contém somente perfil da conta, solicitações/encerramento e gestão familiar. Os painéis legados de entregador, revisão de veículo, benefício, aporte, pedido/rastreamento e repasse foram retirados dessa composição; as APIs e suas permissões não foram alteradas. Eles não integram a declaração de acessibilidade ou o portfólio V1.

O ensaio integrado percorre cadastro, confirmação, login, perfil, privacidade/encerramento, logout e recuperação. Em Chromium, ele verifica Axe sobre a página autenticada inteira em 390×844 e 1280×900, ausência de overflow horizontal, foco inicial visível, ordem inicial por teclado, foco do primeiro campo inválido, rótulos e mensagens com `role="alert"`. O ensaio separado `FamilyBrowserFlowTest` repete Axe e os dois layouts após perfil, convite, aceite, confirmação e revogação.

Limites: Axe detecta um conjunto automatizável de problemas e não prova acessibilidade completa. Ainda faltam avaliação humana com leitor de tela, zoom/reflow além dos tamanhos ensaiados, contraste em estados de sistema/alto contraste, movimento reduzido, diferentes navegadores/dispositivos e uso prolongado somente por teclado. SMTP externo, conteúdo efetivamente recebido e atendimento humano também não são avaliados por Axe.

### Encerramento: estados que não podem ser confundidos

O contrato da API retorna `closureStatus` (`PENDENTE`, `BLOQUEADA`, `EM_EXECUCAO`, `ENCERRADA`) separado de `purgeStatus` e `anonymizationStatus`. O estado `ENCERRADA` significa encerramento local concluído; `TRANSFORMADA_NAO_INTEGRAL` significa que campos controlados foram transformados, não que a pessoa ficou anônima. O expurgo de conteúdo e a conservação de registros financeiros/auditáveis são decisões distintas. A web V1 apresenta esses estados e permite registrar/acompanhar a solicitação; a execução efetiva continua condicionada a operador, custódia, obrigações e políticas aprovadas.

## Resíduos após encerramento

| Categoria residual | Finalidade | Acesso permitido | Política pendente |
|---|---|---|---|
| UUIDs, FKs, protocolo, `encerramento_conta`, tombstones e diário de expurgos | Integridade, prova da execução e impedir restauração indevida | Serviços de privacidade/recovery e auditoria nominal; nenhum acesso público | Prazo, custodiante e rotação de chaves |
| Operações, lançamentos, repasses, reservas, custódias e comprovantes financeiros | Reconciliação, obrigação e defesa; não apagar dívida por cascata | Serviços financeiros e auditoria restrita; sem projeção operacional desnecessária | Retenção financeira, disputa, fiscalidade e descarte de evidências |
| Chaves de deduplicação e referências de negócio | Idempotência e não duplicação | Banco/serviços internos mínimos | Prazo e descarte aprovados |
| Auditoria e logs técnicos | Segurança, investigação e diagnóstico | Operação com menor privilégio; sem conteúdo privado em logs | Minimização, prazo, acesso e exportação |
| Backups, PITR, objetos de fornecedores e cópias em dispositivos | Continuidade técnica ou cópia já recebida | Somente operadores autorizados; diário reaplicado antes de liberar restore | Criptografia, rotação, expurgo de cópias e contratos |
| Conteúdo de custódia/obrigação não terminal | Não abandonar responsabilidade nem envelope | Custódia/financeiro designado e auditoria | Critério de terminalidade e prazo legal |

Não há promessa de anonimização integral nem de exclusão em fornecedores, e-mails já recebidos, caches de dispositivos ou cópias fora do controle do serviço. HMAC de CPF/e-mail, isoladamente, não altera essa conclusão.

## Checklist única para habilitar cadastro real

Os nomes abaixo foram conferidos em `application.yaml`, nos controladores de autenticação/segurança, no gateway SMTP e nos scripts de recovery. Marcadores não são valores válidos e nenhum segredo deve ir para Git, documentação, chat ou logs.

| Bloco | Configuração/requisito exato | Quem fornece ou executa |
|---|---|---|
| SMTP | Conta/provedor já controlado pelo autor; `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`; `EXAME_PERTO_EMAIL_FROM` verificado; `EXAME_PERTO_EMAIL_ENABLED=true` somente no ensaio autorizado. STARTTLS obrigatório, verificação de hostname e timeouts de 5 s já são defaults do projeto; TLS implícito exige as propriedades Spring Mail indicadas pelo provedor, sem desativar certificado. | Autor fornece host, porta, credencial e remetente pelo canal privado e autoriza o ensaio. Não contratar, cadastrar cartão ou criar conta externa nesta rodada. Projeto aplica e valida localmente. |
| Destinatário de homologação | Uma caixa real controlada pelo autor e autorização específica para cadastro/confirmação/recuperação. Não existe variável de destinatário: o endereço é informado no formulário do ensaio. | Autor fornece privadamente e autoriza cada escopo de envio. |
| Banco | `DATABASE_URL` em formato JDBC, `DATABASE_USER`, `DATABASE_PASSWORD`; PostgreSQL compatível e usuário/permissões definidos para Flyway e operação. | Autor escolhe ambiente/região e fornece acesso privado. Projeto executa migrações e verificações. |
| Chaves | `EXAME_PERTO_DATA_KEY`, `EXAME_PERTO_SEARCH_KEY`, `EXAME_PERTO_SIGNING_KEY`: três valores independentes, cada um com 32 bytes aleatórios codificados em Base64. Devem ser estáveis, com backup restrito; rotação de cifra/busca exige migração ainda não implementada. | Projeto consegue gerar localmente. Autor escolhe o cofre/gestor e controla acesso, backup e recuperação. |
| Armazenamento de segredos | Variáveis injetadas pelo processo ou gerenciador de segredos, fora do repositório, imagem, arquivo publicado, histórico de shell e logs. Separar credenciais de aplicação, backup e diário. | Autor escolhe a solução operacional; projeto prepara nomes e valida ausência no Git. |
| HTTPS, origem e proxy | A homologação local usa a exceção segura de loopback em `http://localhost:5173`, sem exposição pública. Uma publicação futura exige origem HTTPS exata em `REGISTRATION_ALLOWED_ORIGIN`, sem wildcard, e certificado válido; não exige comprar domínio se o provedor oferecer subdomínio. `server.forward-headers-strategy` permanece `none`: confiança em proxy/IP e rate limit distribuído exigem desenho antes de múltiplas instâncias. | Autor decide o ambiente futuro sem compra/cartão; projeto mantém mesma origem ou projeta proxy explícito e testa pelo endereço final. |
| Cookie/CORS/CSRF | Cookie refresh já é `HttpOnly`, `Secure`, `SameSite=Lax`, path `/api/v1/auth`; CORS permite uma origem exata, credenciais e headers enumerados; login web confere `Origin`, e refresh/logout exigem `Origin` + `X-CSRF-Token`. Não há variável para afrouxar esses controles. | Projeto mantém e verifica. Autor não precisa fornecer outro segredo CSRF; o HMAC deriva de `EXAME_PERTO_SEARCH_KEY`. |
| Política mínima da conta | `REGISTRATION_PRIVACY_APPROVED=true` somente após aprovação documentada de finalidade/base aplicável, controlador/responsável, categorias da conta/e-mail/outbox/logs, retenção/descarte, canal de suporte e tratamento de acesso/correção/encerramento. Prazos técnicos de token/sessão não são prazos legais. | Autor/responsáveis aprovam; projeto apenas configura o booleano depois da aprovação. |
| Pendências de responsáveis | Nomear responsável por privacidade/solicitações, operação de e-mail/bounces, segurança/segredos, banco/backup/restauração e suporte. Representação legal/incapacidade e retenção financeira continuam sem política aprovada e não são habilitadas pelo cadastro. | Autor nomeia e aprova. Projeto registra papéis e mantém módulos não aprovados bloqueados. |
| Objetos privados | A implementação atual aceita somente raiz POSIX durável em `EXAME_PERTO_PRIVATE_ROOT`, com acesso privado; o default em diretório temporário não serve para dados reais. Adaptador S3/objeto externo ainda não existe no código. | Autor fornece volume/armazenamento e política operacional. Projeto configura permissões e valida o adaptador existente. |
| Backup e diário | Para cópia: `BACKUP_DATABASE_URL`, `PRIVATE_OBJECT_ROOT`, `BACKUP_DESTINATION`, `APPLICATION_RELEASE`, confirmação `BACKUP_WRITES_QUIESCED=true`. Para o diário separado: `SOURCE_DATABASE_URL` e `PURGE_JOURNAL_DATABASE_URL`. Restore usa `RESTORE_DATABASE_URL`, `RESTORE_PRIVATE_OBJECT_ROOT`, `RESTORE_BUNDLE`, `RESTORE_ISOLATION_CONFIRMED=true`, `EXTERNAL_EFFECTS_DISABLED=true` e, após revisão, `RESTORE_RELEASE_APPROVED=true`. Destino precisa de criptografia, acesso mínimo e backup próprio; RPO/RTO, agenda, monitoramento e ensaio real ainda precisam ser aprovados. | Autor escolhe destinos, retenção, RPO/RTO e responsáveis. Projeto executa scripts/ensaio e registra evidência sem credenciais. |

O projeto consegue gerar localmente as três chaves, validar configuração e ausência de segredos, executar Flyway, testar os fluxos com dados sintéticos, configurar a raiz privada e ensaiar backup/restore isolado. Ele não consegue escolher ou ativar provedor, verificar remetente, criar credenciais externas, aprovar política, nomear responsáveis, definir RPO/RTO ou comprovar recebimento na caixa do autor. Não será contratado serviço, comprado domínio ou ativada cobrança.

### Roteiro curto de homologação do e-mail

1. Com HTTPS/origem, PostgreSQL, chaves, política, SMTP, remetente e caixa do autor configurados privadamente, manter todas as demais integrações bloqueadas e autorizar uma janela de envio.
2. Cadastrar uma conta no endereço controlado, registrar separadamente aceitação SMTP e recebimento (inclusive spam/remetente), confirmar uma única vez e comprovar que repetição/expiração não autentica.
3. Solicitar recuperação pela resposta genérica, receber o novo código, trocar a senha, comprovar revogação das sessões anteriores e novo login. Registrar somente data, provedor e referência não sensível; nunca token, endereço, cabeçalho privado ou captura com dado pessoal.
4. Conferir outbox `ENVIADO`/`RECONCILIAR`, timeout e bounce no provedor. Falha ou mera aceitação SMTP não encerra a homologação; ao final, decidir explicitamente se o cadastro pode permanecer habilitado.

Pagamentos, repasses, mapas, GPS, benefícios, integrações logísticas, expurgo e políticas não aprovadas permanecem desabilitados. Mobile, aparelho Android e sua auditoria continuam separados desta publicação web.

O portfólio V1 pode apresentar acesso, perfil, família e privacidade como fluxos reais condicionais, mas não deve oferecer cadastro real antes da homologação de e-mail nem anunciar entrega, benefício, rastreamento ou pagamento. A verificação Axe cobre a página V1 autenticada inteira nos dois tamanhos registrados acima; isso não amplia a conclusão para os painéis operacionais excluídos nem substitui avaliação humana.
