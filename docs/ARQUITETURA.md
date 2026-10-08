# Arquitetura

Status: conta básica e autorização familiar implementadas em 03A/03/04; demais módulos seguem como desenho inicial. Stack escolhida pelo autor em 29/09/2026.

## Organização

Um repositório reúne backend, web, app do entregador e documentação. O backend começa como um monólito modular: um processo Spring Boot, com módulos e transações bem definidos. Isso permite aprender, testar e operar a plataforma sem introduzir microserviços e filas distribuídas antecipadamente.

| Caminho previsto | Responsabilidade |
|---|---|
| `backend/pom.xml` | Dependências, compilação e testes Java |
| `backend/src/main/java/br/com/exameperto/identity/` | Contas, autenticação, representação e permissões |
| `backend/src/main/java/br/com/exameperto/drivers/` | Cadastro, documentos, fotos e aprovação do entregador |
| `backend/src/main/java/br/com/exameperto/vehicles/` | Veículos e vínculo autorizado com entregadores |
| `backend/src/main/java/br/com/exameperto/evidence/` | Documentos privados, verificações e retenção |
| `backend/src/main/java/br/com/exameperto/benefits/` | Critérios, solicitações, decisões e recurso |
| `backend/src/main/java/br/com/exameperto/funding/` | Instituições, programas, reservas e liquidação |
| `backend/src/main/java/br/com/exameperto/pricing/` | Tarifa versionada, rotas e orçamento |
| `backend/src/main/java/br/com/exameperto/deliveries/` | Pedido, designação, retirada, entrega e ocorrências |
| `backend/src/main/java/br/com/exameperto/tracking/` | Ingestão e distribuição autorizada de localização |
| `backend/src/main/java/br/com/exameperto/integrations/` | Adaptadores para serviços externos |
| `backend/src/main/java/br/com/exameperto/audit/` | Registro de decisões e ações sensíveis |
| `backend/src/main/resources/db/migration/` | Migrações Flyway do PostgreSQL |
| `backend/src/test/` | Testes de regras, API, banco e concorrência |
| `web/src/` | Paciente, familiar e administração em React/TypeScript |
| `driver-app/src/` | App do entregador em React Native/Expo |
| `contracts/openapi.yaml` | Contrato HTTP versionado, proposta disponível |
| `infra/compose.yaml` | Ambiente local reproduzível, a produzir |
| `docs/` | Arquitetura, diagramas, execução e decisões |
| `.github/workflows/` | Integração contínua, a produzir |

Os caminhos de implementação são planejados; este pacote contém documentação, glossário, backlog local e contrato OpenAPI. Dentro dos módulos, separar controladores HTTP, serviços de aplicação, entidades/regras e persistência conforme necessário, sem adicionar camadas vazias.

## Interfaces

**Web:** React + TypeScript + Vite. Uma aplicação com áreas protegidas por perfil. Paciente e familiar usam pelo celular sem instalação; avaliação e gestão usam a mesma aplicação com autorização específica.

**App do entregador:** React Native + TypeScript + Expo, inicialmente Android. Cadastro, documentos, veículo ativo, ofertas, tarefas, navegação e localização. Rastreamento em segundo plano precisa de development build e testes reais; não assumir funcionamento apenas no Expo Go.

**Estado de interface:** TanStack Query para dados do servidor; estado local para formulários. Zustand somente se houver estado compartilhado real que justifique. Não adicionar Redux.

## Identidade e autorização

Spring Security no backend, com decisões por papel, paciente, instituição e pedido. A proposta é access token curto e refresh token rotativo e revogável, armazenado com hash no servidor. Na web, refresh em cookie HttpOnly/Secure com proteção CSRF; no mobile, credenciais no SecureStore. Access token web em memória, sem persistir documentos/tokens em localStorage.

Validar expiração, assinatura, emissor e audiência dos tokens. Autenticação WebSocket não usa token em query string. Autorizar CONNECT e cada assinatura por pedido, impedindo que o cliente escolha um tópico arbitrário de outra entrega. Revogação deve interromper futuras ações e assinaturas.

Autenticar uma conta e verificar a identidade documental são estados separados. Não criar administrador com senha padrão, nem permitir promoção de papel pelo formulário. Provisionamento administrativo precisa de procedimento controlado e auditado. Verificação de e-mail e recuperação dependem de provedor real.

## Motorista e veículo

Cadastro exige nome completo, CPF, nascimento, RG/CIN, CNH, selfie/foto atual, contato, placa, marca/modelo, cor, ano de fabricação, ano-modelo, CRLV-e e fotos atuais do veículo.

Não exigir que o carro pertença ao motorista: vínculo pode ser propriedade, locação ou autorização, sujeito à evidência e análise. Dados consultados e fotos enviadas devem corresponder ao veículo ativo. Troca de veículo ou foto deve passar por revisão antes de novas tarefas.

Na fatia implementada, `veiculo`, `vinculo_veiculo`, `vinculo_documento` e os snapshots `revisao_vinculo_*` são persistidos pela migração V6. A API mantém versão otimista no vínculo, histórico de substituição e reutilização somente por proprietário. CRLV, foto e autorização aplicável são documentos privados em quarentena; o painel e o app exibem estado, não autenticidade ou aprovação. A fila de AO usa atribuição + MFA e deixa `POLICY_UNDEFINED` quando faltam critérios profissionais.

Guardar detalhes pessoais somente em módulos privados. A representação enviada ao destinatário contém foto de perfil aprovada, nome de identificação, placa, modelo e cor da tarefa. Não incluir CPF, RG, cópias de CNH/CRLV ou dados biométricos.

Consulta oficial e foto de documento não são equivalentes. QR Code autenticado não prova sozinho a situação atual da habilitação. Verificações de habilitação, categoria e condições profissionais aplicáveis serão fechadas antes da operação real.

## Documentos

Armazenamento privado de objetos, com adaptador para S3 compatível e alternativa local restrita ao desenvolvimento. Sem bucket público ou caminho estático de documentos. A API autoriza cada acesso; URLs temporárias, se usadas, são curtas e específicas.

Limitar extensão, tamanho e tipo real de arquivo; usar identificador aleatório; descartar nomes originais do caminho; controlar acesso e retenção. Upload recebido não recebe selo de verificado. Logs não contêm documento, CPF completo, selfie, renda ou endereço residencial. Biometria não deve ser armazenada como requisito automático; política depende do fornecedor e da finalidade.

## Solicitações de benefício (V7)

`solicitacao_beneficio` captura instituição, política e versão/snapshot usados no pedido; `solicitacao_dimensao` mantém idade, deficiência e renda independentes. O paciente ou familiar só cria/lê com escopo BENEFICIOS vigente. Evidências permanecem privadas e reutilização exige mesma conta/paciente, finalidade e versão; substituição encerra a revisão corrente e preserva histórico. A fila institucional usa `membro_instituicao`; atribuição, inspeção e decisão exigem MFA e impedem autoanálise. Recurso cria revisão própria para outro analista. Ausência de política vigente bloqueia com `422 POLICY_UNDEFINED`; nenhuma decisão reserva saldo ou habilita programa.

## Aporte institucional (V8)

`programa` é uma referência administrativa pré-existente; esta fatia não cria instituição, programa ou saldo inicial. `aporte` registra valor BRL exato, origem, comprovante privado e estado PENDENTE. Dois GF nominais distintos, com vínculo institucional, capacidade global e MFA, revisam uma única vez; a conciliação preserva evidência separada e auditoria. Somente CONFIRMADO gera `lancamento_aporte`; a disponibilidade consulta exclusivamente esses lançamentos, sob idempotência e lock PostgreSQL. Nenhuma integração bancária ou transferência externa é presumida.

## Benefícios e recursos

## Pedidos e precificação (V9)

O módulo de pedidos grava endereços cifrados e autorização de retirada privada, consultando escopos PEDIDOS/RECEBIMENTO a cada chamada. `RouteProvider` é um adaptador explícito: a implementação padrão permanece indisponível até configuração real; testes isolados podem fornecer resposta controlada e identificada. `OrderService` valida distância/duração, consulta `tarifa` vigente, calcula dinheiro exato e grava snapshot, validade e origem da rota. A alteração de endereço substitui cotações antigas. Este recorte não chama provedor externo, não presume unidade aceita, não cobra, não reserva financiamento e não designa entregador.

Gratuidade por idade/deficiência e desconto econômico são decisões independentes, com evidência, regra versionada, revisão e validade apropriada. Aplicativo não diagnostica e não aprova por CID isolado. CPF/nascimento não comprova deficiência ou renda.

Faixas de renda e tarifas financeiras devem ser configuradas pelo programa e registradas; os valores exemplificados nas conversas não são preços ou critérios oficiais.

Instituição e programa têm escopo próprio. Acesso a financiamento requer acordo registrado e recursos efetivamente disponibilizados. Entrada manual de financiamento, se permitida, exige comprovação, responsável e revisão; não equivale a depósito bancário confirmado.

Não habilitar programa real com orçamento inventado. Sem financiamento disponível, benefício pode ser elegível sem que uma entrega subsidiada possa ser contratada. Oferecer espera ou orçamento particular com aceite, nunca cobrar diferença automaticamente.

Reserva, cancelamento e liquidação usam transações PostgreSQL, bloqueio adequado e chaves de idempotência. Registrar lançamentos rastreáveis. Paciente + eventual instituição = frete do serviço completo; cancelamento segue apuração D06. Receita da plataforma é separada e explicitada.

## Entrega e rastreamento

Distância e tempo vêm de rota calculada entre coordenadas confirmadas. Provedor final não foi escolhido. Não substituir rota indisponível por distância inventada. Trânsito ao vivo exige fonte específica; estimativa sem essa fonte deve ser identificada.

Orçamento registra tarifa, rota, expiração, parcelas e frete do entregador. Depois do aceite, alteração de endereço/escopo exige nova cotação e concordância.

Designação atômica impede duas aceitações. Somente entregador aprovado, veículo aprovado e usuário designado atualizam o pedido. Envio de GPS é autenticado por HTTP; backend distribui ao vivo aos participantes por WebSocket/STOMP.

Posição inclui horário da captura, horário do recebimento e precisão. Rejeitar dados inválidos, antigos/fora de ordem e posição enviada por entregador não designado. Mostrar posição desatualizada se conexão ou GPS falhar; sem ponto recebido, mostrar indisponibilidade e não criar marcador. D09 está implementada como configuração provisória: captura 15 s, stale 60 s, oito pontos/dois minutos, buffer de até oito pontos com idade máxima de dois minutos, corpo de 1 KiB, tolerância futura de 30 s e aviso de precisão acima de 100 m. Esses valores e a retenção ainda exigem ensaio em aparelho e aprovação antes de habilitar dados reais.

Navegação turn-by-turn será inicialmente delegada ao aplicativo de mapas instalado, sem construir um navegador próprio. O monitoramento acompanha as duas etapas da tarefa: aproximação à unidade e entrega ao destinatário, até concluir/cancelar.

## Integrações

| Adaptador | Propósito | Condição |
|---|---|---|
| Cadastro oficial | Conferir CPF e nascimento | Serviço contratado e finalidade definida |
| Identidade | Conferência documental; biometria adiada | Critérios profissionais e eventual credenciamento/contrato aplicáveis |
| Documento VIO | Autenticar QR compatível | API contratada ou conferência assistida |
| Senatran | Situação de condutor e veículo | Autorização, contratação e escopo permitido |
| Rotas | Distância, duração e geometria | Provedor/instância configurado |
| Pagamento | Cobrança e repasse reais | Conta, contrato e webhooks verificáveis |
| E-mail/push | Verificação, recuperação e atualização | Provedor e permissões reais |

Integração desabilitada retorna estado explícito de indisponibilidade; não usa fallback fictício. Chaves ficam fora do repositório. Não solicitar senha gov.br do usuário para acessar serviços.

## Operação e validação

PostgreSQL e arquivos persistem entre reinícios; migrações Flyway são versionadas. Dados reais nunca entram no repositório ou no pacote de distribuição. O [procedimento de backup/restauração](BACKUP-RESTAURACAO.md) cobre o adaptador local em ensaio descartável: cópia com escritas suspensas, manifesto/checksums, diário de expurgos externo, gate persistente e liberação separada. Infraestrutura real, cifragem do destino, rotação e RPO/RTO continuam critérios de deploy.

Testes essenciais: isolamento entre famílias/instituições, revogação, documentos privados, duas aceitações concorrentes, duas reservas concorrentes, repetição de webhook, cancelamento, código de recebimento, GPS antigo e perda de conexão.

A publicação da documentação no GitHub está autorizada nesta etapa, em branch sem deploy. Deploy da aplicação só depois de implementação, verificação e autorização posterior. App mobile precisa de build e instalação reais; uma URL web não equivale à entrega do aplicativo nativo.

## Referências técnicas

- Spring Security: https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html
- Expo Location: https://docs.expo.dev/versions/latest/sdk/location/
- OSRM: https://project-osrm.org/docs/v5.24.0/api/
- Datavalid e requisitos: https://centraldeajuda.serpro.gov.br/duvidas/pt/avisos/datavalidsenatran/
- Consulta Senatran: https://centraldeajuda.serpro.gov.br/consultasenatran/comofunciona/

## Detalhamento técnico

[Modelo físico](MODELO-DADOS.md), [matriz de segurança](SEGURANCA.md) e [OpenAPI](../contracts/openapi.yaml) detalham este desenho. [STATUS](STATUS.md) separa verificações realizadas de trabalho futuro.

## Direções adotadas em 30/09/2026

[D01–D12](DECISOES-PENDENTES.md) distinguem arquitetura adotada, política operacional pendente e integração indisponível. Primeiro marco: versão funcional validada localmente, sem piloto presumido. Cadastro/segurança independem de programa subsidiado. Particular e subsidiado exigem habilitação real própria. Biometria adiada; seleção por requisitos/custo total, sem orçamento ou compra autorizada. Nenhuma unidade confirmada. Após retirada, custódia e retorno precisam de protocolo e cobertura; cancelamento não garante frete integral no aceite.

## Fatias de identidade implementadas

Backend/React, Flyway V1–V17 e PostgreSQL cobrem as fatias locais registradas em [STATUS](STATUS.md). V15 adiciona solicitações próprias de acesso, correção e exclusão com protocolo, resposta cifrada e acompanhamento; V17 acrescenta encerramento idempotente e transformação controlada da conta. Familiar ou outra conta não atua em nome do titular por esse endpoint. Resposta, autorização do expurgo, execução, encerramento e verificação são transições diferentes. Cada ação privilegiada revalida sessão, papel nominal e MFA.

O executor V15 inventaria e trata banco, objetos locais, posições GPS, outbox não financeira e cache de conexões. V17 também revoga credenciais/concessões, transforma identificadores controlados e bloqueia custódia/obrigações sem cascata; a especificação detalhada está em [ENCERRAMENTO-CONTA](ENCERRAMENTO-CONTA.md). Versões inexistentes são `NAO_APLICAVEL`; temporários sem vínculo, dispositivos e fornecedores ficam `PROCEDIMENTO_PENDENTE`, nunca como remoção comprovada. Referências financeiras são `PRESERVADO` enquanto a retenção específica não for validada. V16/V17 adicionam gate/recibos de restore; os comandos em `ops/recovery/` reaplicam o diário externo antes de liberar acesso. O ensaio não fornece infraestrutura externa, captura automática monitorada do diário ou política de rotação; `privacy.purge-enabled=false` é o padrão.
