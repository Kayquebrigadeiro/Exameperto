# Diagramas

Planejamento técnico — 30/09/2026. As caixas representam componentes e capacidades planejados, não serviços já implementados, credenciados ou contratados. Integrações externas ficam indisponíveis até habilitação real.

## Componentes e comunicação

```mermaid
flowchart TB
  subgraph Interfaces["Interfaces"]
    W["Web do paciente e familiar"]
    A["Painel administrativo web"]
    M["App do entregador"]
  end
  B["Spring Boot: API REST e WebSocket"]
  D[("PostgreSQL")]
  O["Armazenamento privado de documentos"]
  subgraph Externos["Integrações habilitadas por contrato/configuração"]
    I["Identidade e documentos oficiais"]
    R["Rotas e mapas"]
    P["Pagamento e repasse"]
    N["E-mail e notificações"]
  end
  W -->|"HTTPS e acompanhamento"| B
  A -->|"HTTPS e revisão"| B
  M -->|"HTTPS: tarefa e GPS"| B
  B -->|"WebSocket: estado e localização"| W
  B -->|"WebSocket: ofertas e tarefa"| M
  B --> D
  B --> O
  B --> I
  B --> R
  B --> P
  B --> N
```

Nenhum cliente acessa o banco diretamente. O backend autoriza cada consulta, envio de posição e assinatura ao vivo. Fotos operacionais são entregues somente a participantes autorizados da tarefa; documentos de análise não são publicados.

## Módulos do backend

```mermaid
flowchart TB
  API["Controladores REST e mensagens ao vivo"]
  ID["Identidade e autorização"]
  DV["Motoristas e veículos"]
  EV["Evidências e benefícios"]
  DE["Entregas e ocorrências"]
  PR["Rotas e precificação"]
  FU["Financiamento e reservas"]
  TR["Rastreamento autorizado"]
  AU["Auditoria e integrações"]
  API --> ID
  API --> DV
  API --> EV
  API --> DE
  EV --> FU
  DE --> PR
  DE --> FU
  DE --> TR
  ID --> AU
  DV --> AU
  EV --> AU
  FU --> AU
  TR --> AU
```

Um processo inicial e um banco, com responsabilidades separadas por módulo. Integrações compartilham adaptadores; não é proposta de nove microserviços.

## Modelo de dados conceitual

### Fluxo de benefício e revisão

```mermaid
flowchart LR
  P[Paciente ou familiar com BENEFICIOS vigente] --> S[Solicitação + dimensões independentes]
  S --> E[Evidências privadas em quarentena]
  S --> V[Snapshot da política/versionamento]
  E --> R[Revisão atribuída]
  R -->|MFA + sem autoanálise| D[Decisão humana]
  D --> A[Recurso]
  A --> R2[Outro analista + MFA]
  V --> X{Política vigente?}
  X -->|não| U[422 POLICY_UNDEFINED]
  D --> F[Sem reserva de financiamento]
```

```mermaid
erDiagram
  direction TB
  USUARIO ||--o{ AUTORIZACAO_PACIENTE : recebe
  PACIENTE ||--o{ AUTORIZACAO_PACIENTE : concede
  PACIENTE ||--o{ CONVITE_FAMILIAR : inicia
  CONVITE_FAMILIAR ||--o| AUTORIZACAO_PACIENTE : confirma
  VERIFICACAO_EVIDENCIA }o--o{ SOLICITACAO_BENEFICIO : reutiliza
  USUARIO ||--o| MOTORISTA : possui
  MOTORISTA ||--o{ VINCULO_VEICULO : possui
  VEICULO ||--o{ VINCULO_VEICULO : permite
  PACIENTE ||--o{ PEDIDO : solicita
  PACIENTE ||--o{ SOLICITACAO_BENEFICIO : apresenta
  SOLICITACAO_BENEFICIO ||--o{ EVIDENCIA : referencia
  SOLICITACAO_BENEFICIO ||--o{ DECISAO_BENEFICIO : recebe
  INSTITUICAO ||--o{ PROGRAMA : financia
  PROGRAMA ||--o{ DECISAO_BENEFICIO : regula
  PEDIDO ||--o{ ORCAMENTO : possui
  ORCAMENTO ||--o| RESERVA_SUBSIDIO : requer
  PROGRAMA ||--|| CONTA_PROGRAMA : controla
  PROGRAMA ||--o{ APORTE : recebe
  DOCUMENTO ||--o{ APORTE : comprova_registro
  DOCUMENTO o|--o{ APORTE : fundamenta_revisao
  PROGRAMA ||--o{ RESERVA_SUBSIDIO : garante
  ORCAMENTO ||--o{ ORCAMENTO_BENEFICIO : aplica
  DECISAO_BENEFICIO ||--o{ ORCAMENTO_BENEFICIO : comprova
  UNIDADE_RETIRADA ||--o{ PROTOCOLO_CUSTODIA : aceita
  PROTOCOLO_CUSTODIA ||--o{ PEDIDO : orienta
  PEDIDO ||--o| CUSTODIA : preserva
  PEDIDO ||--o| APURACAO_REMUNERACAO : requer
  POLITICA_CANCELAMENTO ||--o{ ORCAMENTO : regula
  POLITICA_CANCELAMENTO ||--o{ APURACAO_REMUNERACAO : fundamenta
  PEDIDO ||--o{ DESIGNACAO : registra
  VINCULO_VEICULO ||--o{ DESIGNACAO : executa
  PEDIDO ||--o{ EVENTO_ENTREGA : registra
  DESIGNACAO ||--o{ POSICAO : transmite
  PEDIDO ||--o{ LANCAMENTO_FINANCEIRO : origina
  RESERVA_SUBSIDIO ||--o{ LANCAMENTO_FINANCEIRO : movimenta
  OPERACAO_FINANCEIRA ||--o{ LANCAMENTO_FINANCEIRO : agrupa
  OPERACAO_FINANCEIRA ||--o{ OUTBOX : agenda
  OPERACAO_FINANCEIRA o|--o{ EVENTO_EXTERNO : concilia
```

Visão resumida do [modelo físico](MODELO-DADOS.md). Conta, sessão, paciente, convite, concessão, a fatia local de entregador/documento, MFA de sessão do AO e vínculo de veículo possuem migrações V1–V6; as demais relações continuam planejadas. Restrições planejadas: uma designação ativa por pedido; um orçamento aceito vigente; vínculo de veículo e motorista aprovados; decisão de benefício atribuída ao paciente; programa e reserva da mesma instituição; idempotência dos lançamentos. Pedido pode não usar subsídio, então lançamentos particulares não dependem de reserva. Evidências clínicas e financeiras precisam de acesso restrito e retenção própria.

### Ticket 06 — vínculo e revisão versionada

```mermaid
flowchart LR
  A[Entregador autenticado] --> B[POST/PUT veículo + vínculo]
  B --> C{versão corrente?}
  C -- não --> X[412 VERSION_MISMATCH]
  C -- sim --> D[veiculo + vinculo_veiculo]
  D --> E[CRLV / FOTO / autorização em QUARENTENA]
  E --> F[vinculo_documento corrente]
  F --> G[revisao_vinculo snapshot da versão]
  G --> H{AO atribuído + MFA}
  H -- não --> I[403 MFA_REQUIRED ou fila pendente]
  H -- sim --> J[inspeção estrutural limitada]
  J --> K{substituição ou versão mudou?}
  K -- sim --> L[bloqueia revisão antiga e cria snapshot novo]
  K -- não --> M[decisão continua 422 POLICY_UNDEFINED]
```

O fluxo não consulta fonte oficial, não libera ofertas e não trata inspeção de estrutura como autenticidade documental ou varredura antimalware.

## Entregador, quarentena e revisão — ticket 05 parcial

```mermaid
flowchart LR
  M[App Expo do entregador] -->|sessão MOBILE| A[API autenticada]
  A --> P[(PostgreSQL V4)]
  A --> Q[(Armazenamento privado/quarentena)]
  P --> R[Revisão atribuída com MFA]
  R --> S[Inspeção estrutural local]
  S -->|estrutura recusada| B[BLOQUEADA / sem aprovação]
  S -->|estrutura aceita| X{Autenticidade + critérios + responsável real}
  X -->|pendente| B
  X -->|habilitado futuramente| D[Decisão profissional]
  A -->|proxy no-store| M
  W[Painel web do analista] --> R
```

O armazenamento local privado, a atribuição com TOTP e a inspeção estrutural limitada foram exercitados com dados sintéticos. A inspeção trata segurança básica do arquivo; não prova autenticidade do documento, situação profissional nem elegibilidade. Critérios profissionais e responsável real continuam ausentes, portanto nenhuma decisão aprova o cadastro ou habilita entregas. Armazenamento externo só será dependência quando os requisitos de operação/deploy o exigirem; sua ausência não invalida o ensaio local. Foto operacional só poderá ser projeção de documento profissionalmente aprovado, nunca aprovação automática.

## Estados da entrega

```mermaid
stateDiagram-v2
  [*] --> SOLICITADA
  SOLICITADA --> EM_VERIFICACAO
  SOLICITADA --> CANCELADA
  EM_VERIFICACAO --> CANCELADA
  EM_VERIFICACAO --> AGUARDANDO_ACEITE
  EM_VERIFICACAO --> NAO_ATENDIDA
  AGUARDANDO_ACEITE --> DISPONIVEL: Aceite e cobertura financeira
  AGUARDANDO_ACEITE --> CANCELADA
  DISPONIVEL --> ACEITA: Designacao atomica
  DISPONIVEL --> CANCELADA
  ACEITA --> RETIRADA: Retirada autorizada
  ACEITA --> OCORRENCIA: Suspensao operacional
  ACEITA --> CANCELADA: Abrir apuracao e reter cobertura
  RETIRADA --> EM_ENTREGA
  EM_ENTREGA --> ENTREGUE: Codigo de recebimento
  RETIRADA --> OCORRENCIA
  EM_ENTREGA --> OCORRENCIA
  OCORRENCIA --> ACEITA: Retomar se origem ACEITA
  OCORRENCIA --> RETIRADA: Retomar se origem RETIRADA
  OCORRENCIA --> EM_ENTREGA: Retomar se origem EM_ENTREGA
  OCORRENCIA --> ENCERRADA_COM_OCORRENCIA: Destino comprovado e remuneracao apurada
  ENTREGUE --> [*]
  CANCELADA --> [*]
  NAO_ATENDIDA --> [*]
  ENCERRADA_COM_OCORRENCIA --> [*]
```

Cancelamento pré-retirada interrompe deslocamento, abre apuração quando houver designação e conserva cobertura; não gera integral nem libera tudo automaticamente. Depois da retirada, ocorrência mantém custódia/retorno até destino comprovado. Nenhuma unidade está confirmada; operação real bloqueada. Estado de pagamento, benefício e reserva é separado do estado da entrega. Reentrega não cria custo adicional sem autorização e cobertura.

## Aceite e liquidação

```mermaid
sequenceDiagram
  participant C as Paciente
  participant B as Backend
  participant D as PostgreSQL
  participant M as Entregador
  C->>B: Aceitar orçamento, parcelas e politica versionada
  B->>D: Confirmar parcelas e reservar somente subsidio positivo
  D-->>B: Particular sem programa ou subsidio reservado ou insuficiente
  Note over B,D: Cobranca externa via outbox, fora da transacao
  Note over B,D: Parcela paciente positiva exige confirmacao real
  B->>D: Aplicar evento autenticado sem duplicacao
  D-->>B: Cobertura confirmada ou pendente
  alt Cobertura confirmada
    B-->>C: Pedido disponível para entrega
    M->>B: Aceitar tarefa
    B->>D: Criar designação única e ativa
    D-->>B: Designação confirmada
    B-->>M: Frete do servico completo e politica de cancelamento
    Note over B,M: Aceite nao garante integral em cancelamento
    M->>B: Confirmar retirada, protocolo e prova de custodia
    Note over B,M: Exigir unidade e cobertura de retorno habilitadas
    M->>B: Confirmar entrega com código
    B->>D: Evento de recebimento e encerrar custodia
    B->>D: Liquidar reserva e registrar repasse na mesma transacao
    B-->>C: Entrega concluída
  else Cobertura pendente ou indisponivel
    B-->>C: Aguardar ou aceitar outro orçamento
  end
```

Confirmação de pagamento real depende do provedor habilitado e dos eventos autenticados correspondentes. Registro de repasse a pagar não equivale a dinheiro transferido; confirmação de transferência vem da integração ou conciliação comprovada.

## Rastreamento ao vivo

```mermaid
sequenceDiagram
  participant M as App do entregador
  participant B as Backend
  participant D as PostgreSQL
  participant C as Paciente autorizado
  M->>B: Enviar posição com horário e precisão
  B->>D: Conferir designação ativa e registrar posição válida
  B-->>C: Publicar posição e horário via WebSocket
  alt Perda de conexão ou posição antiga
    C->>B: Recuperar último estado autorizado
    B-->>C: Última posição e indicação de desatualização
  else Entrega encerrada
    B-->>M: Encerrar rastreamento da tarefa
    B-->>C: Encerrar assinatura e mostrar conclusão
  end
```

O app enfileira somente dados recentes com limites e política de descarte; reconexão não reproduz pontos antigos como se fossem atuais. Autorização é reavaliada no envio e no recebimento, e o fechamento da tarefa encerra compartilhamento.

## Estados financeiros independentes

```mermaid
flowchart LR
  E["Beneficio aprovado"] --> V["Verificar recursos reais"]
  V --> R["Reserva confirmada"]
  R --> C["Cobertura: reserva e parcela paga"]
  C --> D["Entrega comprovada"]
  D --> L["Liquidacao local e valor a pagar"]
  L --> P["Repasse pendente via outbox"]
  P --> I["Resultado incerto: conciliar"]
  P --> T["Transferencia confirmada pelo provedor"]
  I --> T
```

Timeout não confirma transferência. [Contrato HTTP](../contracts/openapi.yaml) e [permissões/STOMP](SEGURANCA.md) detalham comandos e participantes. O [backlog](../.scratch/planejamento/README.md) mantém as dependências por fatia funcional.

## Convite e confirmação do adulto — D03, implementado no ticket 04

```mermaid
flowchart LR
  P["Paciente adulto titular"] --> C["Convite privado: somente hash no PostgreSQL"]
  C --> E["Canal de e-mail habilitado"]
  E --> A["Destinatário autenticado aceita uma vez"]
  A --> F["Paciente reautentica e confirma escopos e prazo"]
  F --> G["Concessão vigente no PostgreSQL"]
  G --> Q["Cada operação consulta titular, escopo e vigência"]
  Q --> R["Expiração ou revogação bloqueia acesso"]
  A --> N["Sem confirmação: nenhum acesso"]
  C --> X["Canal ausente ou falha: 503 e rollback"]
```

O perfil permanece com identidade PENDENTE; e-mail, CPF, parentesco, idade ou deficiência não o tornam verificado nem concedem acesso. Menores e representação legal permanecem fora da cobertura inicial. Documento/foto e STOMP são integrações futuras que deverão reautorizar cada acesso/despacho; não foram presumidos nesta implementação.

## Cancelamento e custódia — D06

```mermaid
flowchart TD
  C["Interrupção solicitada"] --> F{"Já retirou?"}
  F -->|"Não"| A["Parar deslocamento; apurar serviço se designado"]
  F -->|"Sim"| O["Ocorrência: preservar custódia e cobertura de retorno"]
  O --> D["Destino autorizado comprovado"]
  D --> P["Apurar deslocamento e serviço por política aceita"]
  A --> P
  P --> V{"Critérios, evidência e cobertura válidos?"}
  V -->|"Não"| B["Apuração pendente; preservar cobertura"]
  V -->|"Sim"| L["Liquidar devido e liberar somente excedente"]
  L --> R["Repasse sujeito à confirmação real"]
```

Sem designação/serviço não criar remuneração; reconciliar cobrança/reserva. Valores/multas/responsabilidades continuam pendentes. As hipóteses de GPS D09 ainda exigem ensaio real; nenhuma adequação declarada.

## Marco local e habilitação — D12

```mermaid
flowchart LR
  A["03A: formulário e bloqueio local verificável"] --> B["03: cadastro e segurança"]
  B --> C["Versão funcional validada localmente"]
  C --> P["Particular: políticas, unidade, rota e pagamento habilitados"]
  C --> S["Subsidiado: mesmas dependências e programa com recursos reais"]
  P --> O["Operação somente após habilitação real"]
  S --> O
```

Não há piloto presumido, compra ou deploy autorizado. MFA/segregação são arquitetura adotada; políticas/responsáveis pendentes e integrações indisponíveis mantêm guardas independentes. Cadastro e segurança não dependem de programa subsidiado.

## Conta, sessão e recuperação — implementado no ticket 03

```mermaid
sequenceDiagram
  actor U as Pessoa
  participant W as Web
  participant B as Backend
  participant D as PostgreSQL
  participant E as SMTP real configurado
  U->>W: Solicitar cadastro
  W->>B: POST register
  B->>B: Política, configuração, limites
  B->>D: Transação conta pendente e hash de desafio
  B->>E: Código aleatório em memória
  alt Falha SMTP
    B->>D: Rollback
    B-->>W: 503 sem conta parcial
  else Aceitação SMTP
    B->>D: Commit
    B-->>W: 202 pendente de confirmação
  end
  U->>W: Informar código recebido
  W->>B: POST verification
  B->>D: Lock conta e consumo único antes do prazo
  B-->>W: E-mail confirmado, demais verificações independentes
  W->>B: Login WEB com Origin
  B->>D: Validar senha e criar sessão
  B-->>W: Access em memória e refresh HttpOnly Secure
  W->>B: Obter CSRF e renovar sessão
  B->>D: Lock conta e rotacionar refresh
  Note over B,D: Replay revoga família e logout inclui sucessor
  U->>W: Solicitar recuperação
  W->>B: POST recovery
  B->>D: Se conta apta, enfileirar apenas UUID
  B-->>W: 202 genérico, sem afirmar envio
  B->>D: Worker bloqueia fila e conta, grava hash
  B->>E: Tentar envio do código em memória
  B->>D: ENVIADO ou RECONCILIAR com desafio invalidado
  W->>B: Código e nova senha
  B->>D: Consumo único, senha e revogação de todas as sessões
```

SMTP aceito não prova chegada à caixa do destinatário. Queda entre envio e commit pode gerar código inválido; nova solicitação é necessária. O ensaio de navegador usa capturador de e-mail exclusivamente nos testes; integração real permanece não homologada.
