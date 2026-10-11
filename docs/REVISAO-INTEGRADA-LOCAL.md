# Revisão integrada para uso local — 11/10/2026

Base da revisão: `a287b36`. O objetivo é distinguir o que pode ser usado pelo navegador e aplicativo reais do que existe apenas como API, teste automatizado ou desenho condicionado. “Funciona de ponta a ponta” abaixo significa fluxo exercitado por navegador/app → API → PostgreSQL em teste integrado; SMTP externo e aparelho Android são indicados separadamente e não são inferidos desses testes.

## Diagnóstico do HTTP 503

O frontend enviava `POST /api/v1/auth/register` porque a página abria no modo **Solicitar cadastro**, não no modo **Entrar**. A função de submissão não troca login por cadastro: ela envia o valor do modo visível. O acesso à conta agora começa em login e cadastro exige seleção explícita; há regressão da rota emitida.

O cadastro tem somente dois caminhos próprios para 503: `RESTORE_BLOCKED`, emitido antes do controller pelo gate de restauração, ou `INTEGRATION_UNAVAILABLE`, quando configuração/chaves/SMTP não estão disponíveis ou o transporte rejeita o envio. No ambiente inspecionado:

- Vite respondeu pela origem `127.0.0.1:5173` e encaminhou à API em `127.0.0.1:8080`;
- PostgreSQL estava acessível, Flyway V1–V17 estava aplicado e `controle_restauracao.estado` era `NORMAL`;
- o processo Java correto possuía as variáveis privadas necessárias, sem valores expostos;
- Gmail em porta 587, autenticação, STARTTLS obrigatório, verificação de hostname, remetente e allowlist única estavam configurados; a autenticação SMTP sem envio foi aceita;
- o banco continha zero contas, compatível com o rollback do cadastro quando o envio falha;
- a requisição inválida repetida pelo mesmo caminho retornou `400 INVALID_INPUT`, provando que o gate já não estava bloqueando e que o proxy alcançava o backend.

O corpo original do 503 não foi conservado pelo navegador e os logs antigos não tinham diagnóstico seguro do ramo interno, portanto não é correto escolher retroativamente entre configuração ausente e rejeição do transporte. A evidência demonstra que o 503 foi do limite de cadastro/e-mail ou do gate, não de “login”, e elimina o gate no estado atual. A correção acrescenta log sanitizado correlacionável (`ACCOUNT_CONFIGURATION_MISSING` ou `EMAIL_TRANSPORT_REJECTED`) sem endereço, senha, token ou conteúdo. Um novo 503 passa a ser diagnosticável pelo `correlationId` público.

## Matriz de fluxos

| Perfil / fluxo | Superfície | Classificação atual | Evidência e limite real |
|---|---|---|---|
| Conta: cadastro → confirmação → login → perfil → recuperação → troca → logout | Web e API | Funciona de ponta a ponta, condicionado ao e-mail | `BrowserFlowTest` percorre o fluxo inteiro com PostgreSQL real descartável e fronteira de e-mail de teste; login, refresh, reload e logout passam pelo Vite/API. No banco local real ainda não há conta, e o envio Gmail completo aguarda o usuário submeter sua conta e confirmar os códigos privados. |
| Conta: reload e sessão expirada | Web e API | Falha técnica corrigida | A web agora restaura automaticamente a sessão por refresh HttpOnly + CSRF e remove os painéis quando o access expira. Tokens continuam fora de `localStorage`/`sessionStorage`. |
| Perfil básico e privacidade do titular | Web e API | Funciona de ponta a ponta | Leitura/edição com ETag, protocolos e estados de encerramento são cobertos por navegador e PostgreSQL. A execução de expurgo depende de papel, MFA e política; não é prometida como ação imediata. |
| Paciente e familiar autorizado | Web e API | Funciona de ponta a ponta em teste; depende de participantes reais no uso local | Perfil, convite, aceite, confirmação, escopos, prazo e revogação são cobertos por `FamilyBrowserFlowTest`. Uso normal exige duas contas reais e destinatário expressamente permitido; a allowlist não foi ampliada. |
| Entregador: cadastro, documentos, veículo e vínculo | Web de desenvolvimento, mobile e API | Implementação incompleta para operação | Formulários e APIs existem, persistem e mantêm arquivos privados em quarentena. Critério profissional, responsável nominal e inspeção antimalware abrangente permanecem pendentes; nenhuma aprovação é automática. |
| Entregador: ofertas, designação, custódia, ocorrência e GPS | Somente mobile e API | Depende de decisão ou integração externa | Fluxos transacionais têm testes integrados; a web não os oferece. Uso real exige entregador/veículo aprovados, cobertura, protocolo, tarefa ativa, retenção aprovada, `EXAME_PERTO_TRACKING_ENABLED=true` e aparelho Android. Sem isso a interface informa bloqueio/ausência, sem GPS simulado. |
| Analista operacional: revisão de veículo | Web de desenvolvimento e API | Depende de decisão ou integração externa | Painel chama fila, atribuição, inspeção e decisão; 403/MFA e `POLICY_UNDEFINED` são esperados sem provisionamento nominal e política profissional. O flag visual não cria papel. |
| Analista de benefício | Somente API | Implementação incompleta | Fila, atribuição, inspeção, decisão e recurso existem e são testados, mas não há tela web de análise. Política, critérios/documentos e instituição real continuam pendentes. |
| Analista de cadastro e privacidade privilegiada | Somente API | Implementação incompleta | Rotas protegidas existem; faltam interface própria e procedimento nominal aprovado para uso. Expurgo permanece desabilitado por padrão. |
| Financeiro: aporte e dupla revisão | Web de desenvolvimento e API | Depende de decisão ou integração externa | Persistência, idempotência, segregação entre dois gestores e saldo contábil têm testes. Uso requer gestores nominais, MFA, instituição/programa e evidências reais; nada é provisionado automaticamente. |
| Financeiro: pagamento, repasse e conciliação | Web de desenvolvimento e API | Depende de integração externa | Livro e estados existem, mas provedores padrão estão indisponíveis e retornam `503 INTEGRATION_UNAVAILABLE`. Registro contábil não é transferência; a UI não deve ser usada para criar dinheiro de demonstração. |
| Paciente/familiar: pedido, rota, orçamento, cobertura e acompanhamento | Web de desenvolvimento e API | Depende de decisão ou integração externa | APIs e testes cobrem autorização, concorrência e estados. Unidade/protocolo, tarifa, política de cancelamento, rota e pagamento reais faltam. Sem posição real, a tela informa indisponibilidade e não desenha marcador. |

## Navegação e comportamento

A URL web canônica é <http://127.0.0.1:5173/>. A tela pública abre em **Entrar**; cadastro, confirmação, reenvio e recuperação são escolhas explícitas. Durante sessão ativa, o formulário de autenticação é substituído por um shell navegável com conta, paciente/família, benefícios, pedidos, privacidade e entregador. Áreas privilegiadas derivam de capacidades efetivas e cada endpoint revalida papel, instituição, MFA e registro.

Erros `401`, `403`, `404`, `409`, `422` e `503` continuam sendo apresentados como falha/bloqueio, sem mensagem de sucesso. O recarregamento tenta renovar a sessão pelo cookie; falha silenciosa de cookie ausente mantém a página pública. A origem permanece exata e não há CORS wildcard.

## Iniciar, verificar e parar sem apagar dados

Terminal 1:

```sh
./scripts/start-local-email-homologation.sh
```

Terminal 2:

```sh
./scripts/start-local-web.sh
```

Verificação segura, sem imprimir configuração privada:

```sh
./scripts/verify-local-environment.sh
```

Acesse <http://127.0.0.1:5173/>. Não há flag visual de desenvolvimento. Para avaliar áreas privilegiadas com sua conta, use `./scripts/provision-local-access.sh` e reinicie a sessão; os scripts recusam processos duplicados nas portas 8080 e 5173.

Para parar, use `Ctrl+C` nos dois terminais e preserve o PostgreSQL com:

```sh
docker compose --env-file .env.smtp.local -f infra/compose.yaml stop postgres
```

Não use `down -v`: o volume contém os dados locais e esta revisão não autoriza removê-los.
