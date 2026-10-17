CREATE TABLE solicitacao_cancelamento (
  id uuid PRIMARY KEY,
  pedido_id uuid NOT NULL REFERENCES pedido(id),
  designacao_id uuid REFERENCES designacao(id),
  custodia_id uuid REFERENCES custodia(id),
  solicitada_por uuid NOT NULL REFERENCES usuario(id),
  fase varchar(40) NOT NULL CHECK (fase IN ('ANTES_DESIGNACAO','APOS_DESIGNACAO_ANTES_RETIRADA','APOS_RETIRADA')),
  motivo_codigo varchar(80) NOT NULL,
  estado varchar(30) NOT NULL CHECK (estado IN ('PENDENTE_APURACAO','APROVADA','RECUSADA','ENCERRADA')),
  tratamento_financeiro varchar(30) NOT NULL CHECK (tratamento_financeiro IN ('NAO_APLICAVEL','INTENCAO_ESTORNO','ESTORNO_SOLICITADO','ESTORNO_CONFIRMADO')),
  evidencia_provedor varchar(300),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX solicitacao_cancelamento_pendente ON solicitacao_cancelamento(pedido_id) WHERE estado='PENDENTE_APURACAO';
