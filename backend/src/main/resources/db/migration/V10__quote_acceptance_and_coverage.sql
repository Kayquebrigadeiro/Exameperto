CREATE TABLE politica_cancelamento (
    id uuid PRIMARY KEY,
    numero integer NOT NULL UNIQUE CHECK (numero > 0),
    criterios jsonb NOT NULL,
    inicio timestamptz NOT NULL,
    fim timestamptz,
    estado varchar(20) NOT NULL CHECK (estado IN ('ATIVA','INATIVA')),
    CHECK (fim IS NULL OR fim > inicio)
);

ALTER TABLE solicitacao_beneficio ADD COLUMN decisao_valida_ate timestamptz;
ALTER TABLE solicitacao_beneficio ADD COLUMN decisao_revogada_em timestamptz;
ALTER TABLE orcamento ADD COLUMN programa_id uuid REFERENCES programa(id);
ALTER TABLE orcamento ADD COLUMN beneficio_solicitacao_id uuid REFERENCES solicitacao_beneficio(id);
ALTER TABLE orcamento ADD COLUMN politica_cancelamento_id uuid REFERENCES politica_cancelamento(id);
ALTER TABLE orcamento ADD COLUMN pedido_version bigint;
ALTER TABLE orcamento ADD CONSTRAINT orcamento_subsidio_programa CHECK (subsidio_valor = 0 OR programa_id IS NOT NULL);

CREATE TABLE conta_programa (
    programa_id uuid PRIMARY KEY REFERENCES programa(id),
    disponivel numeric(19,2) NOT NULL DEFAULT 0 CHECK (disponivel >= 0),
    reservado numeric(19,2) NOT NULL DEFAULT 0 CHECK (reservado >= 0),
    liquidado numeric(19,2) NOT NULL DEFAULT 0 CHECK (liquidado >= 0),
    moeda char(3) NOT NULL DEFAULT 'BRL' CHECK (moeda='BRL'),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
INSERT INTO conta_programa(programa_id,disponivel)
SELECT p.id,COALESCE(SUM(l.valor),0)::numeric(19,2)
FROM programa p LEFT JOIN lancamento_aporte l ON l.programa_id=p.id AND l.moeda='BRL'
GROUP BY p.id;

CREATE TABLE aceite_orcamento (
    id uuid PRIMARY KEY,
    orcamento_id uuid NOT NULL UNIQUE REFERENCES orcamento(id),
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    aceito_por uuid NOT NULL REFERENCES usuario(id),
    pedido_version bigint NOT NULL,
    orcamento_version bigint NOT NULL,
    condicoes_snapshot jsonb NOT NULL,
    frete numeric(14,2) NOT NULL CHECK (frete > 0),
    paciente_valor numeric(14,2) NOT NULL CHECK (paciente_valor >= 0),
    subsidio_valor numeric(14,2) NOT NULL CHECK (subsidio_valor >= 0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    aceito_em timestamptz NOT NULL DEFAULT now(),
    CHECK (paciente_valor + subsidio_valor = frete)
);
CREATE FUNCTION impedir_mutacao_aceite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'aceite_orcamento is append-only';
END $$;
CREATE TRIGGER aceite_orcamento_imutavel BEFORE UPDATE OR DELETE ON aceite_orcamento
FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_aceite();

CREATE TABLE reserva_subsidio (
    id uuid PRIMARY KEY,
    orcamento_id uuid NOT NULL UNIQUE REFERENCES orcamento(id),
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    programa_id uuid NOT NULL REFERENCES programa(id),
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    valor numeric(19,2) NOT NULL CHECK (valor > 0),
    valor_liquidado numeric(19,2) NOT NULL DEFAULT 0 CHECK (valor_liquidado >= 0),
    valor_liberado numeric(19,2) NOT NULL DEFAULT 0 CHECK (valor_liberado >= 0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    estado varchar(20) NOT NULL CHECK (estado IN ('RESERVADA','PARCIAL','ENCERRADA','LIBERADA','LIQUIDADA')),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (valor_liquidado + valor_liberado <= valor)
);

CREATE TABLE operacao_financeira (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    programa_id uuid REFERENCES programa(id),
    tipo varchar(20) NOT NULL CHECK (tipo IN ('COBRANCA','RESERVA','LIBERACAO','ESTORNO')),
    chave_negocio varchar(160) NOT NULL UNIQUE,
    valor numeric(19,2) NOT NULL CHECK (valor > 0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','PROCESSANDO','CONFIRMADA','FALHOU','INCERTA','RECONCILIAR')),
    provedor varchar(80),
    referencia_externa varchar(160),
    beneficiario_referencia varchar(160),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provedor,referencia_externa)
);

CREATE TABLE financeiro_outbox (
    id uuid PRIMARY KEY,
    operacao_id uuid REFERENCES operacao_financeira(id),
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    tipo varchar(40) NOT NULL CHECK (tipo IN ('CRIAR_COBRANCA','RECONCILIAR_COBRANCA')),
    chave varchar(160) NOT NULL UNIQUE,
    payload_saneado jsonb NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','EM_ENVIO','ENVIADO','RECONCILIAR')),
    tentativas integer NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    disponivel_em timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE evento_pagamento (
    provedor varchar(80) NOT NULL,
    evento_externo_id varchar(160) NOT NULL,
    payload_hash bytea NOT NULL,
    operacao_id uuid REFERENCES operacao_financeira(id),
    estado varchar(20) NOT NULL CHECK (estado IN ('PROCESSADO','REJEITADO','RECONCILIAR')),
    autenticado_em timestamptz NOT NULL,
    processado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (provedor,evento_externo_id)
);

CREATE TABLE aceite_idempotencia (
    ator_id uuid NOT NULL REFERENCES usuario(id),
    operacao varchar(80) NOT NULL,
    chave varchar(128) NOT NULL,
    request_hash bytea NOT NULL,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    aceite_id uuid REFERENCES aceite_orcamento(id),
    PRIMARY KEY (ator_id,operacao,chave)
);

CREATE INDEX reserva_programa_estado ON reserva_subsidio(programa_id,estado);
CREATE INDEX operacao_pedido ON operacao_financeira(pedido_id,created_at,id);
CREATE INDEX outbox_financeira_estado ON financeiro_outbox(estado,disponivel_em,id);
