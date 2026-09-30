# Decisões D01–D12 e pendências de habilitação

30/09/2026 · Direções técnicas adotadas pelo autor nesta rodada; políticas operacionais parcialmente pendentes. Versão documental 2, base `7a8077fa6a7b80e8ba642d3fe2838bd299859ba3`. Substitui as recomendações da versão anterior; não autoriza implementação, compra ou deploy.

## Classificação e alcance

- **Decisão de arquitetura:** direção autorizada para o planejamento; implementação exige autorização posterior.
- **Política operacional pendente:** documentos, critérios, valores, prazos ou responsáveis ainda precisam de validação verificável; operação dependente retorna `422 POLICY_UNDEFINED`.
- **Integração indisponível:** ausência de contrato, credencial ou capacidade homologada; operação dependente retorna `503 INTEGRATION_UNAVAILABLE`.

Cadastro e segurança não dependem de programa subsidiado contratado. Uma política aprovada não habilita automaticamente integração ou operação. Não existem unidades, financiador, orçamento ou contratos informados. Responsáveis reais não serão inventados; identificações e evidências privadas ficam fora do Git.

### D01 — Revisão humana e evidência mínima

**Adotado:** revisão humana administrativa, evidência mínima, reaproveitamento de verificações válidas e recurso por outro analista atribuído. Registrar política, origem da verificação, validade e motivo, sem duplicar cópia desnecessária. Não diagnosticar, decidir condição clínica ou aprovar por CPF/CID isolado. Sem outro analista real, recurso aguarda atribuição independente.

**Pendente:** lista de documentos aceitos, critérios profissionais, responsáveis e validades por dimensão. Os 12 meses para idade/deficiência, seis meses para renda, janela de recurso de 30 dias e meta de cinco dias úteis da versão anterior são apenas propostas para validação, sem ativação. Nenhuma verificação oficial foi habilitada.

### D02 — Benefícios configuráveis

**Adotado:** políticas versionadas e configuráveis, com vigência e snapshots rastreáveis; dimensões idade/deficiência/renda independentes, cobertura até 100%.

**Proposta financeira:** 50% até meio salário mínimo por pessoa e 25% acima de meio até um salário mínimo; média de três meses e aplicação do maior benefício continuam propostas. Composição familiar, rendimentos, referência monetária e combinação final precisam de validação. Não criar políticas ativas ou seeds com essas faixas. Benefício real exige financiador, política validada e recursos comprovados; elegibilidade não garante saldo.

### D03 — Autorização expressa do adulto

**Adotado:** paciente adulto autoriza familiar por convite privado vinculado ao destinatário, aceite autenticado, confirmação expressa do paciente e escopos explícitos. Reautenticar concessão; expiração e revogação efetivas. Nenhum escopo sensível pré-selecionado. Convite aceito ainda não concede acesso.

**Cobertura limitada:** menores e pessoas que necessitem de representação legal não são atendidos por este primeiro fluxo. Parentesco, idade ou deficiência não significam incapacidade. Representação legal é etapa separada, com poderes e evidências próprios; não criar paciente silenciosamente pelo familiar. Expiração de convite em 48 horas e concessão em 90 dias continuam propostas configuráveis, não limites aprovados. Orientação/suporte real depende de responsável ainda não identificado.

### D04 — Unidade, destinatário e custódia

**Adotado:** retirar somente em unidade que aceite o procedimento, mediante autorização específica; entregar ao paciente ou familiar identificado com RECEBIMENTO vigente. Troca de destinatário exige nova validação e invalidação do código. Protocolo versionado de custódia e retorno, unidade de retorno e cobertura devem existir antes da retirada.

**Bloqueio real:** nenhuma unidade está confirmada. Envelope fechado; sem abrir/fotografar conteúdo, entrega informal a porteiro ou abandono. Ausência, recusa, revogação ou lacre comprometido gera ocorrência com destino seguro comprovado. Após retirada, manter custódia até entrega/retorno; encerramento administrativo não elimina essa obrigação.

### D05 — Orçamento rastreável

**Adotado:** rota real, parâmetros versionados, validade e aceite explícito; separar frete do entregador, subsídio e receita da plataforma. Trânsito só se disponível e identificado. Particular não exige programa subsidiado; exige suas próprias dependências reais.

**Pendente:** valores, financiador, custos, tributos/taxas e modelo comercial. Fórmula `máximo(piso, base + km × tarifa_km + minutos × tarifa_minuto)`, validade de 15 minutos e mensalidade institucional são propostas, não decisões comerciais. Um programa por orçamento é o limite técnico inicial; composição de benefícios exige política validada. Sem rota/financeiro habilitados, integração indisponível; sem tarifa validada, política indefinida.

### D06 — Cancelamento e serviço comprovado

**Ajuste adotado:** aceite/designação não garante automaticamente frete integral. Substituída a antiga proposta de garantia institucional integral. Propor apuração de remuneração conforme deslocamento e serviço comprovados, por política versionada aceita pelas partes.

| Momento | Direção técnica | Pendência operacional |
|---|---|---|
| Antes da designação | Cancelar e reconciliar cobrança/reserva; não gerar remuneração de entregador sem serviço. | Estornos, taxas e responsáveis validados. |
| Após designação, antes da retirada | Registrar evidências mínimas de deslocamento/serviço e apuração; não pagar integral ou zerar automaticamente. | Critérios de prova, cálculo, cobertura, revisão/disputa e responsabilidades. |
| Depois da retirada | Abrir ocorrência; preservar custódia e cobertura de retorno. Encerrar apenas com destino comprovado e remuneração apurada. | Protocolo aceito, custeio de retorno/serviço adicional e cláusulas validadas. |

Não fixar valores, multas ou responsabilidades. GPS isolado não prova serviço. Liberar somente cobertura excedente após apuração; não liberar reserva inteira e depois descobrir obrigação sem lastro. Valores devidos, estornos e repasses seguem idempotência e conciliação. Retorno com custo novo exige orçamento/aceite/cobertura; contingência precisa estar prevista antes da retirada. Enquanto essas condições faltarem, bloquear a operação correspondente; não iniciar custódia sem solução de retorno.

### D07 — Finalidades e privacidade

**Adotado:** inventário por finalidade e procedimento de solicitações, incidentes e exclusão. Registrar categorias, necessidade, leitores, compartilhamentos, base a validar, retenção e responsável real; protocolo não significa exclusão concluída.

**Pendente:** organização responsável, controlador/operadores, encarregado quando aplicável e equipe operacional. Não presumir pessoa jurídica, nomeação ou base jurídica. Validar responsabilidades antes de tratar dados reais; não declarar conformidade jurídica por este planejamento.

### D08 — Retenção configurável

**Adotado:** política versionada/configurável, expurgo verificável e tratamento de backups. Todos os prazos abaixo são propostas para validação, não retenção definitivamente aprovada. Prazo financeiro permanece aberto; configuração ausente bloqueia categoria real, sem retenção infinita.

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

### D09 — GPS experimental e documentos reautorizados

**Adotado:** testar em aparelho real/development build: captura a cada 15 segundos, stale após 60 segundos, buffer de oito pontos/dois minutos, tolerância futura de 30 segundos e sinalização de precisão pior que 100 m. São hipóteses configuráveis, sem adequação comprovada de bateria, precisão ou segundo plano. Registrar aparelho, sistema, permissões, duração, bateria, rede e resultados antes de declarar adequação; nenhum ensaio executado.

Download por proxy autenticado reautoriza cada acesso e usa `no-store`, inclusive foto operacional; revogação impede novos acessos, sem apagar cópia já recebida. Upload temporário não substitui objeto finalizado; TTL de cinco minutos segue proposta. Durante ocorrência, GPS só se finalidade e designação/custódia ativas, sem ampliar leitores.

### D10 — MFA e segregação

**Adotado:** MFA para AD/AO/AB/GF, contas nominais e segregação de funções. Bootstrap e recuperação privilegiada exigem conferência independente; aporte exige outro GF, recurso outro analista. Se faltarem pessoas reais, manter essas ações bloqueadas. Sem segunda pessoa fictícia, conta privilegiada padrão ou bypass permanente.

**Pendente:** responsáveis, escolha/gestão de fatores, procedimento verificável de bootstrap/recuperação e protocolo específico de MFA. Revisão mensal e acesso excepcional por até uma hora permanecem propostas, sem ativação. Login básico não habilita ação privilegiada sem MFA verificado pelo servidor.

### D11 — Seleção de fornecedores

**Adotado:** seleção por requisitos e custo total; biometria adiada. Nenhum contrato ou orçamento informado; nenhuma compra autorizada. Adaptador não prova integração disponível. Conferência humana não se apresenta como consulta oficial nem substitui critérios profissionais.

| Serviço | Requisitos propostos de contratação/credencial | Evidência necessária antes de habilitar |
|---|---|---|
| E-mail de verificação/recuperação | Domínio controlado, autenticação do domínio, credencial restrita, tratamento de contatos/conteúdo e custo por envio. | Entrega real, expiração/reuso de token, falha/bounce e recuperação; sem dados clínicos em mensagem. |
| Objetos privados e varredura | Controle por objeto, cifragem, localização/suboperadores, versionamento, exclusão e backup compatíveis com D08; credenciais distintas. | Upload/quarentena, negação a terceiro, revogação, exclusão de versões e restauração. Compatibilidade S3 é interface desejada, não prova desses controles. |
| Rotas/mapas | Licença de uso/exibição/cache, cotas, cobrança e geocodificação/rotas na região atendida; chave de servidor restrita. | Distância/tempo reais, unidade, cobertura, erro e indisponibilidade; trânsito somente se contratado e validado. Servidor público de demonstração não é serviço de produção contratado. |
| Identidade/CPF/documentos/Senatran | Finalidade, autorização de acesso, produto e escopo contratados, credenciamento quando exigido; nenhuma senha gov.br do usuário. | Campos, origem, atualização, autenticação e limites de cada consulta. QR autêntico não comprova sozinho habilitação atual, renda ou deficiência. |
| Cobrança, estorno e repasse | Conta habilitada, beneficiários verificados, modalidade adequada ao modelo de negócio, taxas/prazos, disputas, idempotência/consulta por referência e autenticação de eventos. | Ciclo ponta a ponta, duplicação, timeout, estorno, conciliação, assinatura e divergência de valor/destinatário. Sandbox verifica integração; não comprova liquidação em produção. |
| Push, se necessário | Permissão do dispositivo, tratamento de token e custos. Recomendar adiar se e-mail/status no app bastarem. | Entrega e revogação reais; payload sem dados sensíveis, localização ou código de recebimento. |

### D12 — Primeiro marco local

**Adotado:** primeiro marco é versão funcional validada localmente, sem piloto operacional presumido. Cadastro e segurança independem de programa subsidiado contratado. Particular e subsidiado só operam quando suas próprias dependências reais estiverem habilitadas. Testes sintéticos exclusivamente isolados; aplicação sem seeds, contas privilegiadas ou sucessos fictícios.

A menor etapa proposta é [03A — cadastro básico e bloqueio verificável](../.scratch/planejamento/issues/03a-cadastro-local.md), com critérios de aceite para autorização. Ela contribui para o marco local; não equivale à versão completa ou operação pronta. Implementação e deploy continuam posteriores.

## Rastreabilidade

| IDs | Artefatos afetados | Tickets |
|---|---|---|
| D01–D02 | Modelo, contrato de benefício, segurança | 01, 07, 10 |
| D03–D04 | Convite/concessão, unidade/protocolo, destinatário e custódia; diagramas | 01, 04, 09, 11, 12 |
| D05–D06 | Orçamento, apuração, transações e ADR-007; diagramas | 01, 08–12, 14 |
| D07–D09 | Inventário, retenção, proxy documental, GPS | 02, 03, 05–07, 13, 15 |
| D10–D11 | Guardas privilegiadas e habilitação externa | 02, 03, 05–06, 08–15 |
| D12 | Spec, backlog, arquitetura e primeiro recorte local | 01–15 e 03A |

Próxima decisão do autor: autorizar a implementação da fatia 03A. Pendências externas permanecem no escopo correspondente dos tickets 01–02; não reabrir D01–D12 como se ainda não houvesse resposta.
