# Preparação do ensaio Android — `de25a99`

Estado: preparação independente do aparelho, sem rastreamento iniciado e sem
aprovação do ensaio físico. A entrega alvo é `de25a99` (`Implementar
rastreamento mobile e retenção GPS`).

## Guardrails

- Usar somente o PostgreSQL do projeto Compose
  `infra/compose.android-homologation.yaml`, com volume
  `exame_perto_android_homolog_postgres` e porta local `127.0.0.1:55433`.
- Usar uma raiz de objetos privada exclusiva, fora do Git, por exemplo
  `/tmp/exame-perto-android-homolog-private` com modo `700`.
- Backend da homologação em `127.0.0.1:58080`; não abrir firewall, túnel público,
  ngrok, ou bind em endereço de rede.
- `EXAME_PERTO_TRACKING_ENABLED=false` e
  `EXAME_PERTO_TRACKING_RETENTION_ENABLED=false` durante toda a preparação.
  A política de retenção, se criada posteriormente, deve existir somente neste
  banco, ser marcada como teste e nunca ser copiada para o banco normal.
- Não usar a conta, senha, tokens, documentos, objetos ou volume do ambiente
  normal. Não habilitar e-mail externo, pagamento, rota, geocodificação ou
  qualquer integração não necessária.
- Os cenários abaixo são preparação de teste; não concedem aprovação
  profissional nem autorizam operação real.

## Perfil isolado preparado

O arquivo Compose separado cria banco e volume distintos do perfil local
normal. A imagem `postgres:17.6` já estava em cache. O PostgreSQL foi iniciado
em 16/10/2026; a senha foi gerada em memória com `openssl rand`, não foi
impressa nem versionada. O backend foi iniciado temporariamente com JDK 21,
aplicou as migrações até V21, respondeu HTTP 401 sem autenticação e foi
encerrado. O container isolado permanece disponível para a etapa do aparelho.

```sh
read -r -s -p 'Senha temporária da homologação Android: ' EXAME_PERTO_ANDROID_HOMOLOG_POSTGRES_PASSWORD
printf '\n'
export EXAME_PERTO_ANDROID_HOMOLOG_POSTGRES_PASSWORD
docker compose -p exame-perto-android-homolog \
  -f infra/compose.android-homologation.yaml up -d postgres

export DATABASE_URL='jdbc:postgresql://127.0.0.1:55433/exameperto_android_homolog'
export DATABASE_USER='exameperto_android_homolog'
export DATABASE_PASSWORD="$EXAME_PERTO_ANDROID_HOMOLOG_POSTGRES_PASSWORD"
export EXAME_PERTO_PRIVATE_ROOT='/tmp/exame-perto-android-homolog-private'
export EXAME_PERTO_TRACKING_ENABLED=false
export EXAME_PERTO_TRACKING_RETENTION_ENABLED=false
export SERVER_PORT=58080
mkdir -p "$EXAME_PERTO_PRIVATE_ROOT" && chmod 700 "$EXAME_PERTO_PRIVATE_ROOT"

mvn -q -f backend/pom.xml spring-boot:run
```

O repositório não possui `mvnw`; usar o Maven acima somente após
instalar/selecionar JDK 21. Antes de iniciar o backend, conferir
que a URL aponta para `55433`, o objeto aponta para a raiz temporária e ambas as
variáveis de rastreamento estão `false`. A migração V21 deve ser aplicada nesse
banco isolado; não executar migração apontando para a porta `5433`.

Para o aparelho físico, conectar por USB e executar no host:

```sh
adb devices
adb reverse tcp:58080 tcp:58080
EXPO_PUBLIC_API_URL=http://127.0.0.1:58080/api/v1 npx expo run:android
```

`adb reverse` mantém a conexão no cabo e no loopback do host. Se o backend não
responder em `127.0.0.1:58080`, não substituir por IP da LAN sem decisão
explícita; não usar túnel público.

## Ferramentas e pré-requisitos

Verificação realizada no host em 16/10/2026:

| Item | Resultado | Necessário |
|---|---|---|
| Node.js | `v24.19.0` | >= `20.19.4`; compatibilidade do Expo deve ser confirmada |
| npm | `12.0.2` | necessário para `npm ci` |
| Docker | `28.5.2` | necessário para PostgreSQL isolado |
| Maven | disponível | necessário para backend |
| Java global | OpenJDK `25.0.3` | não alterar |
| JDK do projeto | OpenJDK `21.0.12.1` em `/usr/lib/jvm/java-21-openjdk-amd64` | usar somente via `JAVA_HOME`/`PATH` do comando |
| `adb` | ausente | Android SDK Platform-Tools |
| `sdkmanager`/SDK | ausentes | instalar em `$HOME/Android/Sdk`, sem privilégio |
| Gradle global | ausente | não é necessário: o wrapper deve usar Gradle `9.3.1` |

Requisitos efetivos encontrados no React Native `0.86.3` instalado:

- Android minSdk `24`, compileSdk/targetSdk `36`;
- Build Tools `36.0.0`;
- NDK `27.1.12297006`;
- Android Gradle Plugin `8.12.0`;
- Gradle Wrapper `9.3.1`.

O projeto não contém diretório `mobile/android` ainda; `expo run:android`
deverá gerar o projeto nativo e o wrapper local. Para manter o Java global
inalterado, todos os comandos Android devem ser executados assim:

```sh
cd /home/grekas/Projetos/AquiPerto/mobile
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version                 # deve mostrar 21.x
```

Dependências realmente ausentes, sem instalação privilegiada. Depois de obter
o ZIP oficial do Android Command-line Tools para Linux em
`/tmp/commandlinetools-linux.zip`, instalar apenas no diretório do usuário:

```sh
set -eu
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
unzip -q /tmp/commandlinetools-linux.zip -d /tmp/android-cmdline-tools
mv /tmp/android-cmdline-tools/cmdline-tools/* "$ANDROID_HOME/cmdline-tools/latest/"
rm -rf /tmp/android-cmdline-tools
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null
sdkmanager --sdk_root="$ANDROID_HOME" \
  'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' \
  'ndk;27.1.12297006'
adb version
```

O comando acima não foi executado. Não é necessário instalar Gradle global;
o wrapper baixará `gradle-9.3.1-bin.zip` quando o build nativo for autorizado.
Se o ZIP ainda não estiver disponível, parar antes de `unzip`; não usar
`sudo`, Android Studio, AVD ou emulador neste ensaio.

Espaço observado: filesystem raiz com aproximadamente `8.0 GB` livres,
`/home` com `822 GB` livres e `/tmp` com `7.8 GB` livres. O aparelho físico
evita o AVD, mas ainda exige aproximadamente 1 GB para command-line tools e
Platform-Tools, 1–2 GB para plataforma/build-tools/NDK, até 300 MB para o
Gradle distribution e 1–2 GB de caches/build; reservar **5–8 GB na raiz**.
O cache atual do Gradle ocupa 1.8 GB e o `node_modules` 361 MB. Não fazer
downloads grandes além dos comandos acima sem confirmar que a raiz mantém
essa margem.

## Cenários sintéticos preparados

Criar exclusivamente no banco isolado: entregador `ANDROID-HOMOLOG-01`, uma
designação sintética e objetos sem dados pessoais. A designação pode ser
marcada como ativa para preparar a tela, mas o botão de compartilhamento não
deve ser acionado nesta etapa. Não criar aprovação profissional real, saldo,
pagamento, destinatário real, documento real ou operação externa.

Quando o ensaio físico for autorizado, registrar para cada caso: aparelho e
versão Android, versão do build, permissões, hora, estado da rede, bateria
inicial/final, resultado HTTP, sequência aceita/rejeitada e estado da fila.

| Caso | Ação | Resultado esperado |
|---|---|---|
| Permitir em primeiro plano | aceitar somente durante o uso | Android concede a permissão contextual; o app não envia ponto sem tarefa ativa |
| Negar | negar a solicitação | app permanece utilizável sem rastreamento e informa bloqueio; nenhum ponto chega ao banco |
| Revogar | retirar permissão nas configurações | próximo uso detecta ausência e não envia; sessão/tarefa não contornam o Android |
| Tela bloqueada/troca de app | executar somente após autorização do ensaio | serviço foreground/notificação conforme Android; sem promessa de execução contínua |
| Perda/retomada de rede | cortar rede, depois restaurar | fila local limitada a 2 minutos; reenvio idempotente, sem duplicatas; ponto expirado é rejeitado |
| Sessão expirada/logout | expirar sessão e sair | refresh inválido remove credencial local; logout interrompe o contexto e rejeita novos pontos |
| Revogação da designação | revogar no backend | envio recebe rejeição de autorização; fila é descartada e não há novos pontos |
| Entrega encerrada | concluir a entrega sintética | rastreamento para; endpoint de localização rejeita novas posições |
| Reinício | reiniciar processo/aparelho dentro dos limites Android | SecureStore restaura somente sessão válida; contexto inválido não reinicia captura |
| Force-stop | usar `am force-stop` apenas no cenário autorizado | Android pode interromper definitivamente o serviço; o app não promete recuperar captura perdida |
| Fila/bateria | observar intervalos e bateria do sistema | registrar observação, não critério de aprovação; nenhuma captura fora da tarefa ativa |

Para permissão, revogação, sessão, designação e encerramento, a verificação de
backend deve confirmar somente IDs, estados, sequência e hashes. Nunca registrar
latitude/longitude em logs ou no roteiro. Não iniciar a fila nem consultar
`/locations` durante esta preparação.

## Critério de encerramento

O ensaio só poderá ser declarado fisicamente realizado depois de um aparelho
ou AVD executar os casos acima. Build, typecheck, export, conexão USB e
PostgreSQL isolado não equivalem a ensaio físico aprovado. Pagamento oficial,
deploy, distribuição pública e contratação permanecem adiados.
