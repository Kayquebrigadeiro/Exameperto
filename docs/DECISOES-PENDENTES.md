# Proposta de decisões pendentes — tickets 01–02

29/09/2026 · **ready-for-human — nenhuma recomendação aprovada.**

Base desta rodada: `629ad36a405bb4a290165ffdd08dae6ae789c03d`, árvore inicialmente limpa e branch local/remota `docs/planejamento-tecnico` no mesmo commit. Escopo: proposta documental, validação e publicação; sem contratação, implementação, migração ou deploy. A aprovação de uma política não autoriza automaticamente essas etapas.

## Fontes e limites

Lidos os tickets [01](../.scratch/planejamento/issues/01-decisoes-operacionais.md) e [02](../.scratch/planejamento/issues/02-privacidade-integracoes.md), a [spec](../.scratch/planejamento/spec.md), o [backlog](../.scratch/planejamento/README.md) e os documentos de continuidade/escopo: [STATUS](STATUS.md), [prompt](../PROMPT-CODEX-EXAME-PERTO.md), [README](../README.md), [glossário](../CONTEXT.md), [arquitetura](ARQUITETURA.md), [decisões](DECISOES.md), [modelo](MODELO-DADOS.md), [OpenAPI](../contracts/openapi.yaml), [segurança](SEGURANCA.md), [diagramas](DIAGRAMAS.md) e [ADR-007](adr/0007-transacoes-financeiras.md). Tickets 01–02 remetem à base e ao escopo geral do planejamento, sem lista própria de links técnicos.

Reutilizamos, sem nova pergunta: transporte de envelope fechado; proposta de gratuidade aos 60+ ou por deficiência; renda independente; cobertura máxima de 100%; paciente + instituição = frete integral; receita da plataforma separada; elegibilidade não garante saldo; confirmação bancária distinta de lançamento; nenhuma cobrança extra sem aceite; autorização por finalidade/registro/instituição; revogação efetiva; GPS apenas na tarefa ativa; nenhuma integração ou comprovação fictícia. A composição pelo maior benefício e um programa por orçamento já constam como **propostas**, não decisões aprovadas, e são submetidas em D02/D05.

**Classificação:** D01–D12 são regras propostas do produto. Prazos, percentuais, valores ilustrativos e níveis de serviço abaixo não são leis, preços contratados ou capacidade comprovada de fornecedor. A aprovação humana pode ser parcial e precisa ser registrada por ID, versão, responsável e data. Até lá, permanecem os bloqueios `POLICY_UNDEFINED` e `INTEGRATION_UNAVAILABLE` do contrato.

**Referência legal consultada nesta rodada:** a LGPD exige finalidade, necessidade e segurança; prevê hipóteses distintas para dados pessoais e sensíveis, término do tratamento, exceções de conservação e direitos do titular (arts. 6, 7, 11, 15–18 e 46). Isso não define automaticamente a base aplicável ao Exame Perto nem um prazo universal de guarda. Consentimento genérico não resolve todas as finalidades; execução de frete não autoriza por si só tratamento de dado de saúde. A classificação de cada finalidade, retenção e transferência internacional depende de validação jurídica do caso. [LGPD, texto compilado](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm).

Os papéis de controlador e operador devem refletir quem decide e quem executa cada tratamento; o nome comercial do fornecedor não resolve a classificação. O guia da ANPD é orientação, não contrato nem homologação. [Guia de agentes de tratamento da ANPD](https://www.gov.br/anpd/pt-br/centrais-de-conteudo/materiais-educativos-e-publicacoes/guia-orientativo-para-definicoes-dos-agentes-de-tratamento-de-dados-pessoais-e-do-encarregado).

## Ticket 01 — comprovações e operação

### D01 — Evidências, validade e recurso

**Pergunta:** aceita revisão documental humana com evidência mínima, as validades abaixo e recurso por outro analista? Quem responderá pela política junto ao financiador?

**Recomendação e justificativa:** iniciar com AB atribuído, sem decisão clínica pelo aplicativo, biometria obrigatória ou aprovação por CPF/CID isolado. A análise humana permite começar sem atribuir a uma integração inexistente a capacidade de verificar elegibilidade.

| Dimensão | Evidência proposta | Validade proposta da decisão |
|---|---|---|
| Idade | Documento oficial com foto e nascimento, confrontado com identidade do paciente; reaproveitar verificação já válida, sem pedir nova cópia para o mesmo fim. | 12 meses no programa; renovação usa nascimento já verificado, salvo divergência/fraude. Não se presume que a idade deixe de ser atendida. |
| Deficiência | Comprovante oficial verificável que ateste a condição, ou declaração mínima de profissional habilitado aceita pelo programa, com identificação do emissor e possibilidade de conferência. Lista de emissores/documentos e critérios precisa de validação externa antes de habilitar. Sem exigir exame completo/CID como critério suficiente. | Até 12 meses, limitada ao prazo indicado na evidência quando houver. Condição permanente não exige novo laudo periódico sem motivo; renovar enquadramento no programa com evidência já validada. |
| Renda | Declaração de composição familiar e comprovantes de renda dos últimos três meses disponíveis; autônomo/sem renda por declaração e avaliação social documentada. Não exigir extrato bancário integral como padrão. | 6 meses, com comunicação de mudança relevante. Ausência de comprovante formal abre análise assistida; não aprova automaticamente nem exclui de imediato. |

Decisão deve mostrar critério/versionamento e motivo compreensível, sem expor dados clínicos ao entregador. Propor recurso em até 30 dias da ciência, com revisão por outro AB e meta interna de resposta em cinco dias úteis após documentação suficiente. Depois disso, permitir nova solicitação/correção; os 30 dias não extinguem direitos legais. Renovação não é prazo de retenção da cópia. Documentos já expurgados não devem ser exigidos novamente sem necessidade demonstrada.

**Impactos:** produto — processo acessível, pendência explicada e recurso; segurança — menos dados clínicos e conflito de interesse; custo — equipe de análise e segundo revisor; implementação — aproveitar solicitações/decisões e `recurso_de`, detalhar lista de evidências, motivos e renovação em política versionada após aprovação.

**Dependências:** financiador aprovar lista e critérios; responsável jurídico/privacidade validar tratamento de saúde/renda; confirmar legitimidade dos emissores e critérios profissionais de análise. Consulta oficial só com contrato/credencial e resultado verificável. Aprovar o método não aprova documentos específicos que ainda não foram validados.

### D02 — Faixas de renda e composição

**Pergunta:** aceita calcular renda familiar bruta mensal por pessoa, média dos três últimos meses, com desconto de 50% até meio salário mínimo por pessoa e de 25% acima de meio até um salário mínimo, aplicando o maior benefício válido?

**Recomendação e justificativa:** usar essas duas faixas como hipótese inicial para simulação financeira com o financiador; acima de um salário mínimo, sem desconto por renda. Definir família como moradores que compartilham orçamento; incluir rendimentos regulares, descrever exclusões expressamente no acordo e tratar renda variável/ausente via D01. Fixar o valor de referência do salário mínimo e sua vigência na versão da política, sem consulta implícita a valor atual. Gratuidade válida prevalece sobre renda, sem soma; a renda segue sendo avaliada separadamente. Isso torna a conta explicável e evita acumulação ambígua.

**Impactos:** produto — faixas previsíveis, mas sujeitas a revisão após avaliar exclusões; segurança — dados de terceiros da família exigem minimização e finalidade; custo — impacto depende de demanda e distribuição de renda, ainda não medidos; implementação — cálculo decimal, limites inclusivos e versão imutável, reaproveitando a composição proposta no modelo.

**Dependências:** financiador validar orçamento, composição familiar, rendimentos incluídos/excluídos e tabela final. Não é regra de CadÚnico/BPC nem benefício governamental; nenhum acesso a essas bases foi comprovado. Sem aprovação financeira, faixas não entram em programa ativo.

### D03 — Familiar autorizado e representação legal

**Pergunta:** aceita iniciar com paciente adulto que possa conceder autorização, convite confirmado pelos dois lados e concessões por até 90 dias, deixando representação legal para uma etapa validada especificamente?

**Recomendação e justificativa:** paciente verificado inicia convite privado de uso único, válido por 48 horas; familiar autentica sua própria conta e aceita; paciente confirma a pessoa e os escopos antes da ativação. Sem busca pública por CPF/e-mail; destinatário do convite vinculado à conta verificada, evitando ativação por encaminhamento do link. Escopos separados, sem seleção prévia de BENEFICIOS ou RASTREAMENTO; expiração em até 90 dias, renovação expressa e revogação a qualquer momento. Reautenticação para conceder/alterar. Apoio presencial pode ajudar o paciente a usar sua conta, sem compartilhar senha.

Não equiparar parentesco, idade ou deficiência a incapacidade. Para quem precisa de representante legal, propor um fluxo futuro distinto com prova de identidade, poderes, limites, validade e revisão humana; sem criação silenciosa de paciente pelo familiar. Menores e representação legal ficam indisponíveis no piloto proposto, com explicação e canal de orientação. É limitação de escopo proposta, não afirmação de incapacidade nem proibição legal geral.

**Impactos:** produto — menor cobertura inicial, inclusive de pessoas que mais precisam de ajuda; segurança — impede autodelegação e abuso de convites; custo — suporte e revisão legal posterior; implementação — o atual `GrantInput.familyUserId` não especifica convite/aceite. Será necessário desenhar desafios e confirmação antes de atualizar contrato/modelo. Representação legal exigirá rever o vínculo obrigatório paciente–conta, não reutilizar a concessão do paciente indevidamente.

**Dependências:** responsável jurídico validar poderes/documentos aceitos e atendimento acessível; provedor de e-mail e verificação de conta habilitados; definir responsável pelo suporte. Se representação legal for requisito do primeiro piloto, D03 deve ser alterada e esse fluxo validado antes de liberar o público correspondente.

### D04 — Retirada, destinatário e custódia

**Pergunta:** aceita operar somente com unidades que confirmem previamente a autorização de retirada e entregar apenas ao paciente ou familiar com RECEBIMENTO vigente? Quais unidades participarão?

**Recomendação e justificativa:** validar com cada unidade o documento de autorização, identificação do entregador, validade, horário e protocolo de envelope lacrado antes de ofertar o pedido. Autorização familiar não substitui autorização de retirada. Destinatário é pessoa identificada no pedido; sem entrega a porteiro/vizinho por instrução informal. Alteração de destinatário exige nova verificação e novo código. Revogação durante transporte interrompe acesso do familiar e gera ocorrência para destinação segura; não abandona o envelope.

Envelope recusado, unidade fechada, destinatário ausente ou lacre comprometido gera ocorrência; AO atribuído coordena custódia e retorno aceito pela unidade, sem abrir/fotografar o conteúdo. Encerrar somente com destino do envelope comprovado e obrigação financeira apurada. Retorno/reentrega com custo novo exige aceite e cobertura prévios; definir unidade de retorno e contato de contingência antes da retirada.

**Impactos:** produto — menos unidades disponíveis, maior previsibilidade; segurança — cadeia de custódia e sigilo; custo — integração operacional, suporte e retorno; implementação — detalhar evidência mínima de retirada/recebimento e troca de destinatário, sem introduzir transição terminal que dispense custódia.

**Dependências:** acordo operacional com cada unidade e protocolo de contingência; validação jurídica da autorização e responsabilidade pela custódia. Nenhum hospital/laboratório foi confirmado nesta rodada; modelo eletrônico próprio não prova aceitação pela unidade.

### D05 — Tarifa, cobertura e receita

**Pergunta:** aceita tarifa fixa por orçamento, um financiador/programa por pedido e receita da plataforma paga separadamente pela instituição? Quem financiará o piloto e fornecerá os custos reais para definir os valores?

**Recomendação e justificativa:** fórmula inicial `máximo(piso, base + km × tarifa_km + minutos × tarifa_minuto)`, distância/tempo previstos por rota real, parâmetros e arredondamento versionados. Piso cobre mobilização; não alterar preço porque trânsito real piorou. Custos previsíveis de estacionamento/pedágio devem integrar o orçamento antes do aceite. Validade proposta de 15 minutos; recálculo vencido exige novo aceite. Uma tarefa por entregador no piloto, como já sugerido no modelo. Nenhum valor unitário é fixado sem custos e acordo reais; a fórmula é proposta, não tabela habilitada.

Manter maior benefício válido e um programa por orçamento. Sem saldo, oferecer espera ou novo orçamento particular aceito expressamente. Cobrança e subsídio cobrem todo o frete bruto do entregador. Recomendar mensalidade institucional separada para plataforma e taxas de pagamento, sem desconto no repasse e sem cobrança adicional do paciente no piloto. Confirmar aporte por conciliação e segundo GF, como já previsto.

**Exemplos exclusivamente documentais, não tarifas/seeds:** frete de R$ 40,00 com gratuidade: paciente R$ 0,00 + instituição R$ 40,00; renda de 50%: R$ 20,00 + R$ 20,00; renda de 25%: R$ 30,00 + R$ 10,00; particular: R$ 40,00 + R$ 0,00. Em todos, entregador recebe R$ 40,00 brutos; taxas do provedor e receita da plataforma têm cobertura separada. Obrigações tributárias devem ser validadas, sem prometer valor líquido fiscal.

**Impactos:** produto — preço previsível, sem ajuste automático; segurança — rastreabilidade e segregação financeira existentes; custo — instituição assume operação/taxas e precisa avaliar sustentabilidade; implementação — aproveita tarifa/orçamento/reserva; mensalidade e cobertura de taxas precisam de desenho próprio antes de implementação, pois não são modeladas como frete.

**Dependências:** instituição identificada, acordo, recursos comprovados, parâmetros monetários reais, estudo de demanda/custo, validação contábil e provedor financeiro. Sem isso, preço/subsídio permanecem indisponíveis; não contratar serviços nesta rodada.

### D06 — Cancelamento, ocorrência e pagamento do entregador

**Pergunta:** aceita, para o piloto, cancelamento sem multa ao paciente e garantia institucional do frete integral após designação, além de retorno/reentrega previamente custeados?

**Recomendação e justificativa:** adotar garantia simples para proteger a remuneração sem transferir ao paciente uma multa ainda não validada. É uma opção deliberadamente mais cara; medir cancelamentos antes de propor cobrança proporcional. A instituição deverá contratar e lastrear a contingência inclusive em pedidos particulares; sem essa garantia, não liberar essa modalidade de operação.

| Momento/cenário | Tratamento proposto |
|---|---|
| Antes da designação, inclusive pagamento ainda pendente | Cancelar sem remuneração de entregador; liberar reserva, iniciar devolução de eventual cobrança e conciliar confirmação tardia sem ressuscitar pedido. Instituição cobre taxas não devolvidas pelo provedor. |
| Após designação e antes da retirada, por paciente/unidade/plataforma | Encerrar designação e pagar frete integral garantido pela instituição; devolver parcela do paciente. Aceite já comprometeu disponibilidade do entregador. |
| Após retirada | Ocorrência, sem cancelamento simples; preservar custódia e concluir entrega/retorno autorizado. Frete original integral; custo adicional por orçamento separado, aceite/cobertura antes de executar. Em encerramento frustrado, instituição garante o original e a devolução da parcela do paciente. |
| Desistência do entregador, fraude suspeita ou falha atribuída a ele | Ocorrência e revisão por AO sem conflito, ouvindo o entregador. Não confiscar automaticamente remuneração nem aplicar multa. Apurar serviço comprovado e valor devido segundo cláusula específica validada; essa exceção permanece bloqueada até acordo, sem presumir frete zero. |

Garantia não pode ser apenas promessa: reservar cobertura contingente necessária antes da designação, sem usar duas vezes o saldo destinado a subsídio. Separar financiador do serviço e responsabilidade por perdas na contabilidade. Recomendar iniciar repasse em até um dia útil após liquidação; mostrar previsão e estado confirmado/incerto, sem prometer prazo bancário não contratado. Liberar parcela incontroversa e tratar disputa em procedimento próprio, após desenho e validação.

**Exemplo documental:** orçamento R$ 40,00, paciente R$ 20,00 + subsídio R$ 20,00. Entrega normal: soma R$ 40,00 ao entregador. Cancelamento após designação sob a garantia proposta: paciente recebe estorno de R$ 20,00; instituição cobre R$ 40,00 totais (R$ 20,00 originalmente reservados + R$ 20,00 de contingência); entregador recebe R$ 40,00 uma vez. Taxas ficam fora dessa soma. A confirmação do estorno/repasse continua dependendo do provedor.

**Impactos:** produto — política simples e proteção do paciente/entregador; segurança — conciliação e revisão contra cancelamentos abusivos; custo — exposição máxima por pedido, taxas e retornos deve caber no orçamento do financiador; implementação — o modelo atual libera a reserva no cancelamento e só detalha liquidação integral na entrega. Será necessário desenhar transferência/consumo de cobertura e compensação atômicos, garantia adicional, apuração parcial/disputa e operações por serviço adicional. Não aplicar esta recomendação diretamente ao contrato existente.

**Dependências:** instituição aceitar e financiar garantia, cláusulas com entregadores e unidades, análise jurídica de consumo/responsabilidade/relação de trabalho, validação contábil e capacidades reais de estorno/repasse. Falta de aprovação da exceção por falha do entregador bloqueia a operação que dependa dela; aprovação geral não cria automaticamente essa cláusula.

## Ticket 02 — privacidade, acesso e fornecedores

### D07 — Responsáveis, finalidades e atendimento ao titular

**Pergunta:** quem será a pessoa jurídica responsável pela operação e quem responderá por privacidade, análise de benefícios e gestão financeira? Aceita inventário de finalidades e canal privado de solicitações antes de coletar dados reais?

**Recomendação e justificativa:** documentar por finalidade: responsável decisor, operadores, dados mínimos, destinatários, fundamento jurídico validado e prazo. Separar logística, análise de benefício e prestação de contas; financiador não recebe laudos por financiar. Propor canal autenticado e alternativa assistida com verificação proporcional de identidade; fornecer protocolo, decisão fundamentada e acompanhamento. Meta interna de triagem de dois dias úteis, sem substituir prazos legais específicos. Não usar prazo de acesso como prazo universal de exclusão.

**Impactos:** produto — direitos exercitáveis e comunicação clara; segurança — evita exclusão/exportação por pessoa alheia e coleta excessiva; custo — responsável e atendimento; implementação — ampliar o procedimento de `solicitacao_privacidade` para execução verificável, exceções e comprovação de conclusão, sem confundir RESPONDIDA com dado apagado.

**Dependências:** nomeação organizacional fora de Git quando contiver dados pessoais; jurídico validar bases por finalidade e regime aplicável ao encarregado/canal. Antes de produção, validar obrigações e prazos de atendimento e incidentes. A escolha do usuário pode indicar funções/organizações aqui; contatos pessoais e credenciais ficam em registro privado.

### D08 — Retenção, exclusão e backups

**Pergunta:** aceita os prazos operacionais propostos abaixo como ponto de partida, sujeitos à validação por categoria, com retenção excepcional justificada e exclusão também em fornecedores?

**Recomendação e justificativa:** guardar a conclusão mínima da verificação por mais tempo que a cópia usada para verificá-la; evitar manter documentos completos por conveniência. Prazos contados em dias corridos salvo indicação. São tetos operacionais propostos, não prazos legais. Coleta real só após aprovação da categoria e verificação de eventuais obrigações incompatíveis.

| Categoria/finalidade | Gatilho e teto propostos | Descarte/acesso |
|---|---|---|
| Upload abandonado; arquivo rejeitado/quarentena reprovada | 7 dias da última tentativa; 7 dias da rejeição, respectivamente. | Apagar objeto, temporários e miniaturas; permitir reenvio seguro. Manter motivo mínimo conforme auditoria. |
| Cópias de idade, deficiência e renda | 30 dias após ciência da decisão; se houver recurso no prazo, 30 dias após decisão do recurso. | Expurgar cópias; conclusão mínima e emissor/referência ficam durante validade do benefício + 90 dias. Recurso posterior usa histórico mínimo e nova evidência somente se necessária. |
| Cópias de identidade, CNH, CRLV e vínculo | Até 30 dias após decisão final da análise. | Guardar resultado, campos necessários e validade da aprovação até fim do vínculo + 90 dias; comprovação de situação atual exige revalidação própria. |
| Foto operacional | Enquanto aprovação/vínculo vigente; apagar em até 30 dias da substituição/encerramento. | Participantes só durante tarefa; referências históricas não mantêm cópia pública ou snapshot binário. |
| Convite e autorização familiar | Segredo de convite até consumo/48 h; evidência mínima da concessão até 90 dias após expiração/revogação. | Revogar acesso de imediato; apagar segredo e vínculos excedentes, ressalvados registros de pedido necessários e validados. |
| Endereço, coordenadas de origem/destino, retirada e recebimento | Até 90 dias após encerramento do pedido. | Retirar acesso operacional ao encerrar; invalidar código imediatamente e apagar hash em até 24 h; reduzir prova ao mínimo sem conteúdo do exame. |
| Posições GPS brutas | Até 24 h após encerramento da tarefa. | Compartilhamento termina imediatamente; expurgo de banco, cache, filas e app. Sem uso para publicidade/perfil. D09 limita buffer. |
| Conta/contatos/identificadores | Enquanto conta ativa; até 30 dias após encerramento validado. | Apagar/anonimizar irreversivelmente o excedente; dependências legais específicas separadas, sem manter CPF por mera conveniência de unicidade. |
| Auditoria de decisões/acessos | 180 dias do evento. | Metadados mínimos, acesso restrito e integridade; sem cópias de documento/endereço/GPS. |
| Logs técnicos | 14 dias do evento. | Sem dados pessoais de negócio/tokens/URLs assinadas; descartar também em observabilidade contratada. |
| Finanças, comprovantes, chaves de negócio e eventos externos | Prazo final pendente de contador/jurídico e janela de disputa/replay do provedor. Exigir tabela específica antes de habilitar. | Separar escrituração/evidência necessária de payload bruto; conservar deduplicação enquanto risco existir. Não adotar 5 anos por suposição nem prazo nulo como retenção infinita. |
| Backups | Rotação máxima proposta de 30 dias da cópia, sem arquivo histórico indefinido. | Cifrar, restringir acesso e aplicar registro de expurgos antes de restaurar acesso. Dado eliminado pode persistir inacessível até a rotação; informar esse limite. |

GPS deve ficar fora dos backups gerais, ou usar cópias com a mesma janela de 24 h após término; não prometer eliminação em 24 h e preservá-lo por 30 dias inadvertidamente. Meta inicial para dados duráveis: RPO 24 h e RTO 24 h, a validar com operação; GPS transitório pode não ser recuperável. O registro de expurgos deve durar até vencer a última cópia recuperável e ser minimizado, sem guardar conteúdo excluído.

Exclusão envolve banco, objetos/versionamento, miniaturas, filas/outbox saneadas, caches, dispositivo e fornecedores. Processo deve registrar solicitado, autorizado, executado e verificado, incluindo prazo residual de backup. `legal_hold` exige fundamento específico validado, responsável, escopo e revisão a cada 30 dias; impede somente expurgo necessário, não concede acesso operacional. Pedido pendente não justifica reter toda a conta. Conflitos com FKs e registros append-only serão tratados por anonimização/expurgo autorizado planejado, sem cascata geral.

**Impactos:** produto — histórico mais curto e possível necessidade de nova evidência; segurança — reduz exposição, exige exclusão verificável e bloqueios restritos; custo — menor armazenamento, maior trabalho de expurgo/restore e atendimento; implementação — classes de retenção existentes precisam de gatilhos e tratamento de referências/snapshots; adaptar estado de privacidade e jobs somente após aprovação.

**Dependências:** responsável de D07, validação jurídica/contábil por categoria, contrato de exclusão/backup do fornecedor e evidência futura de restauração/expurgo. Prazo financeiro fica explicitamente aberto; os demais são recomendações revisáveis, não parecer de suficiência probatória.

### D09 — Limites de GPS e acesso a arquivos

**Pergunta:** aceita testar captura a cada 15 segundos, posição desatualizada após 60 segundos e buffer máximo de dois minutos, com download de documentos por proxy autenticado?

**Recomendação e justificativa:** usar esses limites apenas como hipótese de medição no Android real, nunca como capacidade comprovada. Propor buffer de até oito pontos/dois minutos, rejeitar pontos mais antigos e horário futuro além de 30 segundos; sinalizar precisão pior que 100 m, sem apresentar alta precisão falsa. Parar captura/assinaturas e descartar buffer ao encerrar. Aceitar localização durante ocorrência somente se designação/custódia e finalidade de tarefa continuarem ativas, sem ampliar leitores AO/AD automaticamente. Medir bateria, perda de conexão e segundo plano antes de fixar parâmetros.

Para documentos sensíveis, preferir proxy que reautoriza cada download, com resposta `no-store`; URLs de upload com validade de cinco minutos e sem possibilidade de substituir arquivo finalizado. A revogação bloqueia novos acessos, mas não apaga cópia já recebida pelo leitor. Identificação operacional também deve obedecer ao encerramento da tarefa.

**Impactos:** produto — transparência sobre localização velha, sem promessa de rastreamento contínuo; segurança — menor janela de vazamento/replay; custo — proxy aumenta banda/carga e GPS frequente consome bateria/dados; implementação — especificar parâmetros STOMP/HTTP, descarte e transporte de arquivos; verificar compatibilidade de `photoUrl` e downloads no contrato antes de habilitar.

**Dependências:** medição em aparelho/development build, permissões do sistema e fornecedor de armazenamento. Nenhum ensaio foi executado; se metas falharem, voltar para decisão documentada, sem posição simulada.

### D10 — Bootstrap, recuperação e segregação de acessos

**Pergunta:** aceita MFA obrigatório para AD/AO/AB/GF, duas pessoas para criação/recuperação administrativa e revisão mensal dos privilégios? Quem ocupará essas funções?

**Recomendação e justificativa:** bootstrap de uma conta nominal verificada por operador identificado, mediante solicitação e conferência por segunda pessoa registrada fora do cadastro público; privilégio mínimo e MFA antes do uso. Preferir passkey/chave de segurança; definir alternativa TOTP e códigos de recuperação protegidos para continuidade, sem senha padrão. Recuperação administrativa com verificação independente de identidade, dupla conferência, revogação de sessões/fatores comprometidos e notificação; não depender apenas de acesso ao e-mail perdido.

AD atribui análises com escopo mínimo, sem ler comprovantes por ser administrador. Proibir revisão própria e conflito de interesse; manter dois GF distintos para aportes. Revisar privilégios mensalmente e revogar imediatamente ao desligar/mudar função. Se não houver segunda pessoa disponível, ação privilegiada permanece pendente; não criar bypass permanente. Para incidentes, acesso excepcional proposto de até uma hora, motivo/escopo mínimos, aprovação independente e revisão posterior, sem conceder leitura clínica global ou GPS histórico.

**Impactos:** produto — pode haver espera para recuperação/análise; segurança — menor risco de tomada de conta e abuso interno; custo — pelo menos duas pessoas habilitadas, fatores e suporte; implementação — procedimento de bootstrap e ciclo de concessão/expiração/MFA ainda não estão especificados no OpenAPI; detalhar primeiro, preservando matriz existente.

**Dependências:** responsáveis nomeados, registro privado de custódia/recuperação, biblioteca ou serviço de MFA avaliado e exercício futuro de recuperação. MFA é controle proposto do produto; não afirmar que a lei exige especificamente esta tecnologia ou periodicidade.

### D11 — Fornecedores e evidência necessária

**Pergunta:** aceita priorizar e-mail, armazenamento privado e rotas gerenciados, adiar biometria e condicionar pagamentos/consultas oficiais à homologação? Há fornecedores ou contratos já existentes e qual é o teto mensal disponível?

**Recomendação e justificativa:** selecionar por requisitos e custo total após obter volume estimado, organizações contratantes e orçamento; não escolher marca por presunção. Evitar operar serviços de e-mail/mapas por conta própria no piloto, reduzindo manutenção; preservar adaptadores previstos na arquitetura. Conferência humana documental pode atender D01 quando aprovada, mas não se apresenta como consulta oficial nem substitui requisitos profissionais obrigatórios de entregador.

| Serviço | Requisitos propostos de contratação/credencial | Evidência necessária antes de habilitar |
|---|---|---|
| E-mail de verificação/recuperação | Domínio controlado, autenticação do domínio, credencial restrita, tratamento de contatos/conteúdo e custo por envio. | Entrega real, expiração/reuso de token, falha/bounce e recuperação; sem dados clínicos em mensagem. |
| Objetos privados e varredura | Controle por objeto, cifragem, localização/suboperadores, versionamento, exclusão e backup compatíveis com D08; credenciais distintas. | Upload/quarentena, negação a terceiro, revogação, exclusão de versões e restauração. Compatibilidade S3 é interface desejada, não prova desses controles. |
| Rotas/mapas | Licença de uso/exibição/cache, cotas, cobrança e geocodificação/rotas na região atendida; chave de servidor restrita. | Distância/tempo reais, unidade, cobertura, erro e indisponibilidade; trânsito somente se contratado e validado. Servidor público de demonstração não é serviço de produção contratado. |
| Identidade/CPF/documentos/Senatran | Finalidade, autorização de acesso, produto e escopo contratados, credenciamento quando exigido; nenhuma senha gov.br do usuário. | Campos, origem, atualização, autenticação e limites de cada consulta. QR autêntico não comprova sozinho habilitação atual, renda ou deficiência. |
| Cobrança, estorno e repasse | Conta habilitada, beneficiários verificados, modalidade adequada ao modelo de negócio, taxas/prazos, disputas, idempotência/consulta por referência e autenticação de eventos. | Ciclo ponta a ponta, duplicação, timeout, estorno, conciliação, assinatura e divergência de valor/destinatário. Sandbox verifica integração; não comprova liquidação em produção. |
| Push, se necessário | Permissão do dispositivo, tratamento de token e custos. Recomendar adiar se e-mail/status no app bastarem. | Entrega e revogação reais; payload sem dados sensíveis, localização ou código de recebimento. |

**Estado comprovado nesta rodada:** o repositório contém interfaces e referências a serviços, mas nenhum contrato, credencial, homologação ou teste de capacidade foi apresentado/verificado. Não foram pesquisadas marcas/preços nem acessadas contas privadas. Menções a Datavalid, VIO, Senatran, S3 ou OSRM na arquitetura não significam contratação ou acesso disponível.

**Impactos:** produto — disponibilidade depende de habilitação por serviço; segurança — menos dados enviados e segredos segregados; custo — cotar mensalidade, uso, banda, consultas, taxas, suporte e saída; implementação — adaptadores, health/status e validação de webhooks. Qualquer header, assinatura, estado ou DTO específico exige atualizar OpenAPI/modelo/segurança/diagramas juntos antes de implementar.

**Dependências:** orçamento/volume, responsável pela contratação, termos de tratamento/suboperadores/transferência internacional, credenciais por ambiente em cofre e homologação observável. Contratos/segredos não entram em Git. Aprovar esta estratégia não autoriza compra nem declara fornecedor capaz.

### D12 — Recorte do primeiro piloto

**Pergunta:** o primeiro piloto pode priorizar entregas subsidiadas de um único programa e unidades confirmadas, ou pedidos particulares precisam estar disponíveis desde o início?

**Recomendação e justificativa:** começar com um programa financiado e suas unidades confirmadas, incluindo pacientes elegíveis conforme D01–D02; deixar pedidos inteiramente particulares para a expansão. Isso concentra validação operacional, suporte e custeio da garantia em um acordo verificável. Paciente sem cobertura recebe indisponibilidade/espera; a alternativa particular prevista na arquitetura só é oferecida quando efetivamente habilitada. Não elimina a dimensão independente de renda nem muda quem é elegível.

Regras já estabelecidas, sem nova pergunta: registrar decisão por ID, atualizar artefatos técnicos afetados e obter autorização posterior de implementação/deploy; capacidade sem política ou integração continua bloqueada, sem simulação.

**Impactos:** produto — piloto pode começar com cobertura menor, somente após nova autorização; segurança — bloqueios verificáveis e rastreabilidade; custo — permite medir despesa e cancelamentos de um programa, mas concentra dependência no financiador; implementação — backlog continua `ready-for-human`, sem novos fluxos nesta rodada.

**Dependências:** aprovação do autor, responsáveis institucionais, validações externas indicadas e autorização futura de implementação; deploy permanece separado. Aprovação do usuário não substitui contrato/credencial/validação externa.

## Compatibilidade e próxima revisão técnica

Esta proposta não modifica estados, DTOs, tabelas nem diagramas vigentes. Eles continuam representando a proposta técnica anterior. Os pontos abaixo são lacunas/alterações futuras, **não capacidades já existentes**:

| Decisões | Ajustes necessários após decisão humana | Tickets diretamente afetados |
|---|---|---|
| D01–D02 | Políticas, evidências aceitas, validade/renovação e recurso; conferir decisões e snapshots. | 07, 10 |
| D03–D04 | Convite/aceite, destinatário, representação legal se incluída, custódia e retorno; revisar segurança, dados/API e diagramas. | 04, 09, 12 |
| D05–D06 | Tarifas reais, receita separada, garantia contingente, liberação versus liquidação no cancelamento, disputa e serviço adicional; revisar ADR-007, dados/API, segurança e diagramas. | 08–12, 14 |
| D07–D09 | Tabela aprovada de retenção, remoção de referências sem quebra contábil, exclusão/backup, proxy e limiares medidos; atualizar dados/API, segurança e diagramas afetados. | 03, 05–07, 13, 15 |
| D10–D11 | Procedimentos privilegiados, MFA, contratos de adaptadores, evidências externas e responsáveis; conferir matriz e protocolo de cada serviço. | 03, 05–06, 08–10, 13–15 |
| D12 | Delimitar público/programa/unidades e disponibilidade de modalidade particular; reavaliar backlog sem marcar tickets concluídos. | 01–15 |

## Como responder às decisões

Pode aprovar, rejeitar ou ajustar cada ID, sem repetir regras já estabelecidas. Perguntas que exigem informação concreta, além do aceite das recomendações:

- **D01/D02:** quem valida critérios junto ao programa e se aceita as faixas/validades propostas?
- **D03/D04:** representação legal precisa entrar no primeiro piloto? Quais unidades aceitarão retirada e retorno?
- **D05/D06:** qual instituição financiará fretes, contingências e custos da plataforma? Aceita garantir frete após designação e cancelamento sem multa ao paciente? Valores de tarifa e cláusula de falha do entregador precisam de validação própria.
- **D07/D08:** qual organização responde pelo tratamento, quem conduz privacidade e quem validará os prazos financeiros? Aceita os demais tetos operacionais como proposta para validação?
- **D09/D10:** aceita os limites experimentais de GPS/proxy e MFA/dupla conferência? Quem terá funções privilegiadas, sem publicar dados pessoais aqui?
- **D11/D12:** existem contratos/provedores aproveitáveis e teto mensal? O piloto pode começar com um programa subsidiado, ou precisa incluir pedidos particulares?

Nenhuma resposta foi presumida. Regras sem aprovação, contratos/credenciais não apresentados, prazo financeiro e valores reais de tarifa permanecem abertos. Próxima ação: decisão humana por ID e validações externas; depois, nova rodada documental coerente antes de qualquer implementação autorizada.
