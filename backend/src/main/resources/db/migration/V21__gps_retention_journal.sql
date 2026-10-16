CREATE TABLE diario_expurgo_gps (
    id uuid PRIMARY KEY,
    politica_id uuid NOT NULL REFERENCES politica_retencao(id),
    designacao_id uuid NOT NULL,
    sequencia bigint NOT NULL CHECK (sequencia > 0),
    referencia_hash bytea NOT NULL,
    capturada_em timestamptz NOT NULL,
    expurgado_em timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(designacao_id, sequencia)
);

CREATE INDEX diario_expurgo_gps_politica ON diario_expurgo_gps(politica_id, expurgado_em);

COMMENT ON TABLE diario_expurgo_gps IS 'Diário append-only de posições GPS expurgadas; não conserva coordenadas e deve ser capturado no diário externo antes de restauração.';
