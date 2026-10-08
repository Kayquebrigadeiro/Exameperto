CREATE TABLE controle_restauracao (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    estado varchar(20) NOT NULL CHECK (estado IN ('NORMAL','BLOQUEADO','APLICANDO','VERIFICADO','FALHA')),
    backup_id uuid,
    iniciado_em timestamptz,
    verificado_em timestamptz,
    liberado_em timestamptz,
    detalhe_codigo varchar(80),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO controle_restauracao(singleton,estado) VALUES (true,'NORMAL');

CREATE TABLE restauracao_expurgo_aplicado (
    backup_id uuid NOT NULL,
    execucao_id uuid NOT NULL,
    titular_id uuid NOT NULL,
    verificado_origem_em timestamptz NOT NULL,
    aplicado_em timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (backup_id,execucao_id)
);

COMMENT ON TABLE controle_restauracao IS 'Gate persistente: uma copia restaurada permanece inacessivel ate reaplicar e verificar expurgos externos ao backup.';
COMMENT ON TABLE restauracao_expurgo_aplicado IS 'Recibos idempotentes da reaplicacao; o diario autoritativo fica fora do backup da aplicacao.';
