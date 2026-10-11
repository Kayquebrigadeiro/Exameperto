# Roteiro local das interfaces existentes

Este roteiro permite inspecionar as interfaces já implementadas sem criar contas fictícias, conceder papéis, inserir políticas ou simular efeitos externos. Ele usa somente a configuração privada `.env.smtp.local` já existente, cujo destinatário continua limitado por `EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS`.

## Preparação segura

Pré-requisitos: Docker, Java/Maven, Node/npm e, para o aplicativo móvel, Android SDK com emulador ou aparelho autorizado. A configuração atual é privada, está fora do Git e deve permanecer com modo `0600`.

Em um terminal na raiz do projeto, inicie PostgreSQL e backend com a configuração existente:

```sh
./scripts/start-local-email-homologation.sh
```

Em outro terminal, inicie a web:

```sh
./scripts/start-local-web.sh
```

Acesse somente a origem local canônica <http://127.0.0.1:5173/>. Não alterne para `localhost`: login, CSRF, cookie, links locais, origem autorizada e WebSocket conservam a origem exata. Após o login, o shell usa rotas por hash (`#inicio`, `#perfil`, `#familia`, `#beneficios`, `#pedidos`, `#privacidade`, `#entregador` e `#entregas`). Áreas privilegiadas aparecem somente conforme `/api/v1/me/capabilities`, mas cada operação continua sendo autorizada novamente pelo backend.

Para conceder um papel à sua própria conta local já cadastrada e confirmada, encerre preferencialmente o backend e execute:

```sh
./scripts/provision-local-access.sh
```

Escolha `conceder` ou `revogar` e o papel. `ANALISTA_OPERACIONAL` e `OPERADOR_PRIVACIDADE` são globais e separados. `ANALISTA_BENEFICIO` exige o UUID de uma instituição que já exista. `GESTOR_FINANCEIRO` exige instituição e registra também a capacidade global exigida pelo backend. A ferramenta não cria instituição/programa e registra a alteração em `auditoria_acesso_local`; é desabilitada por padrão e não possui endpoint HTTP. Reinicie o backend e entre novamente. MFA continua obrigatório, e sua segunda conta não satisfaz revisão independente.

Use a conta real já confirmada, se ela ainda existir no banco local. Se o volume tiver sido descartado, só é aceitável cadastrar novamente o endereço real já autorizado pela allowlist. Não use outro destinatário, não invente CPF, nascimento, documento, saldo, programa, pedido ou posição.

## Mapa de navegação

| Área | Onde existe | URL ou destino | Requisito para visualizar | Requisito para operar | Bloqueio atual relevante |
|---|---|---|---|---|---|
| Conta e paciente | `#inicio`, `#perfil`, `#familia`, `#privacidade` | Conta confirmada | CPF/nascimento próprios para criar paciente; outra pessoa real/allowlisted para convite | Identidade continua pendente; encerramento depende de política e obrigações. |
| Familiar | `#familia`, `#beneficios`, `#pedidos` | Concessão vigente por escopo | O formulário identifica o paciente em nome de quem age; backend revalida em cada chamada | Convite exige outro destinatário permitido; não ampliar allowlist. |
| Entregador | `#entregador`, `#entregas` | Cadastro próprio; aprovação/veículo para ofertas | Cadastro, documentos, veículo, ofertas, endereços atribuídos, custódia, entrega e ocorrência têm interface web | GPS contínuo permanece mobile; política profissional e parceiros reais não são inventados. |
| Operação | `#operacao` | AO nominal + MFA | Filas de entregador e veículo, atribuição, inspeção e decisão | Autoanálise e política ausente bloqueiam. |
| Benefícios | `#beneficios`, `#analise-beneficios` | P/F BENEFICIOS; AB institucional + MFA para análise | Solicitação, evidências, listagem, decisão e recurso | Recurso exige outro analista; instituição/política devem existir. |
| Financeiro | `#financeiro` | GF global + institucional + MFA | Aportes, evidência, revisão, saldo, obrigações e conciliação | Programa real e outro GF são necessários; provedor padrão retorna indisponível, sem dinheiro fictício. |
| Privacidade operacional | `#operacao-privacidade` | OP nominal + MFA | Fila, resposta, autorização, execução e verificação separadas | Expurgo desligado por padrão; política, backups e obrigações continuam guardas. |

## Ordem recomendada de inspeção

1. Abra a web e percorra os modos públicos de cadastro, confirmação, login e recuperação sem enviar novos formulários. Entre apenas com a conta real já confirmada.
2. Após o login, inspecione **Seu perfil**, **Solicitações e encerramento** e **Autorizações sob controle**. Não crie perfil de paciente nem convite sem dados e participantes reais autorizados.
3. Percorra o menu lateral. Áreas sem papel não aparecem; use o provisionamento local somente para sua conta e reinicie a sessão. Respostas `403`, `422 POLICY_UNDEFINED` e `503 INTEGRATION_UNAVAILABLE` são bloqueios esperados, não etapas a contornar.
4. Para o mobile, confirme primeiro `adb devices`. Em `mobile/`, use um development build apontado ao backend local:

   ```sh
   EXPO_PUBLIC_API_URL=http://10.0.2.2:8080/api/v1 npx expo run:android
   ```

   Em aparelho físico, substitua `10.0.2.2` pelo IP LAN da máquina. Não distribua o APK. O export existente não comprova que a interface rodou em aparelho.
5. No aplicativo, entre com a conta real e navegue pelas seções de cadastro, veículo, ofertas e designações. Não envie documentos pessoais nem acione custódia/GPS sem os requisitos reais; a ausência de ofertas e designações é o estado correto neste ambiente.

## Como interpretar os bloqueios

- `401 UNAUTHENTICATED`: sessão ausente, expirada ou revogada.
- `403 FORBIDDEN` ou `MFA_REQUIRED`: falta papel, vínculo, escopo ou elevação MFA; o flag visual não concede nenhum deles.
- `404 NOT_FOUND`: o registro não existe ou não pertence ao ator; não tentar descobrir IDs de terceiros.
- `422 POLICY_UNDEFINED`: falta política ou responsável aprovado; não inserir política fictícia para ultrapassar o bloqueio.
- `503 INTEGRATION_UNAVAILABLE`: fornecedor/adaptador real não está habilitado; não substituir por confirmação manual.

Ao terminar, encerre Vite e backend. A remoção do banco/volume deve ser uma decisão explícita porque pode apagar a conta real local; este roteiro não autoriza essa remoção.

Use `./scripts/verify-local-environment.sh` para conferir Vite → backend → PostgreSQL, V18, gate de restauração e estrutura da configuração sem exibir valores privados. Encerre Vite e backend com `Ctrl+C` e execute `./scripts/stop-local.sh`; ele apenas para o PostgreSQL e preserva o volume.
