# Auditoria das dependências mobile — ticket 05

Data da análise: 05/10/2026. Base: `fd186a4`. Comando de entrada: `npm audit --omit=dev --json`, no diretório `mobile/`, com npm 11.19.0 e Node.js 24.19.0.

## Como interpretar o relatório

O relatório inicial tinha 29 **nós de dependência sinalizados** (21 altos e 8 moderados), não 29 vulnerabilidades independentes nem 29 falhas demonstradas como exploráveis. Só `expo` e `react-native` eram dependências diretas entre os nós sinalizados. Os outros 27 eram transitivos. Embora o npm os conte como produção porque Expo e React Native estão em `dependencies`, os caminhos vulneráveis encontrados executam no host de desenvolvimento/build: CLI, Metro, prebuild/configuração, assinatura, suporte Xcode e infraestrutura Jest. Não foi encontrado um caminho dos advisories até o código JavaScript executado pelo usuário no app exportado.

Essa classificação reduz a exposição observada, mas não prova ausência de exploração: não houve análise formal de todos os fluxos internos dos pacotes nem teste ofensivo. Entradas não confiáveis em arquivos, globs, CSS/source maps, certificados ou configuração durante desenvolvimento/CI ainda poderiam alcançar algumas rotas. Por isso os alertas remanescentes continuam registrados.

Rotas abreviadas usadas na tabela:

- `E`: projeto → `expo` → `@expo/cli`/configuração/prebuild/Metro.
- `R`: projeto → `react-native` → CLI comunitário/Metro ou infraestrutura Jest.
- `M`: `E` ou `R` → Metro → pacote folha.

## Os 29 nós iniciais

| Severidade | Pacote sinalizado | Relação e caminho afetado | Uso observado | Tratamento |
|---|---|---|---|---|
| Alta | `expo` | Direta; raiz de `E` | runtime + ferramentas, mas o advisory chega por ferramentas | atualizado 53.0.27 → 57.0.26 |
| Alta | `react-native` | Direta; raiz de `R` | runtime + ferramentas, advisory inicial por CLI/Jest | atualizado 0.79.2 → 0.86.3 |
| Alta | `@expo/cli` | Transitiva; `E` | desenvolvimento/build | atualizado; ainda agregado por advisories remanescentes |
| Alta | `@expo/code-signing-certificates` | Transitiva; `E` → CLI → pacote → `node-forge` | assinatura no build | atualizado; folha continua sem versão corrigida compatível |
| Alta | `@jest/environment` | Transitiva; `R` → Jest | teste/desenvolvimento | rota removida pela atualização do RN |
| Alta | `@jest/fake-timers` | Transitiva; `R` → Jest → pacote | teste/desenvolvimento | rota removida pela atualização do RN |
| Alta | `@jest/transform` | Transitiva; `R` → Jest → pacote → `micromatch`/`braces` | teste/desenvolvimento | rota Jest removida; `braces` persiste via Metro |
| Alta | `@react-native/community-cli-plugin` | Transitiva; `R` → plugin → Metro | desenvolvimento/build | atualizado; ainda agregado pela rota Metro |
| Alta | `babel-jest` | Transitiva; `R` → Jest → transform | teste/desenvolvimento | rota removida pela atualização do RN |
| Alta | `braces` | Transitiva; `M` → `micromatch` → `braces` | glob do bundler/watch | permanece em 3.0.3; não há versão corrigida publicada no relatório |
| Alta | `image-size` | Transitiva; `M` → `image-size` | leitura de metadados no bundler | removido da árvore; os dois advisories de loop não aparecem no audit final |
| Alta | `jest-environment-node` | Transitiva; `R` → Jest | teste/desenvolvimento | rota removida pela atualização do RN |
| Alta | `jest-haste-map` | Transitiva; `R` → Jest → `micromatch` | teste/desenvolvimento | rota removida pela atualização do RN |
| Alta | `jest-message-util` | Transitiva; `R` → Jest → `micromatch` | teste/desenvolvimento | rota removida pela atualização do RN |
| Alta | `metro` | Transitiva; `E`/`R` → Metro | desenvolvimento/build | atualizado; ainda agregado por `braces` |
| Alta | `metro-config` | Transitiva; `E`/`R` → Metro config | desenvolvimento/build | atualizado; ainda agregado por `braces` |
| Alta | `metro-file-map` | Transitiva; `M` → file map → `micromatch` | desenvolvimento/build/watch | atualizado; ainda agregado por `braces` |
| Alta | `metro-transform-worker` | Transitiva; `M` → transform worker | desenvolvimento/build | atualizado; ainda agregado pela rota Metro |
| Alta | `micromatch` | Transitiva; `M` → `micromatch` → `braces` | glob do bundler/watch | atualizado para 4.0.8; agregado ainda presente pela folha `braces` 3.0.3 |
| Alta | `node-forge` | Transitiva; `E` → CLI/code signing → `node-forge` | geração/verificação criptográfica no build | permanece em 1.4.0; advisory não aponta versão corrigida |
| Alta | `postcss` | Transitiva; `E` → Metro config → `postcss` | transformação CSS/source maps no build | atualizado 8.4.49 → 8.5.29; quatro advisories iniciais não aparecem no audit final |
| Moderada | `@expo/config` | Transitiva; `E` → config | desenvolvimento/build | atualizado; ainda agregado pela cadeia Xcode/UUID |
| Moderada | `@expo/config-plugins` | Transitiva; `E` → config plugins → `xcode` → `uuid` | prebuild nativo | atualizado; ainda agregado pela folha `uuid` |
| Moderada | `@expo/metro-config` | Transitiva; `E` → Metro config | desenvolvimento/build | atualizado; o nó final passou a alto por agregação de `braces` |
| Moderada | `@expo/prebuild-config` | Transitiva; `E` → prebuild config | prebuild nativo | atualizado; ainda agregado pela cadeia Xcode/UUID |
| Moderada | `expo-asset` | Transitiva; `expo` → asset → constants/config | runtime do módulo + configuração; advisory pela configuração | atualizado; não aparece no audit final |
| Moderada | `expo-constants` | Transitiva; `expo`/asset → constants → config | runtime do módulo + configuração; advisory pela configuração | atualizado; não aparece no audit final |
| Moderada | `uuid` | Transitiva; `E` → config plugins → `xcode` → `uuid` | manipulação do projeto iOS no prebuild | permanece em 7.0.3; `xcode` restringe a série e não há correção compatível declarada |
| Moderada | `xcode` | Transitiva; `E` → config plugins → `xcode` → `uuid` | prebuild iOS | permanece agregado pela folha `uuid` |

## Atualização compatível executada

Foi seguido o guia oficial de atualização incremental do Expo, um SDK por vez: 53 → 54 → 55 → 56 → 57. Em cada etapa as versões foram confrontadas com `npx expo install --check`/`--fix` e `npx expo-doctor`; não foi usado `npm audit fix --force`, `--legacy-peer-deps` ou override.

As notas oficiais justificaram duas mudanças materiais: `newArchEnabled` foi removido do `app.json`, pois SDK 55+ só suporta a Nova Arquitetura; e o SDK 56 não foi mantido porque o doctor identificou a regressão conhecida de memória do Hermes e recomendou SDK 57 com React Native corrigido. Estado final compatível: Expo 57.0.26, React 19.2.3, React Native 0.86.3, Document Picker 57.0.3, File System 57.0.7, Secure Store 57.0.4, TypeScript 6.0.3 e `@types/react` 19.2.18. O lockfile npm v3 foi preservado e regenerado por instalação normal.

Fontes oficiais consultadas: [guia de atualização do Expo](https://docs.expo.dev/workflow/upgrading-expo-sdk-walkthrough/), [SDK 54](https://expo.dev/changelog/sdk-54), [SDK 55](https://expo.dev/changelog/sdk-55), [SDK 56](https://expo.dev/changelog/sdk-56) e [SDK 57](https://expo.dev/changelog/sdk-57).

## Resultado e exposição remanescente

O audit final reporta 23 nós (16 altos e 7 moderados, zero críticos). Isso não significa que somente seis falhas foram corrigidas: a árvore nova eliminou os dois advisories de `image-size`, os quatro de `postcss` e os caminhos Jest antigos, mas passou a enumerar outros nós agregadores da árvore recente. Os 23 nós finais convergem para três advisories folha ainda reportados:

| Advisory/folha | Caminho final | Exposição observada | Situação |
|---|---|---|---|
| `braces` 3.0.3, GHSA-vfj7-8cjw-p6xm | Expo/RN → Metro/file map → `micromatch` 4.0.8 → `braces` | DoS por padrão de glob profundamente aninhado no host de desenvolvimento/build; nenhuma entrada de glob do usuário final foi identificada | sem versão corrigida indicada; aguardar atualização compatível do Expo/RN/Metro |
| `node-forge` 1.4.0, GHSA-86w9-cpqp-85rv | Expo → CLI/code-signing-certificates → `node-forge` | verificação de assinatura no tooling; não é a autenticação/sessão do app | sem versão corrigida indicada; não substituir criptografia nem fazer override incompatível |
| `uuid` 7.0.3, GHSA-w5hq-g745-h8pq | Expo → config plugins → `xcode` 3.0.1 → `uuid` | prebuild iOS; métodos v3/v5/v6 com buffer fornecido não foram chamados pelo app | correção exigiria romper a faixa de `xcode`; aguardar upstream compatível |

O próprio audit sugere downgrades para Expo 44.0.6 e React Native 0.72.17. Eles são incompatíveis com a matriz oficial do SDK 57 e reintroduziriam stacks antigas; portanto não são correções aceitáveis. Os alertas acima não foram ignorados, suprimidos nem classificados como resolvidos. Antes de distribuição, repetir o audit e a análise de alcance, atualizar quando houver release estável compatível e executar o roteiro em aparelho/emulador.

## Rechecagem no ticket 15 — 08/10/2026

O typecheck e o export Android voltaram a passar (590 módulos, bundle de 1,5 MB), ainda sem execução em aparelho. `npm audit --omit=dev` manteve 23 nós sinalizados: 16 altos, 7 moderados e nenhum crítico, nas mesmas cadeias de tooling descritas acima. Após excluir `.expo` do Git, `expo-doctor` aprovou 20 de 21 verificações; a restante aponta `expo-location` 19.0.8 quando o SDK espera `~57.0.20` e Expo 57.0.26 quando espera `~57.0.27`. `expo install --check` confirmou as mesmas divergências. Nenhum downgrade, override ou atualização não revisada foi aplicado. A correção compatível, novo audit/doctor e o ensaio em aparelho permanecem bloqueios de publicação.
