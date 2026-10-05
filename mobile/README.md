# App do entregador

Expo/React Native para cadastro, upload privado e acompanhamento de estado. O app usa somente a API autenticada; não contém credenciais, aprovação local ou armazenamento de documentos. `EXPO_PUBLIC_API_URL` aponta para o backend no ambiente de desenvolvimento.

O refresh móvel fica no SecureStore, é rotacionado pela API e removido no logout. O app consulta o histórico após upload para acompanhar quarentena/inspeção; nenhum estado local aprova o cadastro.

## Validação reproduzível em Android

Pré-requisitos: Node.js >= 20.19.4, npm, JDK 21, Docker, Android Studio/SDK com `adb` e um AVD iniciado ou aparelho com depuração USB. Use somente contas e documentos sintéticos em ambiente isolado.

1. Na raiz, suba PostgreSQL com `docker compose -f infra/compose.yaml up -d` e inicie o backend com as chaves privadas de desenvolvimento e a política de cadastro de teste. Configure `EXAME_PERTO_PRIVATE_ROOT` para um diretório temporário privado. Não habilite e-mail externo.
2. Neste diretório, execute `npm ci`, `EXPO_NO_TELEMETRY=1 npx expo install --check`, `EXPO_NO_TELEMETRY=1 npx expo-doctor`, `npm run typecheck` e `EXPO_NO_TELEMETRY=1 npm run build:android`. O export só verifica o bundle; não conta como instalação ou uso.
3. Confirme o destino com `adb devices`. Para um development build instalado localmente, defina `EXPO_PUBLIC_API_URL=http://10.0.2.2:8080/api/v1` no emulador (ou `http://IP_LAN_DO_COMPUTADOR:8080/api/v1` no aparelho) e execute `EXPO_PUBLIC_API_URL=... npx expo run:android`. Aceite apenas as mudanças nativas geradas localmente; não distribua o APK.
4. Abra o app instalado, entre com uma conta sintética criada no ambiente isolado, feche/reabra para verificar a restauração pelo SecureStore, salve o perfil, envie PDF/JPEG/PNG sintético pelo seletor nativo e use “Atualizar acompanhamento”.
5. Confirme via API/PostgreSQL as linhas em `entregador`, `documento` e `revisao_entregador`, e o objeto em `EXAME_PERTO_PRIVATE_ROOT/quarantine`. Registre apenas IDs, estados e hashes, nunca tokens ou conteúdo do documento.
6. Exercite arquivo com conteúdo incompatível com a extensão/MIME, arquivo acima de 10 MiB, API desligada, token de acesso expirado com refresh, refresh inválido e logout. Depois do logout, reabrir o app não pode restaurar a sessão. O documento deve permanecer inacessível antes da inspeção.
7. Se houver AO sintético no banco descartável, execute atribuição/MFA/inspeção pelo painel e confirme que a tentativa de decisão profissional retorna `422 POLICY_UNDEFINED`. A inspeção estrutural não comprova autenticidade documental nem constitui varredura antimalware.

Sem `adb`, emulador ou aparelho conectado, esses passos continuam pendentes e nenhum build/export substitui o ensaio no dispositivo.

O app está alinhado ao Expo SDK 57/React Native 0.86. O [relatório de dependências](../docs/AUDITORIA-DEPENDENCIAS-MOBILE.md) registra a migração e os alertas transitivos remanescentes do toolchain. Antes de qualquer distribuição, repita `npm audit --omit=dev`, a análise de alcance e os ensaios em dispositivo; não use `npm audit fix --force`, pois a sugestão atual faz downgrade incompatível da stack.
