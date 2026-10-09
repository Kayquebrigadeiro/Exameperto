# Escopo concreto do portfólio V1

Este recorte descreve o que um visitante poderá executar com uma conta real, sem dados de demonstração, deploy ou integração simulada. A publicação só pode ocorrer depois da configuração privada e homologação descritas abaixo; no ambiente atual, o SMTP continua desligado.

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

## Plano mínimo de publicação (sem executar nesta rodada)

1. **Serviços:** aplicação Spring Boot, PostgreSQL com Flyway, armazenamento privado POSIX/S3 compatível, worker de e-mail e diário independente de expurgos; gate de restauração deve permanecer fechado até verificação.
2. **Configuração/segredos:** `DATABASE_*`, três chaves aleatórias independentes (`DATA_KEY`, `SEARCH_KEY`, `SIGNING_KEY`), `REGISTRATION_ALLOWED_ORIGIN`, `REGISTRATION_PRIVACY_APPROVED`, SMTP host/porta/usuário/senha/remetente e `EXAME_PERTO_EMAIL_ENABLED=true` somente após homologação. Segredos ficam fora do Git e com backup restrito.
3. **Homologação de e-mail:** usar endereço controlado pelo autor; verificar recebimento, spam, remetente, confirmação única, reenvio, recuperação, bounce e revogação de sessões. Aceitação SMTP não equivale a recebimento.
4. **Dados e recuperação:** PostgreSQL e objetos privados com permissões restritas; backup consistente, manifesto/checksums, diário separado e ensaio isolado de restauração com reaplicação de expurgos/encerramentos antes de liberar acesso. Não sobrescrever banco ou arquivos do autor.
5. **Desligamento padrão:** pagamentos, repasses, mapas, GPS, WebSocket operacional, benefícios, workers externos, webhooks e qualquer envio não homologado permanecem desabilitados; a UI deve dizer “indisponível”, nunca inventar resultado.
6. **Custos a pesquisar e decisões do autor:** hospedagem/DB, armazenamento e egress, SMTP, domínio/TLS, backups/PITR, gestão de chaves, monitoramento, retenção de logs e eventual provedor de objetos. O autor precisa escolher fornecedores, orçamento, região, RPO/RTO, endereço de homologação e aprovar políticas/responsáveis; nenhum preço ou prazo é assumido aqui.
7. **Mobile separado:** aparelho Android, permissões/background, bateria, rede intermitente, alinhamento Expo e auditoria de dependências permanecem fora da publicação web.

O portfólio V1 pode apresentar acesso, perfil, família e privacidade como fluxos reais condicionais, mas não deve oferecer cadastro real antes da homologação de e-mail nem anunciar entrega, benefício, rastreamento ou pagamento. A verificação Axe desta rodada cobre apenas os painéis novos em 390×844; os painéis legados ainda têm pendências de rotulagem e não foram declarados acessíveis integralmente.
