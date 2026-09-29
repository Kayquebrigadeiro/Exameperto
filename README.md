# Exame Perto

Nome de trabalho de uma plataforma de retirada autorizada e entrega de resultados de exames, com acompanhamento do entregador e benefícios financiados por instituições.

## Estado do projeto

**Planejamento técnico. A aplicação ainda não foi implementada.** A stack foi escolhida pelo autor em 29/09/2026. Não há contas predefinidas, dados de demonstração ou integrações simuladas no produto previsto.

O caso que motivou o projeto é o deslocamento de uma familiar entre Santana de Parnaíba e Barueri para realizar exames e, depois, buscar resultados. O primeiro escopo cobre a entrega de resultados/documentos em envelope fechado, não coleta de amostras ou transporte de pacientes.

## Stack escolhida

| Parte | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security e Maven |
| Persistência | PostgreSQL, Flyway e Spring Data JPA |
| Web do paciente, familiar e administração | React, TypeScript e Vite |
| App do entregador | React Native, TypeScript e Expo |
| Comunicação | API REST; atualizações ao vivo por WebSocket/STOMP |
| Contrato e documentação da API | OpenAPI |
| Testes previstos | JUnit, Testcontainers, Vitest e testes de fluxo no navegador |
| Ambiente local previsto | Docker Compose |

Versões exatas e dependências serão fixadas na implementação. Hospedagem e serviços externos ainda não foram contratados ou escolhidos definitivamente.

## Documentação

- [Arquitetura e estrutura das pastas](docs/ARQUITETURA.md)
- [Diagramas de componentes, dados e entrega](docs/DIAGRAMAS.md)
- [Decisões técnicas e plano de implementação](docs/DECISOES.md)

O GitHub renderiza os diagramas Mermaid presentes na documentação. Cada alteração de fluxo, entidade ou responsabilidade deve atualizar o diagrama correspondente no mesmo commit do código.

## Regras centrais

- Pessoas com 60 anos ou mais e pessoas com deficiência comprovada: gratuidade do frete segundo a política proposta do programa.
- Apoio econômico independente: desconto progressivo conforme renda familiar verificada.
- Benefícios não se acumulam acima de 100% do frete.
- Parcela do paciente + parcela da instituição = frete bruto acordado com o entregador.
- Entregador recebe o frete integral, sem comissão oculta.
- Subsídio só pode ser confirmado com instituição, programa e recursos realmente habilitados.
- Cadastro não equivale a identidade verificada, CNH habilitada ou veículo aprovado.
- Documentos pessoais são privados; destinatário autorizado vê foto aprovada e identificação operacional do entregador e veículo.
- Localização é compartilhada somente durante a tarefa ativa e com seus participantes autorizados.

Essas são regras do produto, não uma declaração de que o governo já oferece ou financia esse serviço.

## Integrações ainda não habilitadas

Consultas oficiais, biometria, pagamento, repasse, e-mail e mapas dependerão de seleção do serviço, credenciais e requisitos aplicáveis. Ausência de integração deve aparecer como indisponível ou pendente; não produzir resposta fictícia, saldo fictício ou aprovação automática.

## Próximas etapas

1. Validar este desenho e fechar os contratos da API e as migrações.
2. Implementar cadastro, autenticação, autorização e análise de motorista/veículo.
3. Implementar comprovações, benefícios e financiamento institucional.
4. Implementar rota, orçamento, pedido e distribuição aos entregadores.
5. Implementar app do motorista e rastreamento real.
6. Verificar segurança, concorrência, persistência e execução em aparelhos reais.
7. Publicar o repositório revisado e realizar deploy da versão verificada.

Dados sintéticos poderão existir exclusivamente em testes automatizados isolados, sem criar contas ou registros no banco da aplicação.
