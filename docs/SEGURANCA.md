# Segurança, permissões e privacidade

Proposta técnica para revisão em 29/09/2026. Controles abaixo são requisitos a implementar; não representam auditoria aprovada, conformidade jurídica certificada ou integração contratada. [Modelo físico](MODELO-DADOS.md), [OpenAPI](../contracts/openapi.yaml), [backlog](../.scratch/planejamento/README.md).

## Regra de autorização

Negar por padrão. Autenticação não concede identidade verificada nem aprovação profissional. Para cada requisição: sessão válida → papel/capacidade → relação com o registro → finalidade → estado/validade → permissão sobre os campos retornados. UUID imprevisível não é autorização. Listas, contagens e cursores aplicam os mesmos filtros antes de paginar. Recurso alheio retorna 404 sem revelar sua existência; 403 fica para ação conhecida sem capacidade.

`P`: paciente titular; `F`: familiar com concessão vigente e escopo específico; `E`: entregador; `AO`: analista operacional atribuído; `AB`: analista de benefício membro ativo da instituição e atribuído; `GF`: gestor financeiro da instituição; `AD`: administrador com capacidade administrativa. AO e AB são capacidades distintas do papel de analista, não papéis concedidos no cadastro. GF é uma capacidade institucional de gestão, provisionada separadamente. AD só acessa documento clínico se também tiver capacidade/finalidade específica; ser administrador não contorna a matriz.

## Matriz por ação e registro

| Ação / superfície | Quem pode | Restrição de registro, campos e estado |
|---|---|---|
| Cadastro, login, recuperação | Público; desafio autenticado para confirmação | Sem campo de papel; resposta genérica em cadastro/recuperação; rate limit; sem credencial/provedor, indisponível. |
| Perfil e sessões | Próprio usuário | Sem enumeração de contas, hashes/tokens nunca retornados em perfil. Logout revoga sessão; recuperação revoga todas. |
| Criar perfil de paciente | Próprio usuário | CPF único, identidade PENDENTE; não atesta elegibilidade. |
| Conceder/listar/revogar familiar | P verificado | Paciente da concessão deve ser o titular; F não delega nem promove; expiração e escopos explícitos. Sem descoberta pública de conta por CPF/e-mail; seleção/convite seguro do familiar ainda precisa de fluxo aprovado. |
| Upload de evidências | Dono; F BENEFICIOS/PEDIDOS conforme finalidade; GF para financiamento | Documento pertence ao paciente/programa autorizado. Upload não aprova conteúdo. Identidade do entregador não pode ser enviada por familiar de paciente. |
| Ler/download de evidências | Dono, F com finalidade concedida, analista atribuído ou GF responsável pelo comprovante | Cada categoria exige finalidade. AO não lê renda/deficiência; AB não lê CNH; GF não lê laudo. AD sem atribuição não lê nenhum desses. |
| Submeter entregador/veículo | E titular, mesmo antes da aprovação | Apenas documentos próprios e vínculo comprovado; sem autoaprovação. |
| Revisar entregador/veículo | AO atribuído | Revisão fora da própria conta, com evidências; foto nova não herda aprovação anterior. |
| Ver programas oferecidos | Conta autenticada | Somente programas habilitados; não expõe saldo, acordos ou lista de beneficiários. |
| Solicitar/listar benefício | P ou F BENEFICIOS | Mesmo paciente; dimensões IDADE/DEFICIENCIA/RENDA independentes; recurso vinculado ao processo do mesmo paciente/programa. |
| Analisar/decidir benefício | AB atribuído | Instituição deriva do programa, não de parâmetro livre; rejeitar autoanálise/conflito de interesse; percentual calculado por regra aprovada. |
| Consultar fila/caso operacional | AO/AD conforme capacidade | AO somente casos atribuídos e documentos da finalidade; AD vê fila mínima para atribuir, sem documentos clínicos. |
| Atribuir análise | AD com capacidade de gestão | Analista ativo e habilitado no escopo; auditoria contém alvo/ator, não laudo. |
| Criar/consultar pedido, orçamento, aceite | P ou F PEDIDOS | Paciente da solicitação autorizado; destinatário válido; aceite explícito das parcelas; frete calculado no servidor. |
| Consultar pedido operacional | E designado ou AO atribuído | Informações mínimas da tarefa; não expõe comprovações de benefício nem motivo clínico. |
| Consultar ofertas | E e vínculo aprovados | Cidade, distância e frete; sem nome do paciente, endereço exato ou conteúdo do envelope antes do aceite. |
| Aceitar oferta | E e vínculo próprios aprovados | Somente DISPONIVEL com cobertura; lock e índice único; versão da oferta e idempotência. |
| Ver endereços/retirada | P, F PEDIDOS, E ativo; AO em ocorrência atribuída | Dados necessários à tarefa; E perde acesso operacional após encerramento. Histórico de remuneração continua mínimo. |
| Ver identificação operacional | P ou F PEDIDOS/RECEBIMENTO | Tarefa ativa; nome operacional, foto aprovada temporária e veículo; nenhum CPF/CNH/CRLV. |
| Confirmar retirada/início/entrega | E designado ativo | Autorização de retirada válida; ordem de estados; código do destinatário para conclusão. |
| Emitir código de recebimento | Destinatário registrado | Deve ser P ou F com RECEBIMENTO ainda vigente; rate limit; E não obtém código pela API. Troca de destinatário exige rever autorização, não editar silenciosamente. |
| Cancelar | P/F PEDIDOS ou AO atribuído | Antes da retirada e conforme política aceita; depois abre ocorrência. Não implica estorno já confirmado. |
| Abrir ocorrência | Participante autorizado ou AO atribuído | Pedido relacionado; texto livre privado e saneado; não publica diagnóstico. |
| Resolver ocorrência | AO atribuído | Política, concordância e custeio verificados; sem regra aprovada permanece pendente. |
| Enviar GPS | E da designação ativa | Pedido e designação do mesmo vínculo; sequência/horário/precisão válidos; rejeitar terminal. |
| Consultar/assinar GPS | P, F RASTREAMENTO ou E designado ativo | Só pedido ativo e concessão vigente; AO/AB/GF/AD sem participação não recebem GPS por padrão. |
| Criar programa pendente | AD habilitado | Instituição existente; não habilita financiamento automaticamente. |
| Registrar/ver aporte e saldo | GF da instituição | Programa da instituição autenticada; comprovante privado; saldo não aumenta ao cadastrar aporte. |
| Confirmar aporte | Outro GF da mesma instituição | Segregação registrador/revisor; conciliação comprovada; idempotência e crédito único. |
| Consultar operação financeira | P/F PEDIDOS, E ou GF conforme parcela | Paciente vê cobrança/estorno próprios; E vê repasse próprio; GF vê subsídio institucional. Nenhum dado bancário alheio. |
| Webhook financeiro | Provedor contratado autenticado | Assinatura dos bytes, replay, referência, valor/moeda/destinatário; conta de usuário não substitui autenticação de provedor. |
| Acessos/políticas/integrações | AD via procedimento controlado | Sem rota pública de promoção; habilitação requer acordo e configuração reais, revisão/auditoria; procedimento definitivo é bloqueador da respectiva operação. |
| Solicitação de titular | Próprio usuário | Protocolo privado; análise de finalidade e impedimento legal antes de exclusão; não promete prazo ainda indefinido. |

## Sessões, autenticação e uploads

Senhas com Argon2id por biblioteca consolidada e custo medido, salt próprio da biblioteca, sem criptografia reversível. Parâmetros e versões serão fixados na implementação. Access token curto: validar assinatura, algoritmo permitido, emissor, audiência e expiração; consultar revogação/versão de autorização nas ações sensíveis. Refresh rotativo, hash no banco, revogação da família ao detectar reutilização. Tokens/IDs de sessão não entram em URL nem logs.

Web: access em memória, refresh em cookie HttpOnly/Secure/SameSite; origem permitida e token CSRF em endpoints com cookie, incluindo refresh/logout. Login exige Origin permitido. Mobile: refresh no SecureStore; não aceitar cookie como substituto silencioso do modo mobile. Recuperação usa token aleatório de uso único, expiração e limite de tentativas; resposta não confirma existência de e-mail. Bootstrap administrativo por operador identificado, sem conta/senha padrão; MFA e requisitos de recuperação administrativa são pendências antes de operação privilegiada.

Upload: limite técnico proposto de 10 MiB, tipos JPEG/PNG/PDF, inspeção por assinatura real e parser restrito, quarentena e varredura antes de DISPONIVEL. Nome/chave gerados no servidor; impedir path traversal, conteúdo ativo, descompressão excessiva e reuso de objeto; retirar metadados de fotos quando compatível com a finalidade. Verificar tamanho após upload e hash, sem confiar no Content-Type declarado. Buckets privados; download reautoriza e gera URL curta específica. TTL definitivo deve ser aprovado e configura o limite de revogação de URLs já emitidas; revogação estrita exige proxy autenticado de download em vez de URL durável. URLs não aparecem em analytics, Referer ou logs.

Não armazenar conteúdo dos exames como requisito: transporte é de envelope fechado. Evidência de deficiência/renda continua sensível, mesmo sem arquivo do resultado clínico. Documentos em quarentena nunca aparecem em foto operacional.

## WebSocket/STOMP e localização

OpenAPI cobre HTTP, não o protocolo STOMP. Contrato complementar proposto:

- Endpoint `/ws`, TLS; CONNECT recebe `Authorization: Bearer ...` em header STOMP, nunca query string. Validar Origin web e sessão; limitar tamanho, conexões e frequência. Não usar cookie sozinho para autenticar CONNECT.
- SUBSCRIBE permitido apenas em `/topic/orders/{orderId}/state` (participantes com acesso ao pedido) e `/topic/orders/{orderId}/location` (matriz GPS). Lista explícita de destinos, sem curingas ou tópicos arbitrários. Client SEND para publicação é negado; GPS entra pelo HTTP autenticado.
- Evento de estado: `eventId`, `orderId`, `sequence`, `occurredAt`, `status`, `version`; localização: `eventId`, `orderId`, campos de Location do OpenAPI. Nenhum endereço/documento/código de recebimento em tópicos.
- Autorizar CONNECT, cada SUBSCRIBE e cada despacho. Sessão expirada/revogada, familiar revogado e tarefa encerrada invalidam assinaturas; descartar mensagens enfileiradas que perderam autorização. Evento terminal pode ser emitido uma vez para participante ainda autorizado antes de encerrar; nenhum GPS após o ponto efetivo de encerramento.
- Reconnect faz GET autorizado do estado/última posição; eventos duplicados são deduplicados por ID/sequence. Não oferecer histórico irrestrito de GPS. Última posição indica idade/stale; sem ponto real, mostrar indisponível. Limiares de idade, frequência e buffer dependem de medição real e são obrigatórios para habilitar rastreamento.
- Suspeita de GPS falsificado, precisão ruim ou salto impossível sinaliza revisão; a assinatura do usuário não prova localização física. O sistema não deve declarar prevenção absoluta de spoofing.

## Ameaças e evidências exigidas

| Risco | Controle planejado | Evidência a produzir na implementação |
|---|---|---|
| IDOR/BOLA e mistura de instituições | Escopo por paciente/tenant em query e comando, FKs compostas e matriz de campos | REST com paciente alheio, familiar revogado, cursor reaproveitado e programa de outra instituição. |
| Promoção de papel/mass assignment | DTO fechado sem role/status, provisionamento auditado | Envio de campos extras rejeitado; cadastro nunca vira AO/AB/GF/AD. |
| Duplo aceite e gasto de saldo | Lock, atualização condicional, índice parcial e operação de negócio única | Concorrência em PostgreSQL real: só uma designação e nenhum saldo negativo. |
| Duplicação de cobrança/repasse | Idempotência HTTP, inbox/outbox, chave externa estável e conciliação | Replay de comando/webhook e timeout não duplicam efeito; cancelamento concorrente não ressuscita pedido. |
| Webhook falso/reordenado | Assinatura dos bytes e reconciliação por referência/valor/moeda/destinatário | Assinatura inválida e hash divergente negados; evento antigo não regride estado. |
| Assinatura indevida/GPS vazado | ACL por assinatura e despacho, encerramento por revogação | Usuário alheio, curinga, token expirado e revogação com mensagem no buffer não recebem ponto. |
| Roubo de sessão/CSRF | Rotação, revogação, SecureStore/cookie, Origin/CSRF e rate limit | Refresh reutilizado revoga família; origem não permitida e CSRF ausente rejeitados. |
| Vazamento por arquivo/log/cache | Quarentena, URL curta, objeto privado, logs saneados, no-store | MIME falso, traversal, excesso de tamanho; inspeção de logs/respostas para dados proibidos. |
| Fraude/conflito em análise | Atribuição, segregação, evidências e trilha | Analista fora do tenant ou julgando a própria solicitação negado; aporte exige outro revisor. |
| Código de recebimento adivinhado | Hash, tentativas, validade e invalidação | Brute force limitado; código de outro pedido e reutilização rejeitados. |
| Perda de dados/retorno de dado expurgado | Backup cifrado, restore testado e tombstone de exclusão | Restauração isolada e reaplicação de expurgos; acesso a backups auditado. |

## Retenção, finalidade e pendências

| Categoria | Finalidade / leitores | Política pendente e ação de encerramento |
|---|---|---|
| Conta, CPF e nascimento | Identificação mínima / titular e verificação autorizada | Definir necessidade e prazo; desativar sessão, anonimizar/excluir quando permitido; HMAC também é dado protegido. |
| CNH, RG, CRLV, selfie | Aprovar vínculo/aptidão / AO atribuído | Prazo por evidência e validade; expurgo do objeto e miniaturas; biometria não será armazenada automaticamente. |
| Deficiência, idade, renda | Decidir benefício / P, F autorizado, AB atribuído | Base/finalidade e retenção documental validadas com responsável jurídico/privacidade; evitar laudo completo se comprovação mínima bastar. |
| Endereços, autorização e código | Executar entrega / participantes mínimos | Código expira/invalida; endereço deixa projeção operacional após tarefa; decidir retenção probatória mínima. |
| GPS | Acompanhar tarefa ativa / participantes autorizados | Prazo mínimo baseado em finalidade e necessidade; encerrar compartilhamento imediatamente; expurgo de posições/filas e cache. |
| Finanças, idempotência e eventos externos | Deduplicação, conciliação e prestação de contas / participantes por parcela | Validar obrigações e prazo; preservar chave de negócio enquanto houver risco de replay; payload externo mínimo e privado. |
| Auditoria e acessos administrativos | Investigar ações / equipe autorizada | Definir prazo, integridade, acesso e procedimento de exportação; sem copiar documentos em eventos. |
| Backups/observabilidade | Recuperação e operação / operadores autorizados | Definir rotação, RPO/RTO, expurgo diferido e restore; chaves separadas dos backups. |

Não há prazos legais presumidos. Antes de produção, cada categoria exige responsável, finalidade/base validada, período, gatilho, descarte, tratamento de bloqueio legal e destino em backups. Campo de prazo nulo significa configuração incompleta, não autorização de retenção indefinida. Sem política aprovada, manter a categoria/integração desabilitada para dados reais.

Pendências materiais: comprovação e recurso de benefícios; faixas de renda; custeio/tarifas; cancelamento e remuneração em ocorrência; seleção/contratos de provedores; critérios profissionais de entregador; representação legal/incapacidade; recuperação e bootstrap privilegiado; convite seguro de familiares; limiares/retencão de GPS; política de exclusão; habilitação institucional e dupla revisão financeira. Registrar decisões com responsável antes de mover os tickets dependentes para execução.

## Limites desta etapa

Não foram executados testes de penetração, transações PostgreSQL, aparelhos Android, integração bancária ou varredura de infraestrutura. Foram planejados controles e cenários; validações documentais efetivas constam em [STATUS](STATUS.md). Segurança, escalabilidade e conformidade precisam de evidência posterior.
