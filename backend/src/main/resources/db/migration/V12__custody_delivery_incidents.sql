CREATE TABLE custodia (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL UNIQUE REFERENCES pedido(id),
    designacao_id uuid NOT NULL REFERENCES designacao(id),
    protocolo_id uuid NOT NULL REFERENCES protocolo_custodia(id),
    retirada_em timestamptz NOT NULL,
    retirada_evidencia_id uuid NOT NULL REFERENCES documento(id),
    destino_tipo varchar(20),
    destino_usuario_id uuid REFERENCES usuario(id),
    destino_unidade_id uuid,
    encerrada_em timestamptz,
    destino_evidencia_id uuid REFERENCES documento(id),
    recebimento_evento_id uuid,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (destino_tipo IS NULL OR destino_tipo IN ('ENTREGA','RETORNO')),
    CHECK ((destino_tipo IS NULL AND destino_usuario_id IS NULL AND destino_unidade_id IS NULL)
        OR (destino_tipo='ENTREGA' AND destino_usuario_id IS NOT NULL AND destino_unidade_id IS NULL)
        OR (destino_tipo='RETORNO' AND destino_usuario_id IS NULL AND destino_unidade_id IS NOT NULL)),
    CHECK (encerrada_em IS NULL OR destino_tipo IS NOT NULL)
);

CREATE TABLE evento_custodia (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    custodia_id uuid REFERENCES custodia(id),
    designacao_id uuid REFERENCES designacao(id),
    ator_id uuid NOT NULL REFERENCES usuario(id),
    tipo varchar(30) NOT NULL CHECK (tipo IN ('RETIRADA','INICIO_ENTREGA','ENTREGA_COMPROVADA','OCORRENCIA')),
    estado_anterior varchar(30) NOT NULL,
    estado_novo varchar(30) NOT NULL,
    evidencia_id uuid REFERENCES documento(id),
    motivo_codigo varchar(80),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX evento_custodia_pedido ON evento_custodia(pedido_id,created_at,id);

CREATE TABLE codigo_recebimento (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    destinatario_id uuid NOT NULL REFERENCES usuario(id),
    hash bytea NOT NULL,
    expira_em timestamptz NOT NULL,
    tentativas integer NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    max_tentativas integer NOT NULL CHECK (max_tentativas > 0),
    emitido_em timestamptz NOT NULL DEFAULT now(),
    usado_em timestamptz,
    invalidado_em timestamptz
);
CREATE UNIQUE INDEX codigo_recebimento_ativo ON codigo_recebimento(pedido_id) WHERE usado_em IS NULL AND invalidado_em IS NULL;

CREATE TABLE ocorrencia_entrega (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    custodia_id uuid REFERENCES custodia(id),
    designacao_id uuid REFERENCES designacao(id),
    aberta_por uuid NOT NULL REFERENCES usuario(id),
    tipo varchar(40) NOT NULL CHECK (tipo IN ('UNIDADE_FECHADA','DESTINATARIO_AUSENTE','LACRE_COMPROMETIDO','OUTRA')),
    descricao varchar(500),
    estado varchar(20) NOT NULL CHECK (estado IN ('ABERTA','PENDENTE_RESOLUCAO','ENCERRADA')),
    created_at timestamptz NOT NULL DEFAULT now(),
    encerrada_em timestamptz
);
CREATE INDEX ocorrencia_entrega_pedido ON ocorrencia_entrega(pedido_id,created_at,id);

CREATE TABLE custodia_idempotencia (
    ator_id uuid NOT NULL REFERENCES usuario(id),
    operacao varchar(80) NOT NULL,
    chave varchar(128) NOT NULL,
    request_hash bytea NOT NULL,
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    resultado_id uuid,
    PRIMARY KEY (ator_id,operacao,chave)
);
