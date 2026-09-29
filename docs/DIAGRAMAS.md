# Diagramas

Planejamento técnico — 29/09/2026. As caixas representam componentes e capacidades planejados, não serviços já implementados, credenciados ou contratados. Integrações externas ficam indisponíveis até habilitação real.

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

```mermaid
erDiagram
  direction TB
  USUARIO ||--o{ AUTORIZACAO_PACIENTE : recebe
  PACIENTE ||--o{ AUTORIZACAO_PACIENTE : concede
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
  PROGRAMA ||--o{ RESERVA_SUBSIDIO : garante
  ORCAMENTO ||--o{ ORCAMENTO_BENEFICIO : aplica
  DECISAO_BENEFICIO ||--o{ ORCAMENTO_BENEFICIO : comprova
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

Visão resumida do [modelo físico](MODELO-DADOS.md), ainda sem migração. Restrições planejadas: uma designação ativa por pedido; um orçamento aceito vigente; vínculo de veículo e motorista aprovados; decisão de benefício atribuída ao paciente; programa e reserva da mesma instituição; idempotência dos lançamentos. Pedido pode não usar subsídio, então lançamentos particulares não dependem de reserva. Evidências clínicas e financeiras precisam de acesso restrito e retenção própria.

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
  ACEITA --> CANCELADA: Regra de cancelamento
  RETIRADA --> EM_ENTREGA
  EM_ENTREGA --> ENTREGUE: Codigo de recebimento
  RETIRADA --> OCORRENCIA
  EM_ENTREGA --> OCORRENCIA
  OCORRENCIA --> EM_ENTREGA: Reentrega autorizada
  OCORRENCIA --> ENCERRADA_COM_OCORRENCIA: Resolucao auditada
  ENTREGUE --> [*]
  CANCELADA --> [*]
  NAO_ATENDIDA --> [*]
  ENCERRADA_COM_OCORRENCIA --> [*]
```

Cancelamento e encerramento não estornam automaticamente serviço já prestado. Estado de pagamento, benefício e reserva é separado do estado da entrega. Reentrega não cria custo adicional sem autorização e cobertura.

## Aceite e liquidação

```mermaid
sequenceDiagram
  participant C as Paciente
  participant B as Backend
  participant D as PostgreSQL
  participant M as Entregador
  C->>B: Aceitar orçamento vigente e parcelas
  B->>D: Confirmar parcelas e reservar subsídio em transação
  D-->>B: Reserva criada ou saldo indisponivel
  Note over B,D: Cobranca externa via outbox, fora da transacao
  Note over B,D: Parcela paciente positiva exige confirmacao real
  B->>D: Aplicar evento autenticado sem duplicacao
  D-->>B: Cobertura confirmada ou pendente
  alt Cobertura confirmada
    B-->>C: Pedido disponível para entrega
    M->>B: Aceitar tarefa
    B->>D: Criar designação única e ativa
    D-->>B: Designação confirmada
    B-->>M: Tarefa e frete integral acordado
    M->>B: Confirmar retirada autorizada
    M->>B: Confirmar entrega com código
    B->>D: Liquidar reserva e registrar repasse uma vez
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
