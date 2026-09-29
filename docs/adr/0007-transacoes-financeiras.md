# Transações locais e efeitos externos reconciliáveis

Status: proposta técnica para revisão, 29/09/2026.

Reserva e liquidação serão atômicas no PostgreSQL; chamadas ao provedor ficarão fora da transação, com outbox, chave de operação persistente e conciliação de resultados incertos. Uma transação distribuída com o banco do provedor não está disponível, e repetir cegamente uma chamada após timeout pode pagar duas vezes. A consequência é expor estados pendentes e exigir confirmação externa para declarar pagamento ou repasse concluído; o detalhamento está no [modelo físico](../MODELO-DADOS.md).
