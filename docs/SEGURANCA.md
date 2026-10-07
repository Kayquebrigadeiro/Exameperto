# Segurança, permissões e privacidade

Planejamento revisto em 02/10/2026 conforme D01–D12. Conta, sessão e autorização familiar já têm implementação local; os demais controles continuam requisitos. Isso não representa auditoria aprovada, conformidade jurídica certificada ou integração contratada. [Modelo físico](MODELO-DADOS.md), [OpenAPI](../contracts/openapi.yaml), [backlog](../.scratch/planejamento/README.md).

## Regra de autorização

Negar por padrão. Autenticação não concede identidade verificada nem aprovação profissional. Para cada requisição: sessão válida → papel/capacidade → relação com o registro → finalidade → estado/validade → permissão sobre os campos retornados. UUID imprevisível não é autorização. Listas, contagens e cursores aplicam os mesmos filtros antes de paginar. Recurso alheio retorna 404 sem revelar sua existência; 403 fica para ação conhecida sem capacidade.

`P`: paciente titular; `F`: familiar com concessão vigente e escopo específico; `E`: entregador; `AO`: analista operacional atribuído; `AB`: analista de benefício membro ativo da instituição e atribuído; `GF`: gestor financeiro da instituição; `AD`: administrador com capacidade administrativa. AO e AB são capacidades distintas do papel de analista, não papéis concedidos no cadastro. GF é uma capacidade institucional de gestão, provisionada separadamente. AD só acessa documento clínico se também tiver capacidade/finalidade específica; ser administrador não contorna a matriz.

## Matriz por ação e registro

| Ação / superfície | Quem pode | Restrição de registro, campos e estado |
|---|---|---|
| Cadastro, login, recuperação | Público; desafio autenticado para confirmação | Sem campo de papel; resposta genérica em cadastro/recuperação; rate limit. Cadastro/recuperação dependentes de e-mail retornam indisponível sem provedor, sem conta/desafio parcial; autenticação de conta já habilitada não exige financiador. |
| Perfil e sessões | Próprio usuário | Sem enumeração de contas, hashes/tokens nunca retornados em perfil. Logout revoga sessão; recuperação revoga todas. |
| Criar perfil de paciente | Próprio usuário | CPF único, identidade PENDENTE; não atesta elegibilidade. |
| Conceder/listar/revogar familiar | P adulto titular | O perfil pode manter identidade PENDENTE, sem converter e-mail, CPF ou nascimento em verificação. F não delega nem promove; expiração e escopos são explícitos. Convite privado ao destinatário, aceite autenticado e confirmação reautenticada do paciente; aceite sozinho não concede acesso. Expiração/revogação efetivas. Menores e representação legal fora deste primeiro recorte. |
| Upload de evidências | Dono; F BENEFICIOS/PEDIDOS conforme finalidade; GF para financiamento | Documento pertence ao paciente/programa autorizado. Upload não aprova conteúdo. Identidade do entregador não pode ser enviada por familiar de paciente. |
| Evidência operacional de retirada/retorno/serviço | E designado ativo ou histórico com apuração pendente; AO atribuído; P/F PEDIDOS quando pertinente | RETIRADA/COMPROVANTE vinculada ao mesmo pedido; leitura por finalidade/participação, sem acesso a documentos de benefício. Históricos só acessam prova mínima necessária à apuração, nunca endereço/GPS/código expirados. |
| Ler/download de evidências | Dono, F com finalidade concedida, analista atribuído ou GF responsável pelo comprovante | Cada categoria exige finalidade. AO não lê renda/deficiência; AB não lê CNH; GF não lê laudo. AD sem atribuição não lê nenhum desses. |
| Submeter entregador/veículo | E titular, mesmo antes da aprovação | Apenas documentos próprios e vínculo comprovado; sem autoaprovação. |
| Revisar entregador/veículo | AO atribuído | Revisão fora da própria conta, com evidências; foto nova não herda aprovação anterior. |
| Ver programas oferecidos | Conta autenticada | Somente programas habilitados; não expõe saldo, acordos ou lista de beneficiários. |
| Solicitar/listar benefício | P ou F BENEFICIOS | Mesmo paciente; dimensões IDADE/DEFICIENCIA/RENDA independentes; recurso vinculado ao processo do mesmo paciente/programa. |
| Analisar benefício | AB membro ativo da instituição, caso atribuído e MFA verificado | Não autoanalisar; recurso exige analista diferente. Documento de outra conta, escopo expirado/revogado e versão obsoleta são recusados (404/403/412 conforme operação). |
| Política/decisão | Política ATIVA vigente e versionada | Ausência de política/critério retorna `422 POLICY_UNDEFINED`; `503 INTEGRATION_UNAVAILABLE` é reservado a integração técnica indisponível. CPF, e-mail confirmado, upload ou inspeção estrutural não provam elegibilidade; decisão não garante financiamento. |
| Aporte/disponibilidade | GF nominal, membro ativo da instituição/programa e MFA; revisão por outro GF | Upload e registro ficam PENDENTES. Só conciliação com evidência privada do revisor, versão e trilha cria lançamento confirmado; saldo é soma de lançamentos confirmados. Instituição/programa alheios retornam 403/404, sem consulta bancária fictícia. |
| Analisar/decidir benefício | AB atribuído | Instituição deriva do programa, não de parâmetro livre; rejeitar autoanálise/conflito de interesse; recurso exige outro analista real; revisão administrativa não diagnostica; percentual calculado por regra aprovada. |
| Consultar fila/caso operacional | AO/AD conforme capacidade | AO somente casos atribuídos e documentos da finalidade; AD vê fila mínima para atribuir, sem documentos clínicos. |
| Atribuir análise | AD com capacidade de gestão | Analista ativo e habilitado no escopo; auditoria contém alvo/ator, não laudo. |
| Criar/consultar pedido, orçamento, aceite | P ou F PEDIDOS | Paciente da solicitação autorizado; destinatário válido; aceite explícito das parcelas; frete calculado no servidor. |
| Consultar pedido operacional | E designado ou AO atribuído | Informações mínimas da tarefa; não expõe comprovações de benefício nem motivo clínico. |
| Consultar ofertas | E e vínculo aprovados | Cidade, distância e frete; sem nome do paciente, endereço exato ou conteúdo do envelope antes do aceite. |
| Aceitar oferta | E e vínculo próprios aprovados | Somente DISPONIVEL com cobertura; lock e índice único; versão da oferta e idempotência. |
| Ver endereços/retirada | P, F PEDIDOS, E ativo; AO em ocorrência atribuída | Dados necessários à tarefa; E perde acesso operacional após encerramento. Histórico de remuneração continua mínimo. |
| Ver identificação operacional | P ou F PEDIDOS/RECEBIMENTO | Tarefa ativa; nome operacional, foto aprovada temporária e veículo; nenhum CPF/CNH/CRLV. |
| Confirmar retirada/início/entrega | E designado ativo | Autorização de retirada válida; ordem de estados; código do destinatário para conclusão; prova estruturada de validação no evento ENTREGUE encerra custódia atomicamente, sem guardar código em claro. Retorno usa prova documental mínima. |
| Emitir código de recebimento | Destinatário registrado | Deve ser P ou F com RECEBIMENTO ainda vigente; rate limit; E não obtém código pela API. Troca de destinatário exige rever autorização, não editar silenciosamente. |
| Cancelar | P/F PEDIDOS ou AO atribuído | Antes da retirada e conforme política aceita; depois abre ocorrência. Não implica estorno já confirmado. |
| Abrir ocorrência | Participante autorizado ou AO atribuído | Pedido relacionado; texto livre privado e saneado; não publica diagnóstico. |
| Resolver ocorrência | AO atribuído | Política, concordância e custeio verificados; retomada restaura apenas etapa de origem e nunca salta retirada; sem regra aprovada permanece pendente. Depois da retirada, destino autorizado comprovado e cobertura de retorno são obrigatórios; não encerrar custódia por decisão meramente administrativa. |
| Enviar GPS | E da designação ativa | Pedido e designação do mesmo vínculo; sequência/horário/precisão válidos; rejeitar terminal. |
| Consultar/assinar GPS | P, F RASTREAMENTO ou E designado ativo | Só pedido ativo e concessão vigente; AO/AB/GF/AD sem participação não recebem GPS por padrão. |
| Criar programa pendente | AD habilitado | Instituição existente; não habilita financiamento automaticamente. |
| Registrar/ver aporte e saldo | GF da instituição | Programa da instituição autenticada; comprovante privado; saldo não aumenta ao cadastrar aporte. |
| Confirmar aporte | Outro GF da mesma instituição | Segregação registrador/revisor; conciliação comprovada; evidência DISPONIVEL da mesma instituição e finalidade financeira, preservada com revisor, data e motivo sem substituir comprovante original; idempotência e crédito único. |
| Consultar operação financeira | P/F PEDIDOS, E ou GF conforme parcela | Paciente vê cobrança/estorno próprios; E vê repasse próprio; GF vê subsídio institucional. Nenhum dado bancário alheio. |
| Webhook financeiro | Provedor contratado autenticado | Assinatura dos bytes, replay, referência, valor/moeda/destinatário; conta de usuário não substitui autenticação de provedor. |
| Acessos/políticas/integrações | AD via procedimento controlado | Sem rota pública de promoção; habilitação requer acordo e configuração reais, revisão/auditoria; procedimento definitivo é bloqueador da respectiva operação. |
| Solicitação de titular | Próprio usuário | Protocolo privado; análise de finalidade e impedimento legal antes de exclusão; não promete prazo ainda indefinido. |

## Sessões, autenticação e uploads

Senhas com Argon2id por biblioteca consolidada e custo medido, salt próprio da biblioteca, sem criptografia reversível. Parâmetros e versões serão fixados na implementação. Access token curto: validar assinatura, algoritmo permitido, emissor, audiência e expiração; consultar revogação/versão de autorização nas ações sensíveis. Refresh rotativo, hash no banco, revogação da família ao detectar reutilização. Tokens/IDs de sessão não entram em URL nem logs.

Web: access em memória, refresh em cookie HttpOnly/Secure/SameSite; origem permitida e token CSRF em endpoints com cookie, incluindo refresh/logout. Login exige Origin permitido. Mobile: refresh no SecureStore; não aceitar cookie como substituto silencioso do modo mobile. Recuperação usa token aleatório de uso único, expiração e limite de tentativas; resposta não confirma existência de e-mail. Bootstrap administrativo por operador identificado, sem conta/senha padrão; MFA é obrigatório para AD/AO/AB/GF. Implementação local: TOTP de seis dígitos, segredo cifrado, confirmação, anti-reuso por passo, limite de cinco falhas em 15 minutos e elevação de cinco minutos vinculada à sessão. Enrollment exige papel nominal e senha atual; rotação limpa elevações. Refresh não herda MFA e logout/revogação invalida a sessão. Não há recuperação/bypass do fator: bootstrap, recuperação privilegiada e conferência independente continuam bloqueios operacionais. Sem segunda pessoa real, ações de dupla revisão ficam bloqueadas; nenhuma conta privilegiada padrão.

Upload: limite efetivo local de 10 MiB, tipos JPEG/PNG/PDF validados por assinatura e quarentena. A inspeção estrutural local exige AO atribuído e MFA; exige fechamento válido dos formatos e rejeita recursos ativos conhecidos de PDF. Essa inspeção de segurança do arquivo **não** comprova autenticidade documental, situação atual da habilitação ou aprovação profissional. Nome/chave gerados no servidor; caminhos normalizados, symlinks recusados e permissões POSIX `0700` para diretórios/`0600` para objetos quando disponíveis. Verificar tamanho após upload e hash, sem confiar no Content-Type declarado. Substituição cria novo documento/revisão e preserva histórico mínimo. Armazenamento local privado é a implementação isolada desta fatia; operação externa, backups e varredura antimalware abrangente continuam indisponíveis. Download usa proxy autenticado, com nova autorização por acesso e Cache-Control: no-store. Exclusão física do objeto foi testada na fronteira de armazenamento, mas expurgo de dados reais permanece desabilitado sem política/responsável e tratamento de backups. Foto operacional não é projetada como aprovada automaticamente. Revogação bloqueia novos acessos; não apaga cópia já recebida.

Não armazenar conteúdo dos exames como requisito: transporte é de envelope fechado. Evidência de deficiência/renda continua sensível, mesmo sem arquivo do resultado clínico. Documentos em quarentena nunca aparecem em foto operacional.

## WebSocket/STOMP e localização

OpenAPI cobre HTTP, não o protocolo STOMP. Contrato complementar proposto:

- Endpoint `/ws`, TLS; CONNECT recebe `Authorization: Bearer ...` em header STOMP, nunca query string. Validar Origin web e sessão; limitar tamanho, conexões e frequência. Não usar cookie sozinho para autenticar CONNECT.
- SUBSCRIBE permitido apenas em `/topic/orders/{orderId}/state` (participantes com acesso ao pedido) e `/topic/orders/{orderId}/location` (matriz GPS). Lista explícita de destinos, sem curingas ou tópicos arbitrários. Client SEND para publicação é negado; GPS entra pelo HTTP autenticado.
- Evento de estado: `eventId`, `orderId`, `sequence`, `occurredAt`, `status`, `version`; localização: `eventId`, `orderId`, campos de Location do OpenAPI. Nenhum endereço/documento/código de recebimento em tópicos.
- Autorizar CONNECT, cada SUBSCRIBE e cada despacho. Sessão expirada/revogada, familiar revogado e tarefa encerrada invalidam assinaturas; descartar mensagens enfileiradas que perderam autorização. Evento terminal pode ser emitido uma vez para participante ainda autorizado antes de encerrar; nenhum GPS após o ponto efetivo de encerramento.
- Reconnect faz GET autorizado do estado/última posição; eventos duplicados são deduplicados por ID/sequence. Não oferecer histórico irrestrito de GPS. Última posição indica idade/stale; sem ponto real, mostrar indisponível. Limiares de idade, frequência e buffer dependem de medição real e são obrigatórios para habilitar rastreamento.
- Suspeita de GPS falsificado, precisão ruim ou salto impossível sinaliza revisão; a assinatura do usuário não prova localização física. O sistema não deve declarar prevenção absoluta de spoofing.

Ticket 04 implementa a fronteira HTTP que consulta `autorizacao_paciente` e `autorizacao_escopo` em cada leitura concedida. Módulos futuros de pedidos, benefícios, documentos e rastreamento devem chamar essa mesma decisão dentro da própria operação, após carregar o registro, sem copiar escopos para sessão/JWT. O servidor STOMP ainda não existe; quando implementado, SUBSCRIBE e cada despacho deverão repetir a consulta e encerrar a assinatura após revogação/expiração. Não há alegação de cobertura STOMP nesta fatia.

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
| Vazamento por arquivo/log/cache | Quarentena, proxy reautorizado, objeto privado, logs saneados, no-store | MIME falso, traversal, excesso de tamanho; inspeção de logs/respostas para dados proibidos. |
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

Pendências materiais de validação das direções adotadas: documentos/critério e prazos do recurso de benefícios; faixas de renda; custeio/tarifas; cancelamento e remuneração em ocorrência; seleção/contratos de provedores; critérios profissionais de entregador; representação legal/incapacidade; recuperação e bootstrap privilegiado; prazos do convite adulto já adotado; medição dos limiares/retenção de GPS; política de exclusão; habilitação institucional e dupla revisão financeira. Registrar decisões com responsável antes de mover os tickets dependentes para execução.

## Pedidos e orçamento particular (V9)

Pedido e endereços exigem titularidade ou concessão PEDIDOS vigente reavaliada no backend; destinatário exige titularidade ou RECEBIMENTO vigente. Evidência de autorização de retirada é privada, pertence à conta que a envia e não comprova aceite da unidade. A unidade/protocolo continua bloqueada até procedimento real. Rota usa adaptador identificado, distância/duração e horário da consulta; não há linha reta ou fallback silencioso. Tarifa é versionada e seus parâmetros ficam no snapshot do orçamento; ausência de rota retorna `503 INTEGRATION_UNAVAILABLE`, ausência de política tarifária `422 POLICY_UNDEFINED`. Alteração de endereço invalida orçamento anterior e exige nova cotação/aceite.

V10 implementa o aceite com nova consulta da concessão PEDIDOS, versão atual do pedido, versão/vigência do orçamento, comparação exata de valores e snapshot imutável. Particular não consulta instituição: exige adaptador de pagamento habilitado e permanece pendente até evento externo autenticado. Subsidiado exige benefício ainda vigente, política ativa, programa habilitado e reserva transacional do saldo comprovado; aprovação isolada não é disponibilidade. A entrada externa não aceita token do frontend como confirmação: bytes originais são entregues ao adaptador, que autentica e normaliza; ID/hash deduplicam, divergência de valor/moeda/destinatário é rejeitada e resultado incerto permanece `INCERTA`. O adaptador padrão retorna `503`, sem endpoint de sucesso fictício. Designação, liquidação, repasse e política de cancelamento/estorno continuam fora deste recorte.

## Ofertas e designação (V11)

Ofertas exigem conta, entregador e vínculo/veículo aprovados e vigentes, além de cobertura efetiva: cobrança particular `CONFIRMADA` quando devida e reserva subsidiada integral ainda `RESERVADA`. Aprovação de benefício, `PENDENTE`, `INCERTA`, reserva insuficiente ou liberada não habilitam a projeção. Protocolo da unidade, cobertura de retorno e política de capacidade precisam estar habilitados; nenhum registro real é criado automaticamente.

Antes do aceite, a API móvel recebe somente cidades, distância, frete/moeda, versão e política de cancelamento. O endereço exato só é autorizado ao paciente/familiar pertinente e ao entregador com designação ativa; outro entregador recebe `404`. A identificação exibida ao paciente é snapshot de nome operacional, foto aprovada e placa/modelo/cor, sem CPF, CNH, CRLV, saúde, renda ou benefício. Consultas e downloads futuros da foto devem preservar essa autorização por participação, sem tornar o documento público.

O aceite usa `If-Match`, `Idempotency-Key`, lock do pedido e lock do entregador/vínculo. Aprovação, vigência, cobertura, protocolo e limite são reavaliados dentro da mesma transação; consulta anterior da oferta não concede direito ao aceite. Índice parcial impede duas designações ativas no mesmo pedido e o lock do entregador serializa aceites de pedidos diferentes para aplicar a capacidade configurada. Evento, mudança do pedido, designação e deduplicação confirmam ou revertem juntos. Mudança de endereço e cancelamento bloqueiam o mesmo pedido; após designação ficam recusados enquanto a política operacional/financeira pendente não permitir tratamento seguro.

A aplicação mantém a operação real bloqueada porque os tickets 05–06 não aprovam profissionais/veículos e porque não há política de capacidade ou protocolo/cobertura real cadastrados. Aprovações e recursos de política usados nos testes são sintéticos e inseridos apenas no PostgreSQL descartável. Suspensão impede novos aceites; o tratamento de designação já ativa continua dependente da política de ocorrência/cancelamento e não foi inventado neste ticket.

## Limites desta etapa

Não foram executados testes de penetração, transações PostgreSQL, aparelhos Android, integração bancária ou varredura de infraestrutura. Foram planejados controles e cenários; validações documentais efetivas constam em [STATUS](STATUS.md). Segurança, escalabilidade e conformidade precisam de evidência posterior.

## Habilitação e inventário D07–D12

O inventário por categoria acima deve registrar base a validar, necessidade, compartilhamentos/operadores, política de retenção versionada e responsável real antes da habilitação. Responsáveis institucionais, controlador/operadores, encarregado quando aplicável e equipe continuam **pendentes**, sem pessoa jurídica presumida. Solicitações e incidentes exigem procedimento com registro, atribuição real, análise, execução e verificação; protocolo não equivale a atendimento concluído.

[Prazos propostos D08](DECISOES-PENDENTES.md) são apenas insumos para validação. Retenção configurável trata banco, objetos/versionamento, temporários, filas, caches e backups. Expurgo distingue solicitado, autorizado, executado e verificado; restauração reaplica exclusões antes de acesso. GPS não pode persistir em backup geral por mais tempo que a janela validada da categoria. Prazo financeiro continua aberto; legal hold exige fundamento, escopo, responsável e revisão, sem ampliar acesso.

D09 define hipóteses de ensaio (15 s captura, 60 s stale, oito pontos/dois minutos de buffer, 30 s de tolerância futura e sinalização acima de 100 m de imprecisão). Registrar aparelho/SO, permissões, duração, rede, bateria e resultados em aparelho real/development build antes de declarar adequação. Nenhum resultado de teste existe nesta etapa.

Política ausente: `422 POLICY_UNDEFINED`; integração indisponível: `503 INTEGRATION_UNAVAILABLE`. Falta de MFA verificado: `403 MFA_REQUIRED`; dupla revisão sem responsável independente: `422 POLICY_UNDEFINED`, sem bypass. Biometria adiada, fornecedores avaliados por requisitos/custo total, sem compra autorizada. Operação real exige responsável e política, mesmo particular; particular dispensa programa subsidiado. Versão local e 03A usam testes sintéticos isolados sem habilitar dados reais.

Veículos seguem o mesmo limite: o entregador só cria/consulta/altera seus próprios vínculos e documentos. `PROPRIEDADE`, `LOCACAO` e `AUTORIZACAO` são tipos distintos; ausência de CRLV, foto ou autorização aplicável impede qualquer decisão. Documento privado fica em quarentena e só é baixado por proxy autorizado após inspeção estrutural; essa inspeção não é autenticidade documental nem varredura antimalware. Reutilização aceita somente documento cujo `proprietario_id` é a própria conta. Versão concorrente retorna `412`; substituição registra `substituido_em`, bloqueia revisão corrente e cria snapshot novo. Analista precisa de papel nominal, atribuição, MFA e não pode autoanalisar. Mesmo com evidências inspecionadas, decisão sem critérios/responsável retorna `422 POLICY_UNDEFINED`; `503 INTEGRATION_UNAVAILABLE` fica reservado a integração/capacidade técnica indisponível.

D04/D06: unidade deve aceitar procedimento e retorno; destinatário é paciente ou familiar RECEBIMENTO vigente. Antes da retirada, cancelamento registra interrupção e preserva cobertura para apurar serviço; após retirada mantém custódia e retorno com cobertura até destino comprovado. Valores, multas e responsabilidades continuam sem aprovação.

## Evidência e limites do ticket 03

Conta básica implementada; ver [configuração, controles e homologação](CONTA-EMAIL.md). Access HS256 com emissor/audiência/expiração e consulta de sessão/conta em cada requisição; refresh rotativo com revogação transacional, CSRF vinculado ao cookie e Origin exato. Papéis privilegiados continuam indisponíveis. Campos extras são rejeitados; limites de IP/rota, globais e por chave de conta precedem trabalho sensível. HMAC de busca e cifra têm chaves externas distintas; segredos não são logados.

Respostas de recuperação/reenvio são genéricas: 202 apenas aceita a solicitação; envio ocorre em fila interna sem segredo persistido. Falha de transporte fica RECONCILIAR, sem alegar entrega. Ausência de configuração é 503 para qualquer endereço. Cadastro mantém rollback integral na falha SMTP. Confirmação não comprova identidade, benefício, entregador ou veículo.

Limites técnicos: rate limit por processo, sem coordenação entre réplicas; rotação operacional de chaves, retenção validada, monitoramento/bounces e homologação do provedor ainda pendentes. Não declarar conformidade ou segurança universal com base nos testes desta fatia.
## Ticket 12 — custódia e recebimento

Retirada só é aceita pelo entregador designado ativo, com autorização vigente, protocolo/cobertura de retorno habilitados e evidência operacional mínima aprovada. A aplicação não acessa o conteúdo do exame. O destinatário vigente é reavaliado na emissão e no consumo do código; familiar perde acesso quando a autorização RECEBIMENTO é revogada.

O código é secreto, temporário, de uso único, limitado por tentativas e nunca aparece para o entregador, em logs ou na chave de idempotência. O pedido é bloqueado antes de validar hash/expiração e a confirmação concorrente só pode consumir uma linha. Eventos e ocorrências registram ator, estado anterior/novo e evidência sem encerrar custódia por si só. Retorno, reentrega e cancelamento pós-retirada ficam indisponíveis sem política operacional aprovada.
