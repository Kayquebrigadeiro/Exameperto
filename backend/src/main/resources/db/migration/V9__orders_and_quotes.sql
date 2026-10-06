ALTER TABLE documento DROP CONSTRAINT IF EXISTS documento_categoria_check;
ALTER TABLE documento ADD CONSTRAINT documento_categoria_check CHECK (categoria IN ('IDENTIDADE','HABILITACAO','VEICULO','FOTO_OPERACIONAL','IDADE','DEFICIENCIA','RENDA','FINANCIAMENTO','COMPROVANTE','AUTORIZACAO_RETIRADA'));

CREATE TABLE tarifa (
    id uuid PRIMARY KEY,
    numero integer NOT NULL UNIQUE CHECK (numero > 0),
    formula_codigo varchar(50) NOT NULL,
    parametros jsonb NOT NULL,
    inicio timestamptz NOT NULL,
    fim timestamptz,
    estado varchar(20) NOT NULL CHECK (estado IN ('ATIVA','INATIVA')),
    aprovada_por uuid REFERENCES usuario(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (fim IS NULL OR fim > inicio)
);

CREATE TABLE pedido (
    id uuid PRIMARY KEY,
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    solicitante_id uuid NOT NULL REFERENCES usuario(id),
    origem_cifrada bytea NOT NULL,
    destino_cifrada bytea NOT NULL,
    origem_lat numeric(9,6) NOT NULL CHECK (origem_lat BETWEEN -90 AND 90),
    origem_lon numeric(9,6) NOT NULL CHECK (origem_lon BETWEEN -180 AND 180),
    destino_lat numeric(9,6) NOT NULL CHECK (destino_lat BETWEEN -90 AND 90),
    destino_lon numeric(9,6) NOT NULL CHECK (destino_lon BETWEEN -180 AND 180),
    destinatario_id uuid NOT NULL REFERENCES usuario(id),
    unidade_id uuid NOT NULL,
    estado varchar(30) NOT NULL CHECK (estado IN ('SOLICITADA','EM_VERIFICACAO','AGUARDANDO_ACEITE','DISPONIVEL','ACEITA','RETIRADA','EM_ENTREGA','ENTREGUE','OCORRENCIA','CANCELADA','NAO_ATENDIDA','ENCERRADA_COM_OCORRENCIA')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id,paciente_id)
);
CREATE INDEX pedido_paciente ON pedido(paciente_id,created_at DESC,id);

CREATE TABLE autorizacao_retirada (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL UNIQUE REFERENCES pedido(id),
    documento_id uuid NOT NULL REFERENCES documento(id),
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','VERIFICADA','REJEITADA')),
    criada_por uuid NOT NULL REFERENCES usuario(id),
    verificada_por uuid REFERENCES usuario(id),
    valida_ate timestamptz NOT NULL,
    revogada_em timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE orcamento (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    tarifa_id uuid NOT NULL REFERENCES tarifa(id),
    rota_provedor varchar(80) NOT NULL,
    rota_referencia text NOT NULL,
    distancia_m integer NOT NULL CHECK (distancia_m > 0),
    duracao_s integer NOT NULL CHECK (duracao_s > 0),
    rota_calculada_em timestamptz NOT NULL,
    transito_incluido boolean NOT NULL DEFAULT false,
    politica_snapshot jsonb NOT NULL,
    frete numeric(14,2) NOT NULL CHECK (frete > 0),
    paciente_valor numeric(14,2) NOT NULL CHECK (paciente_valor >= 0),
    subsidio_valor numeric(14,2) NOT NULL DEFAULT 0 CHECK (subsidio_valor >= 0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    expira_em timestamptz NOT NULL,
    aceito_em timestamptz,
    estado varchar(20) NOT NULL CHECK (estado IN ('PROPOSTO','ACEITO','EXPIRADO','SUBSTITUIDO')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (paciente_valor + subsidio_valor = frete),
    UNIQUE (id,pedido_id)
);
CREATE INDEX orcamento_pedido ON orcamento(pedido_id,created_at DESC,id);
CREATE UNIQUE INDEX orcamento_aceito ON orcamento(pedido_id) WHERE estado='ACEITO';

CREATE TABLE pedido_idempotencia (
    ator_id uuid NOT NULL REFERENCES usuario(id),
    operacao varchar(80) NOT NULL,
    chave varchar(128) NOT NULL,
    request_hash bytea NOT NULL,
    pedido_id uuid REFERENCES pedido(id),
    orcamento_id uuid REFERENCES orcamento(id),
    PRIMARY KEY (ator_id,operacao,chave)
);
