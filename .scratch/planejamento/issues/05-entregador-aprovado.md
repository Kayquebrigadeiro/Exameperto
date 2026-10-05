# 05: Entregador envia comprovações e recebe revisão

**What to build:** Entregador usa app para enviar cadastro/documentos privados e acompanha decisão; analista atribuído revisa no painel.

**Blocked by:** 02 — Definir privacidade, acesso privilegiado e provedores, 03 — Cadastrar, autenticar e recuperar conta

**Status:** parcialmente implementado — validação em dispositivo, autenticidade e aprovação profissional bloqueiam conclusão

- [ ] Upload privado com quarentena, limite/tipo real, objeto gerado e download autorizado funciona do app ao armazenamento real isolado local. API → PostgreSQL → armazenamento está verificado; falta executar o trecho iniciado no app instalado.
- [x] Persistência, API e telas distinguem cadastro, inspeção do arquivo e aprovação profissional; falta de provedor não aprova automaticamente.
- [ ] Analista atribuído revisa sem autoaprovação; outras contas não obtêm documentos por ID/URL; foto operacional aprovada é projeção separada.
- [x] Troca de foto/documento cria revisão pendente antes de nova oferta; histórico anterior permanece auditável.

Implementação parcial autorizada sobre a base `966c0b2`, com continuidade técnica autorizada sobre `fd670c8`. Conclusão operacional depende de critérios profissionais e responsáveis reais; armazenamento/consulta externos somente serão exigidos se um requisito concreto os tornar necessários.

## Ajuste D01–D12 — 30/09/2026

D01/D09–D11: revisão humana com evidência mínima, critérios profissionais validados, MFA e segregação. Download/foto via proxy com reautorização/no-store; revogação bloqueia novos acessos. Biometria adiada, integração oficial indisponível não se torna aprovação.

- [x] Verificar os comportamentos e bloqueios independentes acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Comments

02/10/2026 — Implementado cadastro do entregador em `RASCUNHO`, API multipart autenticada, migração V4, armazenamento privado local configurável, quarentena, nomes gerados, validação de assinatura PDF/JPEG/PNG, limite de 10 MiB, hash, histórico de substituição, proxy `no-store` e painel web; app Expo adicionado para cadastro/upload/acompanhamento.

O ensaio PostgreSQL 17.6/Testcontainers + HTTP real + armazenamento local isolado usou apenas documentos sintéticos e verificou quarentena, download bloqueado antes de inspeção, download aprovado por proxy, acesso indevido, tipo inválido, excesso, substituição e fila de revisão. O teste de armazenamento real escreve/lê/remove o objeto em diretório temporário. Não houve emulador Expo nem aparelho físico.

Bloqueio de conclusão: não há critérios profissionais publicados, responsável real provisionado, verificação de autenticidade definida nem ensaio do app em dispositivo. A API exige MFA real vinculado à sessão para atribuição/inspeção, mantém a decisão profissional em `POLICY_UNDEFINED` e não cria aprovação fictícia, conta administrativa padrão ou revisor fictício. O status, antes 503 nesta rodada histórica, foi corrigido para 422 na consolidação abaixo. Não apresentar a inspeção estrutural como autenticidade/consulta oficial. Não iniciar ticket 06 nem deploy.

05/10/2026 — Continuidade técnica a partir de `fd670c8`:

- **Implementação ausente:** decisão profissional e projeção de foto aprovada; processo de autenticidade documental; política/orquestração de retenção e expurgo; provisionamento/recuperação independente de contas privilegiadas. Essas ausências preservam o bloqueio operacional.
- **Implementação existente, mas não testada no ambiente:** app Expo em emulador/aparelho, inclusive SecureStore, seleção/upload nativo, erros de rede e acompanhamento visual. Typecheck e export Android passaram; não havia `adb`, emulador ou aparelho disponível. Instruções reproduzíveis estão em `mobile/README.md`. O audit das dependências móveis ainda reporta 21 alertas altos e 8 moderados, sem correção compatível automática indicada; atualização/migração do SDK deve ser avaliada antes de qualquer distribuição.
- **Dependência externa para operação real:** critérios profissionais aprovados e responsáveis reais; armazenamento/backup/restauração e varredura antimalware de produção conforme requisitos de deploy; eventual consulta oficial de autenticidade somente se escolhida como requisito. Nenhum fornecedor é exigido apenas para o ensaio local.

Implementado TOTP para o AO com segredo cifrado, reautenticação no enrollment, confirmação, anti-reuso, cinco falhas/15 minutos, elevação por sessão por cinco minutos e invalidação por rotação/logout/revogação. Chamada direta de atribuição sem MFA retorna 403; sessão revogada retorna 401. Atribuição impede autoanálise. A inspeção estrutural local do arquivo, também protegida por MFA, valida fechamento mínimo de PDF/JPEG/PNG e rejeita recursos ativos conhecidos de PDF, mas não verifica autenticidade documental nem substitui aprovação profissional.

O armazenamento local foi testado com diretórios `0700` e objetos `0600` em POSIX, chave gerada/normalizada, recusa de traversal/symlink, leitura somente pelo proxy autorizado e exclusão física na fronteira. Isso demonstra o adaptador local isolado, não operação externa, backup, restauração ou expurgo regulado.

Os dois opt-in foram executados explicitamente, sem contar os skips da suíte comum:

- `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -Dtest=BrowserFlowTest -DbrowserTest=true test`: validar conta/sessão pelo caminho Chromium → Vite → API → PostgreSQL; 1 teste executado, 0 falhas, 0 ignorados.
- `mvn -f backend/pom.xml -Dmaven.repo.local=/tmp/exame-m2 -Dtest=FamilyBrowserFlowTest -DbrowserTest=true test`: validar autorização familiar, sessão aberta e revogação pelo mesmo caminho; 1 teste executado, 0 falhas, 0 ignorados.

Nenhuma mensagem externa foi enviada. Os adaptadores de e-mail dos ensaios são exclusivos de teste. O fluxo HTTP real do entregador chegou a PostgreSQL e ao armazenamento local com dados sintéticos; o trecho iniciado pela UI móvel continua pendente por falta de dispositivo/emulador.

05/10/2026 — Dependências mobile e pendências consolidadas a partir de `fd186a4`:

- Expo foi atualizado incrementalmente de SDK 53 para 57, com React Native 0.86.3 e módulos alinhados pelo mapa oficial. `expo install --check` passou e `expo-doctor` passou em 21/21 verificações. A passagem pelo SDK 56 revelou a regressão conhecida do Hermes; o SDK 57 foi adotado conforme recomendação do próprio doctor. Não houve `--force`, override ou troca da stack; o lockfile npm v3 foi preservado.
- O [relatório mobile](../../../docs/AUDITORIA-DEPENDENCIAS-MOBILE.md) decompõe os 21 alertas altos e 8 moderados iniciais por relação, caminho e uso. O audit final registra 16 altos e 7 moderados, zero críticos, concentrados em três advisories folha do toolchain (`braces`, `node-forge`, `uuid`) sem correção estável compatível indicada. Eles não foram ignorados nem declarados resolvidos; a contagem de nós não representa contagem de explorações.
- `npm ci`, alinhamento Expo, doctor, typecheck e export Android passaram. O teste integrado do entregador passou com 2 casos sobre HTTP/PostgreSQL/filesystem reais isolados. A ausência de `adb`, `emulator` e `xcrun` mantém a instalação e o fluxo UI móvel → API → PostgreSQL → armazenamento pendentes; o roteiro detalhado está em `mobile/README.md`.
- `POLICY_UNDEFINED` da decisão profissional foi alinhado para `422` na implementação, OpenAPI e teste. `503 INTEGRATION_UNAVAILABLE` permanece reservado a capacidade/integração indisponível, como inspeção de arquivo desabilitada. A aprovação profissional continua bloqueada; nenhum cadastro foi aprovado.

Pendências consolidadas para concluir o ticket 05:

- [ ] Executar o roteiro em emulador/aparelho Android, incluindo instalação, SecureStore, seletor/upload nativo, falhas de rede, refresh/logout e acompanhamento visual.
- [ ] Definir e publicar critérios profissionais, provisionar responsável real com MFA e implementar decisão/transições e projeção separada da foto aprovada, preservando segregação e recurso independente quando aplicável.
- [ ] Definir se autenticidade documental/consulta oficial é requisito e, somente então, selecionar e homologar a integração; inspeção estrutural de arquivo não é autenticidade nem varredura antimalware.
- [ ] Definir retenção/expurgo e requisitos operacionais de armazenamento, backup, restauração e antimalware antes de dados reais/deploy.
- [ ] Acompanhar releases compatíveis de Expo/React Native/Metro/Xcode e repetir audit/análise de alcance para os três advisories transitivos remanescentes antes de distribuição.

Estado permanece **parcial e bloqueado para aprovação profissional**. Não iniciar ticket 06, distribuir aplicativo ou fazer deploy.
