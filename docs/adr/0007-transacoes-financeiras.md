# Transações locais e efeitos externos reconciliáveis

Status: proposta técnica para revisão, 29/09/2026.

Reserva e liquidação serão atômicas no PostgreSQL; chamadas ao provedor ficarão fora da transação, com outbox, chave de operação persistente e conciliação de resultados incertos. Uma transação distribuída com o banco do provedor não está disponível, e repetir cegamente uma chamada após timeout pode pagar duas vezes. A consequência é expor estados pendentes e exigir confirmação externa para declarar pagamento ou repasse concluído; o detalhamento está no [modelo físico](../MODELO-DADOS.md).

Ajuste D06 (30/09/2026): cancelar não libera automaticamente toda a cobertura nem gera frete integral. Apuração versionada de deslocamento/serviço comprovados precede liquidação e liberação do excedente, com trilha, evidências e idempotência. Após retirada, destino do envelope comprovado e cobertura de retorno são guardas de encerramento. Fórmula, valores, multas, responsáveis e fonte de cobertura continuam pendentes; não ativar operação sem validação.
