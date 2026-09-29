# Continuação do Exame Perto no Codex

Você está trabalhando no Exame Perto, uma plataforma de retirada autorizada e entrega de resultados de exames em envelope fechado. Leia README.md e docs/ARQUITETURA.md, docs/DIAGRAMAS.md e docs/DECISOES.md. Confira o estado real do workspace antes de alterar arquivos: a documentação inicial foi entregue sem aplicação implementada, mas pode haver trabalho posterior.

## Etapa autorizada agora

Concluir o planejamento técnico: modelo físico do PostgreSQL, contrato OpenAPI, matriz de permissões e backlog verificável. Implementação da aplicação e deploy pertencem às próximas etapas. Faça as alterações documentais necessárias e commit/push conforme as regras abaixo.

Entregáveis:
- docs/MODELO-DADOS.md: campos, tipos, relacionamentos, índices, restrições e estados. Inclua valores monetários exatos, unicidade de designação ativa, idempotência financeira e proteção contra concorrência.
- contracts/openapi.yaml: operações, esquemas, autenticação, respostas, erros, paginação e requisitos de idempotência pertinentes. Valide sintaxe e referências com ferramenta disponível; registre limitações.
- docs/SEGURANCA.md: permissões por ação e registro, riscos, controles, retenção e pendências.
- CONTEXT.md: somente glossário do domínio.
- Backlog local com critérios de aceite e dependências; diagramas e links atualizados.

## Decisões estabelecidas

Stack: Java 21, Spring Boot, Spring Security, Maven; PostgreSQL, Flyway, JPA; React/TypeScript/Vite; app do entregador React Native/TypeScript/Expo; REST e WebSocket/STOMP. Monólito modular em um monorepo. Versões exatas e fornecedores externos ainda serão selecionados.

Escopo inicial: resultados/documentos, sem transporte de pacientes ou amostras. Paciente, familiar autorizado, entregador aprovado, analista e administrador têm permissões distintas. Cadastro público não concede papel privilegiado.

Gratuidade proposta para idosos com 60 anos ou mais e pessoas com deficiência comprovada; desconto por renda é uma dimensão independente. Isso é política proposta do produto, não benefício governamental já contratado. Critérios operacionais de comprovação, tabelas de renda e financiamento ainda precisam de definição verificável. CPF ou cadastro, isoladamente, não comprova elegibilidade.

Paciente + instituição cobrem o frete integral acordado com o entregador; benefícios não excedem 100%. Aprovação de benefício e disponibilidade financeira são verificações separadas. Reservas, liquidações e repasses exigem controle transacional e idempotência. Registro contábil não prova transferência bancária. Rota, tempo e tarifa compõem orçamento rastreável, sujeito à política configurada e aceite.

Cadastros e integrações devem ser reais. Ausência de contrato, credencial ou provedor produz estado indisponível/pendente. Dados sintéticos apenas em testes isolados, nunca como contas, saldo, documentos ou GPS na aplicação. Fotos e documentos são privados; identificação operacional do entregador é acessível aos participantes autorizados. GPS somente durante tarefa ativa, com horário e indicação de posição desatualizada.

## Skills: origem, configuração e uso

Fonte de referência: https://github.com/Kayquebrigadeiro/skillsproject.git. Este é o repositório DAS SKILLS, não o destino do código do Exame Perto. Leia as instruções efetivamente instaladas; o catálogo não substitui SKILL.md. Preserve alterações existentes. Caso estejam ausentes, consulte a instalação documentada no fork e informe o que falta, sem inventar comandos ou alegar instalação concluída.

A configuração de trabalho adotada por esta instrução é:
- Tracker: Markdown local no próprio projeto, conforme o template local da skill. Não criar GitHub Issues nesta etapa.
- Instruções para o agente: AGENTS.md; preserve e integre instruções preexistentes. Esta preferência explícita prevalece sobre a escolha padrão da skill.
- Domínio: um CONTEXT.md na raiz e docs/adr/ para decisões que justifiquem ADR. Monorepo não exige múltiplos glossários.
- Labels, se triage instalada: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. Não criar labels remotas.
- Documentação em docs/, contrato em contracts/. Reaproveite decisões existentes; mantenha um índice para não duplicar regras entre DECISOES.md e ADRs.

Você está instruído a usar setup-matt-pocock-skills para configurar o projeto uma vez, aproveitando estas respostas; to-spec para sintetizar a etapa atual e to-tickets para propor o backlog. Leia templates e referências pertinentes. Apresente os entregáveis/backlog concretos para revisão ao final; não inicie os tickets de implementação ainda.

Use domain-modeling quando termos ou relações mudarem e writing-for-agents ao escrever instruções do agente. Entrevista/grill somente para decisões materiais ainda abertas; não reinicie perguntas já respondidas. Seja explícito sobre recomendações e pendências, sem inventar consentimento, contratos ou regras de negócio.

Para futuras etapas autorizadas de implementação: implement + tdd nos limites acordados; diagnosing-bugs para falhas difíceis. Adoto como limites de teste: operações REST com autorização por registro; interfaces públicas de elegibilidade e precificação; concorrência de aceite/reserva; publicação/assinatura autorizada de localização. Use PostgreSQL real isolado nas verificações que dependam de suas transações/restrições. Novos limites materiais devem ser apresentados para decisão. Teste comportamento observável e casos independentes, não detalhes privados ou cópias da implementação. Mudança apenas documental recebe validação documental, sem testes artificiais.

A skill code-review pode usar seus dois agentes de revisão (Standards e Spec); essa delegação está autorizada especificamente quando a skill for usada. Forneça somente o diff, a especificação e as normas necessárias. Outras delegações precisam de justificativa, e não devem duplicar o trabalho.

## Segurança e qualidade

Projete autorização por papel e por registro no backend, isolamento entre instituições e concessão/revogação de acesso de familiares. Planeje senhas com hash adequado, sessões revogáveis, recuperação segura, CSRF quando aplicável e limitação de tentativas. Use bibliotecas consolidadas.

Uploads privados: autorização, tamanho/tipo verificados, nomes gerados, URLs temporárias e retenção definida. Mantenha dados pessoais, segredos e tokens fora de logs, erros públicos e Git. Colete o mínimo necessário; finalidade, retenção, exclusão e obrigações legais permanecem documentadas e pendentes de validação quando preciso. Não invente prazos legais.

Mapeie ataques de acesso indevido, promoção de papel, aceite concorrente, duplicação financeira, assinatura WebSocket indevida e falsificação de eventos externos. Integrações exigem autenticação e validação dos eventos. Verificações executadas e impedidas devem ser distinguidas. Segurança e desempenho exigem evidência; não declare aprovação universal.

## Economia de uso

Mantenha AGENTS.md curto: regras essenciais e ponteiros com condições claras para ler documentos. Carregue apenas skills e referências necessárias à tarefa. Preserve as instruções de revisão/teste em suas fontes, sem copiá-las integralmente para cada ticket.

Use rg e leituras direcionadas. Reutilize o contexto já obtido, ampliando a investigação quando dependências ou evidências exigirem. Evite dumps, releituras e reescritas sem motivo. Faça uma mudança coerente por vez, com critério de conclusão. Execute verificações focadas durante o trabalho e uma verificação abrangente pertinente no fechamento; repita quando alterações/falhas justificarem.

Reduza repetição nas respostas, não a cobertura dos requisitos. Registre pendências e caminhos dos artefatos para continuidade. Ao encerrar uma etapa, atualize docs/STATUS.md com progresso, bloqueios, verificações e próxima ação, sem copiar specs/ADRs. Se usar handoff, respeite seu arquivo temporário e referencie artefatos permanentes.

Configuração feita pelo usuário na extensão: Sol/Medium/Standard para trabalho principal, Luna para ajustes delimitados e modelo mais potente pontualmente. Não presuma que o prompt altera o seletor, o esforço ou a velocidade. Não estime economia percentual sem medição.

## Git: autorização e revisão

Confira remote e branch antes de qualquer push. Se o destino for skillsproject, não envie o Exame Perto para lá. Se o remoto do aplicativo estiver ausente/ambíguo, peça sua URL; continue o trabalho local independente dessa resposta. Preserve mudanças preexistentes e exclua segredos/dados pessoais dos commits.

Capture o SHA de HEAD antes da etapa, se houver, e associe a spec/backlog. Faça commits pequenos e coerentes com mensagens claras e documentação correspondente. Para respeitar code-review, que compara commits contra HEAD: valide, crie commit LOCAL, revise contra o SHA capturado, corrija achados em novos commits e só então faça push. Inclua commits de correção no conjunto revisado. Em repositório sem histórico, registre a necessidade de uma base inicial para revisar o primeiro conjunto. Verifique também mudanças não commitadas antes de concluir.

Está autorizado commit e push de cada parte documental concluída para o remoto confirmado do aplicativo, sem force push, sem apagar alterações existentes e sem deploy. Use branch que não dispare deploy automático. Se envio ou validação ficar bloqueado, informe o motivo e o trabalho local preservado.

Resposta ao finalizar: alterações, verificações realmente executadas, bloqueios, hash/branch e resultado do push. Identifique perguntas materiais pendentes em um bloco curto. Não prometa tarefas concluídas sem evidência.
