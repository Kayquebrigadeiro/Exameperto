# Relatório de prontidão — ticket 15

Data: 08/10/2026. Base autorizada: `880b457`. Escopo: implementação e verificação local das partes independentes; sem deploy, dados reais, contratação ou operação externa.

## Resultado

O recorte local implementa solicitações próprias de acesso, correção e exclusão, com identificação pela sessão ativa, idempotência vinculada ao conteúdo, protocolo e acompanhamento. Resposta/negação, autorização do expurgo, execução e verificação são estados distintos. Operadores precisam de conta nominal, papel AO vigente e MFA de sessão em cada transição privilegiada.

Políticas são versionadas por categoria e exigem finalidade, gatilho, prazo explícito, base/finalidade validada, verificação de descarte e responsável ativo. Uma versão desativada ou futura não autoriza execução; somente uma versão ativa por categoria pode existir. A aplicação não contém política semeada e o expurgo fica desligado por padrão. Os prazos propostos em D08 não foram promovidos a configuração operacional.

O inventário de expurgo registra banco, objetos, versões, temporários, filas, caches, referências, dispositivos, fornecedores e backups. O executor local remove objetos documentais não financeiros, saneia suas linhas sem quebrar FKs, elimina posições GPS relacionadas, remove outbox de conta não financeira e encerra conexões de rastreamento em cache. Retry sob lock é idempotente e gera tombstone sem conteúdo. Documentos e referências financeiras, operações, outbox financeira e deduplicação são preservados; a retenção financeira continua pendente.

`VERIFICADA` significa somente que a ausência foi reconferida nos recursos locais efetivamente controlados. Temporários sem vínculo por conta, dispositivos, fornecedores e backups ficam como `PROCEDIMENTO_PENDENTE`; versão no armazenamento local atual é `NAO_APLICAVEL`; finanças ficam `PRESERVADO`. Esses estados não comprovam exclusão externa.

## Evidências locais

- `PrivacyFlowTest`, HTTP real + Spring + PostgreSQL 17.6/Testcontainers + armazenamento privado temporário: 3 cenários aprovados.
- Cobertura: outra conta e familiar com concessão limitada a `PEDIDOS`, sem escopo de privacidade, recebem 404; sessão revogada recebe 401; ACESSO/CORRECAO/EXCLUSAO, respostas de acesso/correção e replay idempotente; política ausente, futura/desativada e responsável inativo; exceção de conservação; resposta distinta de execução; papel AO revogado durante o processamento; falha de alvo e retry; duas execuções concorrentes; execução repetida; verificação posterior; ausência de objeto, metadado documental, posição GPS e fila controlada; tombstone único; preservação de documento financeiro, operação, outbox financeira e chave de idempotência.
- Flyway aplicou V1–V15 em banco novo no teste específico. O teste de upgrade V5→V15 está na suíte `VehicleLinkFlowTest`.
- Configuração padrão conferida: `EXAME_PERTO_PRIVACY_PURGE_ENABLED=false`, assim como e-mail e rastreamento; chaves de cifra/busca/assinatura não têm valor padrão.
- Objetos privados usam diretório `0700` e arquivo `0600` quando POSIX está disponível, coberto por `PrivateObjectStoreTest`.
- Suíte Maven comum: 57 testes descobertos, 49 aprovados, 0 falhas/erros e 8 opt-ins de navegador ignorados. Os 8 opt-ins foram executados separadamente e passaram com Chromium, API e PostgreSQL descartável, incluindo o upgrade V5→V15.
- Web: build aprovado; Playwright comum com 2 testes aprovados e 8 integrações opt-in ignoradas. A mensagem genérica preserva agora a causa sanitizada da indisponibilidade em vez de presumir transferência financeira para qualquer integração.
- Mobile: typecheck e export Android aprovados, com 590 módulos e bundle de 1,5 MB; isso não equivale a execução em aparelho. `expo-doctor` aprovou 20/21 verificações e manteve incompatibilidades em `expo-location` 19.0.8 (esperado `~57.0.20`) e Expo 57.0.26 (esperado `~57.0.27`).
- Contrato OpenAPI 3.0.3 validado com `openapi-spec-validator 0.7.2`: 126 operações/`operationId` únicos e 1.095 referências internas resolvidas, sem chave YAML duplicada. Os 104 links Markdown locais existem e os 17 blocos Mermaid passaram em `mermaid.parse` 12.0.0.
- Git/configuração: varredura de padrões de chave privada e tokens não encontrou segredo rastreado; `.env`, dependências, builds, relatórios Playwright, `.expo` e objetos sintéticos ficam fora do Git. A árvore de dependências JVM resolveu, mas não há scanner de CVEs JVM configurado.
- Dependências: web sem vulnerabilidades em `npm audit --omit=dev`; mobile mantém 23 nós sinalizados (16 altos, 7 moderados, 0 críticos) no tooling Expo, convergindo para `braces`, `node-forge` e `uuid`. O downgrade incompatível sugerido pelo audit não foi aplicado.

Estas são evidências locais, não homologações de fornecedor nem prontidão de produção.

## Pendências e bloqueios de publicação

- Não há política real aprovada, organização/controlador/operadores definidos nem responsável operacional nomeado; a política sintética existe somente no banco descartável do teste.
- Retenção financeira, janela de disputa/replay e descarte das chaves de deduplicação continuam sem regra validada. Não há retenção infinita adotada: a categoria permanece preservada e bloqueada para decisão.
- Não há inventário operacional completo por categoria nem scheduler para gatilhos temporais de D08; V15 executa o gatilho explícito da solicitação respondida da conta.
- O recorte elimina documentos/GPS/fila/cache vinculados, mas não implementa anonimização integral de todos os dados cadastrais da conta; qualquer ampliação exige inventário e regra aprovados por categoria.
- Backups/restauração: o modelo registra tombstone e exige reaplicação antes do acesso, mas não existe infraestrutura de backup isolada disponível neste repositório para comprovar restore, RPO/RTO ou rotação. Publicação deve continuar bloqueada até procedimento automatizado e ensaio de restauração.
- Armazenamento versionado, temporários órfãos, dispositivos e fornecedores dependem de procedimento/contrato e evidência no ambiente escolhido. Nenhuma exclusão nesses locais foi declarada.
- Homologações de e-mail, objetos/antimalware, identidade, mapas, pagamentos e repasses continuam externas. Sandbox ou teste controlado não comprova operação produtiva.
- Testes mobile físicos do ticket 13 continuam pendentes: Android real/development build, permissões, segundo plano, bateria e perda de rede. Eles não são substituídos pelos testes HTTP/WebSocket.
- Faltam teste de penetração, observabilidade/infraestrutura, recuperação privilegiada e revisão humana das políticas. Não autorizar deploy com base somente neste relatório.

## Decisão de prontidão

O recorte independente do ticket 15 pode ser revisado e mantido na branch documental. O ticket permanece parcial e o projeto **não está pronto para produção ou deploy**.
