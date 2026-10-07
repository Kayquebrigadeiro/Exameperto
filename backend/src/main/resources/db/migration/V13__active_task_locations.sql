CREATE TABLE posicao_tarefa (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    designacao_id uuid NOT NULL REFERENCES designacao(id),
    sequencia bigint NOT NULL CHECK (sequencia > 0),
    capturada_em timestamptz NOT NULL,
    recebida_em timestamptz NOT NULL DEFAULT clock_timestamp(),
    latitude numeric(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude numeric(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    precisao_m numeric(8,2) NOT NULL CHECK (precisao_m BETWEEN 0 AND 999999.99),
    UNIQUE(designacao_id, sequencia)
);
CREATE INDEX posicao_tarefa_pedido_recente ON posicao_tarefa(pedido_id, capturada_em DESC, sequencia DESC);
CREATE INDEX posicao_tarefa_expurgo ON posicao_tarefa(recebida_em);

COMMENT ON TABLE posicao_tarefa IS 'GPS de tarefa ativa; habilitação real e expurgo dependem de política de retenção aprovada.';
