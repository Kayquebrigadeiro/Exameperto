# Execução dos prompts 1–9 — 13/10/2026

Os arquivos locais `sprints/6.md` a `sprints/9.md` estão sobrescritos com uma cópia da sprint 5 e não são evidência dos prompts originais. Os prompts originais têm escopos distintos: 5 entregador/veículos, 6 operação/análises/privacidade, 7 entrega/custódia/acompanhamento, 8 financeiro/isolamento institucional e 9 aceitação integrada. O fechamento anterior que os tratou como uma única entrega foi corrigido; ver [REVISAO-SPRINTS-5-9](REVISAO-SPRINTS-5-9.md).

## Diagnóstico do ambiente normal

- `POST /api/v1/auth/csrf` retornou `403 FORBIDDEN` sem cookie de renovação. A origem canônica, sozinha, não basta: o endpoint deriva o token CSRF da sessão HttpOnly. O controle foi preservado.
- `POST /api/v1/auth/register` com endereço deliberadamente fora da allowlist retornou `503 INTEGRATION_UNAVAILABLE`; o log registrou apenas correlação e motivo sanitizado. Nenhuma mensagem foi enviada e a transação não criou conta.
- `/favicon.ico` passou de ausência observada para `200`; a página declara também o favicon SVG.
- `verify-local-environment.sh` confirmou Vite → backend → PostgreSQL, Flyway V18, gate `NORMAL` e estrutura privada sem imprimir valores.

## Entrega navegável

A web possui URLs públicas de acesso, cadastro, confirmação e recuperação; URLs autenticadas em `/app/...`; voltar/avançar, reload e 404. A área autenticada continua projetada pelas capacidades do backend e ganhou seletor explícito quando há mais de uma área de trabalho.

Paciente/familiar agora escolhe o paciente representado a partir das concessões vigentes e vê escopos/contexto. Benefícios e pedidos usam seleção de paciente; pedidos selecionam o destinatário disponível na sessão. Política e unidade ainda exigem referência interna porque não existe endpoint de catálogo: a interface marca isso como lacuna técnica e não presume parceiro.

Cadastro do entregador, documentos, veículos, substituições, ofertas, tarefas e histórico permanecem conectados ao backend. Uploads do entregador/veículo exibem progresso real; aprovação profissional e veículo vigente continuam necessários para ofertas.

## Evidência e limites

`BrowserFlowTest` percorreu Chromium → Vite → API → PostgreSQL 17.6 descartável sem interceptar API; cadastro, confirmação, login, reload, perfil, família, benefícios, pedidos, entregador, privacidade, recuperação, responsividade e Axe passaram. A primeira execução revelou uma corrida de configuração do mock de e-mail com o worker; o stub foi ordenado antes de habilitar o gateway e a repetição passou.

Os catálogos autorizados de política/unidade, homologação com participantes reais e teste Android permanecem no [backlog](../.scratch/sprints-1-9/README.md). Não houve deploy, contratação, aprovação, dinheiro ou localização simulada.
