# Decisões técnicas

## ADR-001 — Stack

Status: escolhida pelo autor em 29/09/2026.

Java 21 + Spring Boot + Spring Security + Maven; PostgreSQL + Flyway + Spring Data JPA; React + TypeScript + Vite; React Native + TypeScript + Expo para o entregador.

Motivo: aproveitar a experiência do autor com Java e React, manter regras e transações no backend e atender o GPS de um aplicativo nativo. Versões exatas ficam para a criação dos manifests e lockfiles; não usar automaticamente uma versão experimental.

## ADR-002 — Monólito modular e repositório único

Status: desenho inicial adotado.

Um backend, uma web, um app e documentação juntos. Módulos organizam responsabilidades; não criar microserviços, Kafka ou Redis antes de necessidade medida. Um processo inicial pode usar broker STOMP interno; múltiplas instâncias exigirão redesenho de distribuição ao vivo, não apenas duplicar processos.

## ADR-003 — Cadastros reais e integrações explícitas

Status: requisito do autor.

Não incluir contas fake, seeds de pacientes/motoristas, posições simuladas, benefícios aprovados ficticiamente ou saldos inventados. Integração indisponível gera estado explícito. Cadastro real não significa verificação oficial concluída.

Testes automatizados usam dados sintéticos exclusivamente em ambiente isolado e descartável. Não inserir esses dados em ambientes acessíveis a usuários.

## ADR-004 — Subsídio e remuneração

Status: requisito do autor.

Gratuidade por idade/deficiência e desconto por renda são avaliações separadas. Financiador deve ser identificado e habilitado; recurso registrado deve ser rastreável e comprovado. Reserva precisa impedir gasto concorrente do mesmo saldo.

Pagamento do paciente + subsídio = frete integral do entregador. A plataforma precisa de receita separada. Critério proposto não cria direito a recursos governamentais; contratação pública/institucional continua sendo dependência operacional.

## ADR-005 — Rastreamento verdadeiro

Status: requisito do autor.

App envia GPS durante tarefa ativa; backend distribui apenas aos autorizados. Localização antiga recebe indicação clara. Não declarar que há GPS se a posição veio de cenário estático. Desenvolvimento em Android real e development build Expo é necessário para verificar segundo plano.

## ADR-006 — GitHub e deploy no final da primeira versão

Status: parcialmente substituída pelo prompt autorizado de 29/09/2026.

A documentação pode ser commitada e publicada agora no remoto confirmado do aplicativo, em branch sem deploy automático, após validação e revisão contra a base capturada. Implementação e deploy continuam sujeitos a autorização posterior. Arquitetura e diagramas acompanham o código. Não publicar documentos pessoais, arquivos de configuração com segredos ou dumps de banco.

## Pendências antes do código

- Revisão do [OpenAPI](../contracts/openapi.yaml) e do [modelo físico](MODELO-DADOS.md) produzidos nesta etapa; migrações ficam para implementação.
- Critérios/documentos aceitos e papéis de revisão de benefícios.
- Procedimento de criação do administrador e recuperação/verificação de contas.
- Política de retenção e permissão de acesso a documentos e GPS.
- Definição de tarifas e financiadores reais habilitados.
- Seleção e acesso a serviços de identidade, rotas, armazenamento, e-mail e pagamentos.

Essas dependências podem ser implementadas em etapas; sem credencial/contrato, manter a integração indisponível. Não bloquear desenvolvimento local das partes independentes nem preencher dependências com respostas fictícias.

## Índice complementar de ADRs

ADR-001 a ADR-006 permanecem neste documento como fonte original. Não duplicar seu conteúdo em novos arquivos.

| ADR | Estado | Fonte |
|---|---|---|
| ADR-007 — Transações e efeitos externos | Proposta para revisão | [0007-transacoes-financeiras](adr/0007-transacoes-financeiras.md) |

Políticas em aberto e alternativas operacionais estão em [SEGURANCA](SEGURANCA.md) e nos tickets 01–02 do [backlog](../.scratch/planejamento/README.md). A inclusão de uma proposta técnica no contrato não representa aprovação das políticas comerciais.
