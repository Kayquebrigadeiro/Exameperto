# Roteiro local das interfaces existentes

Este roteiro permite inspecionar as interfaces já implementadas sem criar contas fictícias, conceder papéis, inserir políticas ou simular efeitos externos. Ele usa somente a configuração privada `.env.smtp.local` já existente, cujo destinatário continua limitado por `EXAME_PERTO_EMAIL_ALLOWED_RECIPIENTS`.

## Preparação segura

Pré-requisitos: Docker, Java/Maven, Node/npm e, para o aplicativo móvel, Android SDK com emulador ou aparelho autorizado. A configuração atual é privada, está fora do Git e deve permanecer com modo `0600`.

Em um terminal na raiz do projeto, inicie PostgreSQL e backend com a configuração existente:

```sh
./scripts/start-local-email-homologation.sh
```

Em outro terminal, inicie a web com os painéis operacionais visíveis somente no servidor de desenvolvimento:

```sh
VITE_ENABLE_OPERATIONAL_TEST_PANELS=true npm run dev --prefix web
```

Acesse <http://localhost:5173/>. Não há rotas web distintas por papel: autenticação e todos os painéis habilitados são montados nessa página, em sequência. O flag é combinado com `import.meta.env.DEV`; portanto não monta os painéis operacionais em build de publicação. Ele altera apenas a navegação, não as autorizações da API.

Use a conta real já confirmada, se ela ainda existir no banco local. Se o volume tiver sido descartado, só é aceitável cadastrar novamente o endereço real já autorizado pela allowlist. Não use outro destinatário, não invente CPF, nascimento, documento, saldo, programa, pedido ou posição.

## Mapa de navegação

| Área | Onde existe | URL ou destino | Requisito para visualizar | Requisito para operar | Bloqueio atual relevante |
|---|---|---|---|---|---|
| Conta e paciente | Web V1 | <http://localhost:5173/> | Página pública; login para perfil, privacidade e perfil de paciente | Conta confirmada e sessão web; dados próprios reais para qualquer envio | Criar perfil exige CPF e nascimento reais. Não preencher apenas para demonstração. Entrega, rota e pagamento permanecem indisponíveis. |
| Familiar autorizado | Web V1 | <http://localhost:5173/> → **Autorizações sob controle** | Sessão web | Duas contas reais distintas; convite ao e-mail autorizado; aceite do destinatário; confirmação do paciente com escopos e prazo | A configuração existente só autoriza seus destinatários atuais. Sem uma segunda pessoa/endereço expressamente autorizado, apenas inspecione a tela; não crie outra conta nem amplie a allowlist. Representação legal e menores estão fora do fluxo. |
| Entregador — cadastro e veículo | Web de desenvolvimento e app mobile | Web: <http://localhost:5173/> → **Cadastro e comprovações**. Mobile: development build | Web com flag local e sessão; mobile instalado e sessão `MOBILE` | Dados e documentos reais do próprio entregador; armazenamento privado; revisão posterior | Upload não aprova nada. Critérios profissionais, responsável real, antimalware abrangente e política de aprovação estão pendentes. A decisão do backend permanece `422 POLICY_UNDEFINED`. |
| Entregador — ofertas, tarefa, custódia e GPS | Somente app mobile | Aplicativo Expo; API local em `http://10.0.2.2:8080/api/v1` no emulador ou `http://IP_LAN:8080/api/v1` no aparelho | Development build instalado, sessão móvel e acesso ao backend | Entregador e veículo aprovados, oferta elegível, cobertura confirmada, protocolo/capacidade habilitados e designação ativa; localização exige permissão do sistema | Aprovação profissional, protocolo/cobertura, políticas operacionais, pagamento/rota e retenção de GPS não estão habilitados. `EXAME_PERTO_TRACKING_ENABLED` permanece falso. Não há oferta, entrega ou posição simulada. |
| Revisão operacional de veículo | Web de desenvolvimento | <http://localhost:5173/> → **Revisão operacional** | O painel é montado com o flag local; sem papel a própria tela informa indisponibilidade | Papel nominal `ANALISTA_OPERACIONAL`, revisão atribuída, MFA vigente, conta diferente da analisada e evidências estruturais completas | Não existe provisionamento automático de papel. Critérios/responsável de aprovação estão pendentes; a decisão final retorna `422 POLICY_UNDEFINED`. A fila de revisão do cadastro do entregador existe apenas na API, sem tela web própria. |
| Benefício do paciente/familiar | Web de desenvolvimento | <http://localhost:5173/> → **Abrir benefícios** | Flag local e sessão | Paciente titular ou familiar com escopo `BENEFICIOS`; política institucional vigente para criar solicitação | Não há política, critérios, documentos aceitos ou responsáveis reais habilitados. A interface de análise por `ANALISTA_BENEFICIO` existe apenas na API; não há painel web de revisão. |
| Pedido e acompanhamento do paciente/familiar | Web de desenvolvimento | <http://localhost:5173/> → **Abrir pedidos** | Flag local e sessão | Paciente ou familiar com `PEDIDOS`; destinatário autorizado; unidade/protocolo, evidência, tarifa e rota válidos. GPS requer participação vigente | Unidade/protocolo, provedor de rota, tarifa comercial, cobertura e políticas de custódia ainda faltam. Sem posição real a tela mostra indisponibilidade e não exibe marcador. |
| Financeiro — aportes | Web de desenvolvimento | <http://localhost:5173/> → **Abrir aportes** | Flag local e sessão; o painel pode ser visto sem conceder capacidade | Papel global e vínculo institucional `GESTOR_FINANCEIRO`, MFA, programa real e evidência privada; outro GF é obrigatório para revisão | Bootstrap/recuperação privilegiados e responsáveis reais estão pendentes. Não existe programa/saldo predefinido, e a mesma pessoa não pode confirmar o próprio aporte. |
| Financeiro — repasse e conciliação | Web de desenvolvimento | <http://localhost:5173/> → **Abrir repasses** | Flag local e sessão | GF nominal da instituição com MFA; entregador só consulta a própria parcela pela API | O provedor de repasse padrão retorna `503 INTEGRATION_UNAVAILABLE`. Não solicitar, confirmar ou conciliar dinheiro para fins de navegação. |
| Privacidade operacional, administração e análise de benefício/cadastro | Somente API | Rotas `/api/v1/privacy-requests/**`, `/api/v1/analyst/**` e contratos em `contracts/openapi.yaml` | Não há interface gráfica existente | Papéis nominais, MFA, atribuição por registro, política válida e segregação aplicável | Provisionamento privilegiado, responsáveis e políticas continuam pendentes. O expurgo está desabilitado por padrão. Não conceder papéis diretamente no banco. |

## Ordem recomendada de inspeção

1. Abra a web e percorra os modos públicos de cadastro, confirmação, login e recuperação sem enviar novos formulários. Entre apenas com a conta real já confirmada.
2. Após o login, inspecione **Seu perfil**, **Solicitações e encerramento** e **Autorizações sob controle**. Não crie perfil de paciente nem convite sem dados e participantes reais autorizados.
3. Com o flag local ativo, percorra **Cadastro e comprovações**, **Revisão operacional**, os botões de benefício, aportes, pedidos e repasses. Respostas `403`, `422 POLICY_UNDEFINED` e `503 INTEGRATION_UNAVAILABLE` são bloqueios esperados, não etapas a contornar.
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
