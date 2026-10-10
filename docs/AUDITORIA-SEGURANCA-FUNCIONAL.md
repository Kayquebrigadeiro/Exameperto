# Auditoria de segurança e funcionamento integrado

Data: 09/10/2026 · base inicial `e11b1c1` · branch `docs/planejamento-tecnico` · orçamento R$ 0.

Esta revisão usa somente dados sintéticos em Testcontainers, arquivos temporários e servidores locais. Não houve deploy, cobrança, contratação, envio de e-mail ou acesso a dados reais. `referencia.md` e `.vscode/` foram preservados.

## Base, escopo e plano

Ambiente registrado: Linux 6.19.14 Kali x86_64; Java 25.0.3 instalado (Maven executa com Java 21.0.12.1, alvo do `pom.xml`); Maven 3.9.12; Node 24.19.0; npm 12.0.2; Docker 28.5.2; Chromium/Playwright 1.63. Comandos principais: `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 test`, a mesma suíte com `-DbrowserTest=true`, `npm run build && npm test --prefix web`, `npm run typecheck && npm run build:android --prefix mobile`, `npm audit --omit=dev --json` e `npx expo-doctor`.

O recorte V1 navegável é perfil, privacidade/encerramento e gestão familiar. Cadastro real depende de SMTP, chaves, origem HTTPS, política e storage/backup aprovados. Entrega, benefício, GPS, custódia, cobrança, reserva, repasse e painéis operacionais são código legado protegido por backend; não entram na navegação V1 e só são exercitados por opt-ins de teste explícitos. Provedores financeiros, rota, SMTP externo, antimalware, backup operacional e aparelho móvel continuam externos/bloqueados.

Prioridade aplicada: (P0) limites antes de desserialização, bloqueio de efeitos durante restauração e autorização/segredos; (P1) concorrência, idempotência, migrações e fluxos integrados; (P1) dependências e builds; (P2) acessibilidade, documentação e prontidão operacional.

## Achados corrigidos

### F-001 — Alta — corpo ilimitado no webhook público

Reprodução: `POST /api/v1/integrations/payments/events` é público para o adaptador, e o controlador chamava `readAllBytes()` antes de verificar se o provedor estava configurado. Um corpo chunked sem `Content-Length` podia alocar arbitrariamente, inclusive no estado padrão sem provedor. Impacto: esgotamento de heap/threads por requisições não autenticadas.

Correção: `RequestBodyLimitFilter` limita o endpoint a `payment.max-event-payload-bytes` (padrão 65536), rejeita `413` antes do MVC e mantém a assinatura sobre os bytes originais limitados. O contrato OpenAPI declara `maxLength: 65536`. Regressão em `QuoteAcceptanceFlowTest` usa corpo chunked de 65537 bytes e confirma `413` sem chamar o adaptador.

### F-002 — Média — limite de GPS só após desserialização

Reprodução: o controlador verificava `Content-Length` depois de `@RequestBody`; um cliente autenticado podia omitir o comprimento e enviar JSON maior que 1 KiB para fazer Jackson trabalhar antes da recusa. Correção: o mesmo filtro limita `/orders/{id}/locations` antes da desserialização, inclusive chunked, usando `tracking.max-payload-bytes`; o controlador mantém a defesa de contrato. `TrackingFlowTest` cobre comprimento conhecido e chunked.

### F-003 — Média — worker de e-mail consultava/mutava a outbox em estados inadequados

Reprodução: o scheduler consultava o banco a cada segundo mesmo com SMTP desligado e, durante restauração, podia chamar `deliver`, que marca a mensagem como `RECONCILIAR` antes de falhar no gate. Isso gerava consultas desnecessárias e alterava a outbox restaurada.

Correção: `AccountMailWorker` retorna sem consultar quando `EmailGateway.configured()` é falso e consulta `RecoveryGuard` antes de buscar jobs; falha de banco continua sanitizada no bloco de captura. `AccountMailWorkerTest` prova os dois bloqueios e a entrega apenas quando ambos estão liberados.

### F-004 — Média, não corrigida — alertas transitivos no toolchain mobile

`npm audit --omit=dev` encontrou 23 nós (16 altos, 7 moderados, zero críticos), convergindo para `braces` 3.0.3 (GHSA-vfj7-8cjw-p6xm), `node-forge` 1.4.0 (GHSA-86w9-cpqp-85rv) e `uuid` 7.0.3 (GHSA-w5hq-g745-h8pq), em Expo/Metro/configuração de build. Os caminhos observados são tooling/prebuild/watch, não foram demonstrados no bundle de produção. O `npm audit fix --force` sugeriria downgrades incompatíveis; nenhum override foi aplicado. Repetir após release compatível e antes de distribuição.

## Controles revisados

Autenticação usa JWT HS256 com issuer/audience/expiração, sessão e conta consultadas a cada requisição; refresh rotaciona e replay revoga a família; logout, recuperação e fechamento revogam sessões. MFA é TOTP com falha/rate/replay e elevação curta para papéis privilegiados. CSRF de cookie, Origin exata e tokens em memória/SecureStore são separados por cliente. CORS não aceita curingas; `forward-headers-strategy=none` evita confiar em `X-Forwarded-*`; rate limit é por processo, rota/IP e global, portanto réplicas ainda exigem coordenação externa.

Autorização foi conferida nos fluxos de registro, instituição, papel e escopo familiar, inclusive revalidação em GET/SUBSCRIBE/publicação WebSocket e remoção de conexões após logout, expiração, revogação ou encerramento. DTOs rejeitam campos desconhecidos; SQL é parametrizado ou usa identificadores internos fixos. Não foram encontrados `innerHTML`, `eval`, redirects arbitrários ou segredos no código rastreado. Erros/logs são sanitizados; chaves de cifra, busca e assinatura são externas e distintas.

Uploads usam raiz privada POSIX, normalização, `NOFOLLOW`, diretórios 0700/arquivos 0600, quarentena, magic bytes e inspeção estrutural. Isso não é antimalware nem autenticação documental; download passa por autorização do proprietário. Encerramento, expurgo, locks, `If-Match`, idempotência, outbox/inbox e triggers preservam invariantes; V16/V17 bloqueiam HTTP e efeitos externos durante restore e reaplicam o diário antes de liberar acesso. Limites: rate limit não é distribuído, checksum não autentica backup e não há RPO/RTO/fornecedor/aparelho homologados.

## Validação executada

- Suíte final `mvn -DbrowserTest=true test`: 64/64 aprovados, 0 falhas/erros/skips; Flyway novo V1–V17 e PostgreSQL 17.6 descartável. A execução focal pós-correção (`QuoteAcceptanceFlowTest` 4, `TrackingFlowTest` 2 e `AccountMailWorkerTest` 3) também foi 9/9.
- Opt-in browser corrigido para legado: `AcceptanceBrowserFlowTest -DbrowserTest=true` passou com Chromium → Vite → API → PostgreSQL. Os painéis legados só aparecem quando `VITE_ENABLE_OPERATIONAL_TEST_PANELS=true` em desenvolvimento; o build de produção remove esse ramo e a navegação V1 continua sem ele.
- Web: build passou; Playwright comum passou 2 e ignorou 8 testes que exigem PostgreSQL/credenciais opt-in. Axe passou no formulário e nos fluxos V1 registrados; isso não substitui leitor de tela, zoom/reflow, alto contraste, outros navegadores ou uso prolongado por teclado.
- Mobile: typecheck e export Android (590 módulos, bundle 1,5 MB) passaram; `expo-doctor` 20/21, falhando pela versão esperada de `expo-location` e patch do Expo. `adb`/`emulator` não estão instalados: GPS, segundo plano, permissões, bateria e uso físico permanecem não verificados.
- Migrações: banco novo aplicado V1–V17; upgrade V5→versão atual exercitado por `VehicleLinkFlowTest`; restauração/expurgo e concorrência financeira cobertos pelos fluxos existentes. `openapi-spec-validator 0.7.2`, links Markdown e `git diff --check` passaram.
- Endor Labs: binário instalado localmente com SHA-256 verificado (`efd51b…35d4f`), mas execução bloqueada: não há credencial e o namespace não foi fornecido. Não foi inventado namespace nem feito login. Não há scanner JVM/infra local equivalente configurado.

## Prontidão e riscos remanescentes

V1 local está tecnicamente pronta para homologação condicionada: perfil, privacidade, família, sessão e acessibilidade básica têm evidência sintética; cadastro real continua bloqueado até SMTP/remetente/caixa de teste, chaves, HTTPS/origem, política e armazenamento/backup. Operação completa não está pronta: faltam provedores e contratos, responsáveis/MFA operacional, retenção financeira/GPS, antimalware, backup cifrado/autenticado, RPO/RTO, observabilidade, coordenação de rate limit entre réplicas, mobile físico e atualização dos alertas mobile.

Limitações importantes: nenhuma evidência local prova entrega de e-mail, DNS/DMARC, bounce, TLS público, cobrança, repasse, rota, precisão GPS, falha de host/região ou escala. Os adaptadores e mailboxes dos testes são mocks controlados e não representam fornecedores. Não declarar “tudo funciona” nem habilitar políticas/integrações externas com base nesta auditoria.

Próxima ação concreta: o autor deve fornecer por canal privado um SMTP já controlado, remetente verificado, destinatário de homologação e autorização específica de envio; depois configurar somente localmente, executar cadastro → confirmação → recuperação, registrar recebimento separado da aceitação SMTP e revisar os limites antes de qualquer decisão de publicação.
