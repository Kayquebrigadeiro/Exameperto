# 15: Atender titular e verificar operação antes de deploy

**What to build:** Titular acompanha protocolo de privacidade e operadores demonstram retenção/restauração e jornada integrada antes de publicação da aplicação.

**Blocked by:** 02 — Definir privacidade, acesso privilegiado e provedores, 12 — Executar retirada, ocorrência e entrega comprovada, 13 — Compartilhar localização real durante tarefa, 14 — Confirmar repasse e conciliar resultado incerto

**Status:** implementação independente parcial — não pronto para produção

- [x] Banco e API permitem protocolo próprio de acesso/correção/exclusão, acompanhamento e resposta auditada; familiar sem escopo não lê a solicitação. Resposta e exclusão executada/verificada são estados distintos.
- [x] Executar em PostgreSQL/armazenamento isolados expurgo de documentos/GPS/fila/cache conforme política sintética, com retry, concorrência, verificação separada e tombstone. A infraestrutura de backups/fornecedores e RPO/RTO medidos continua pendente; o expurgo permanece desligado por padrão e finanças não são apagadas.
- [ ] Verificar jornada ponta a ponta externa, isolamento institucional completo, infraestrutura/logs e build mobile real com limites acordados. A fatia local cobre conta/familiar sem escopo, revogação durante processamento, segredos/configuração e regressão registrada no relatório.
- [ ] Deploy continua etapa separada: apresentar evidências e pendências para autorização, sem transformar revisão documental em aprovação de produção.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Implementação independente — base 880b457 — 08/10/2026

V15 adiciona `solicitacao_privacidade`, `politica_retencao`, `execucao_expurgo`, `expurgo_alvo`, `tombstone_expurgo` e `retencao_excecao`. Dados de solicitação/resposta são cifrados; ações operacionais exigem papel nominal e MFA verificado em cada etapa. Política sem categoria/finalidade/gatilho, responsável, base, prazo e verificação de descarte retorna `POLICY_UNDEFINED`; configuração padrão mantém `privacy.purge-enabled=false`. Resposta, execução (`EXPURGADA`) e verificação (`VERIFICADA`) são separadas. O executor é idempotente sob lock, permite retry de falha, preserva evidências/chaves financeiras e registra tombstone sem conteúdo.

Evidência local: `PrivacyFlowTest` passou em HTTP/PostgreSQL 17.6/objetos temporários com três cenários, incluindo outra conta, familiar com concessão `PEDIDOS` sem escopo de privacidade, respostas de acesso/correção, sessão e papel revogados, política ausente/futura/desativada, responsável inativo, exceção de conservação, concorrência, idempotência, falha recuperável, ausência de documento/GPS/fila/cache controlados e preservação financeira. A suíte comum teve 49 aprovados e 8 opt-ins ignorados; os 8 opt-ins passaram separadamente. Banco novo V1–V15 e upgrade V5→V15, web build/Playwright, mobile typecheck/export, contrato e documentação foram validados. Ver [relatório de prontidão](../../../docs/PRONTIDAO-TICKET-15.md). Não há comprovação de fornecedores, dispositivos, temporários não vinculados, backups externos/restore, RPO/RTO, anonimização integral da conta ou execução mobile real; não houve exclusão de dados reais, dinheiro ou deploy.

## Ajuste D01–D12 — 30/09/2026

D07/D08/D12: primeiro marco é versão funcional local, sem piloto operacional presumido. Validar inventário, expurgo e backups com política de teste isolada; prazos propostos não viram retenção aprovada. Prazo financeiro e responsáveis continuam pendentes; evidência local não habilita operação particular/subsidiada ou deploy.

- [ ] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).
