# 10: Paciente aceita orçamento e cobertura é confirmada

**What to build:** Paciente escolhe particular ou subsídio, aceita as parcelas e acompanha pendência/insuficiência até a entrega estar financiada.

**Blocked by:** 09 — Orçamento particular; 07 e 08 somente para a extensão subsidiada. Habilitação externa conforme 01–02, no escopo da modalidade.

**Status:** implementação técnica parcial; dependências externas bloqueadas

- [x] Banco, API e web preservam paciente + instituição = frete integral; particular não depende de instituição e subsidiado usa decisão vigente da mesma instituição/programa.
- [x] Aceite duplicado não duplica reserva/cobrança; duas reservas sobre último saldo não causam saldo negativo em PostgreSQL real.
- [x] Sem recurso disponível, a API retorna insuficiência e preserva escolha posterior; não cobra diferença automaticamente.
- [ ] Cobrança externa homologada e ciclo de cancelamento: outbox, contrato do adaptador, evento autenticado/deduplicado e resultado INCERTA foram implementados/testados com adaptador controlado, mas não há provedor contratado nem política de cancelamento/estorno homologada.

Proposta de backlog; não iniciado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D02/D05/D06/D12: caminho particular depende de 09 e de pagamento/políticas reais pertinentes, sem 07/08. Somente extensão subsidiada depende de 07/08. Exibir política de cancelamento versionada no aceite; não prometer remuneração integral automaticamente. Ambos bloqueiam operação enquanto faltar dependência real.

- [x] Verificar interfaces públicas, concorrência e rollback com PostgreSQL real isolado; navegador → API → banco coberto para o aceite particular pendente.

## Implementação V10 — 06/10/2026

Aceite registra condições imutáveis, exige usuário/escopo vigente, orçamento vigente e versões atuais. Mudança de endereço substitui o orçamento, libera reserva pré-designação e manda eventual cobrança para reconciliação. Subsídio revalida benefício/política/programa e reserva `conta_programa` por atualização condicional. Particular cria cobrança pendente somente quando o adaptador declara capacidade; o padrão permanece indisponível.

Pendências que impedem concluir o ticket: provedor de cobrança contratado/homologado, formato/segredo de eventos definitivo, política de cancelamento/estorno e responsável financeiro real. Designação, liquidação e repasse pertencem aos tickets posteriores e não foram iniciados.
