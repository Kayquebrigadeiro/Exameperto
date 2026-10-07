CREATE TABLE politica_designacao (
    id uuid PRIMARY KEY,
    numero integer NOT NULL UNIQUE CHECK (numero > 0),
    limite_tarefas_simultaneas integer NOT NULL CHECK (limite_tarefas_simultaneas > 0),
    inicio timestamptz NOT NULL,
    fim timestamptz,
    estado varchar(20) NOT NULL CHECK (estado IN ('ATIVA','INATIVA')),
    CHECK (fim IS NULL OR fim > inicio)
);

CREATE TABLE protocolo_custodia (
    id uuid PRIMARY KEY,
    unidade_id uuid NOT NULL,
    numero integer NOT NULL CHECK (numero > 0),
    regras jsonb NOT NULL,
    cobertura_retorno_confirmada boolean NOT NULL DEFAULT false,
    inicio timestamptz NOT NULL,
    fim timestamptz,
    estado varchar(20) NOT NULL CHECK (estado IN ('HABILITADO','SUSPENSO')),
    UNIQUE (unidade_id,numero),
    CHECK (fim IS NULL OR fim > inicio)
);
ALTER TABLE pedido ADD COLUMN protocolo_id uuid REFERENCES protocolo_custodia(id);

CREATE TABLE designacao (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    entregador_id uuid NOT NULL REFERENCES entregador(id),
    vinculo_id uuid NOT NULL,
    orcamento_id uuid NOT NULL,
    politica_designacao_id uuid NOT NULL REFERENCES politica_designacao(id),
    politica_cancelamento_id uuid NOT NULL REFERENCES politica_cancelamento(id),
    pedido_version bigint NOT NULL CHECK (pedido_version >= 0),
    limite_tarefas_snapshot integer NOT NULL CHECK (limite_tarefas_snapshot > 0),
    identificacao_snapshot jsonb NOT NULL,
    aceita_em timestamptz NOT NULL DEFAULT now(),
    encerrada_em timestamptz,
    encerramento_motivo varchar(80),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (id,pedido_id),
    FOREIGN KEY (vinculo_id,entregador_id) REFERENCES vinculo_veiculo(id,entregador_id),
    FOREIGN KEY (orcamento_id,pedido_id) REFERENCES orcamento(id,pedido_id)
);
CREATE UNIQUE INDEX designacao_pedido_ativa ON designacao(pedido_id) WHERE encerrada_em IS NULL;
CREATE INDEX designacao_entregador_ativa ON designacao(entregador_id,aceita_em,id) WHERE encerrada_em IS NULL;

CREATE TABLE evento_designacao (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    designacao_id uuid REFERENCES designacao(id),
    ator_id uuid NOT NULL REFERENCES usuario(id),
    tipo varchar(30) NOT NULL CHECK (tipo IN ('ACEITE','CANCELAMENTO_PRE_DESIGNACAO')),
    estado_anterior varchar(30) NOT NULL,
    estado_novo varchar(30) NOT NULL,
    pedido_version bigint NOT NULL,
    motivo_codigo varchar(80),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX evento_designacao_pedido ON evento_designacao(pedido_id,created_at,id);

CREATE TABLE designacao_idempotencia (
    ator_id uuid NOT NULL REFERENCES usuario(id),
    operacao varchar(80) NOT NULL,
    chave varchar(128) NOT NULL,
    request_hash bytea NOT NULL,
    designacao_id uuid NOT NULL REFERENCES designacao(id),
    PRIMARY KEY (ator_id,operacao,chave)
);
