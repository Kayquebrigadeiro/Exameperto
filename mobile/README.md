# App do entregador

Expo/React Native para cadastro, upload privado e acompanhamento de estado. O app usa somente a API autenticada; não contém credenciais, aprovação local ou armazenamento de documentos. `EXPO_PUBLIC_API_URL` aponta para o backend no ambiente de desenvolvimento.

O refresh móvel fica no SecureStore, é rotacionado pela API e removido no logout. O app consulta o histórico após upload para acompanhar quarentena/inspeção; nenhum estado local aprova o cadastro.

## Validação reproduzível em Android

1. Inicie PostgreSQL e backend conforme `../infra/compose.yaml`, com chaves privadas de desenvolvimento e política de cadastro de teste; nunca use dados pessoais reais.
2. Execute `npm ci`, `npm run typecheck` e `EXPO_NO_TELEMETRY=1 npm run build:android` neste diretório.
3. Em development build/Expo compatível, defina `EXPO_PUBLIC_API_URL=http://10.0.2.2:8080/api/v1` no emulador Android ou o IP LAN do computador em aparelho físico; inicie com `npm run android`.
4. Entre com uma conta sintética criada no ambiente isolado, salve o perfil, envie PDF/JPEG/PNG sintético e use “Atualizar acompanhamento”. Confirme no PostgreSQL `entregador`, `documento` e `revisao_entregador`, e no diretório `EXAME_PERTO_PRIVATE_ROOT/quarantine`, usando apenas IDs/hashes nos registros de evidência.
5. Verifique erro de MIME/arquivo, excesso de 10 MiB, API indisponível, sessão expirada/refresh, logout e novo acesso negado. O arquivo deve ficar indisponível até inspeção; aprovação profissional permanece bloqueada.

Sem `adb`, emulador ou aparelho conectado, esses passos continuam pendentes e nenhum build/export substitui o ensaio no dispositivo.

O audit atual das dependências reporta alertas transitivos no toolchain Expo/React Native. Antes de distribuição, planeje a atualização compatível do SDK, repita `npm audit --omit=dev`, o export e todos os ensaios em dispositivo; não use `npm audit fix --force`, pois a sugestão automática altera versões principais incompatíveis.
