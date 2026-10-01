-- Estruturas da fronteira 03A. Nenhuma conta, segredo ou envio é criado.
CREATE TABLE usuario (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    email_cifrado bytea NOT NULL,
    email_busca bytea NOT NULL UNIQUE,
    senha_hash text NOT NULL,
    nome_cifrado bytea NOT NULL,
    telefone_cifrado bytea,
    estado varchar(40) NOT NULL CHECK (estado IN ('PENDENTE_EMAIL','ATIVO','BLOQUEADO','ENCERRADO')),
    email_verificado_em timestamptz
);
CREATE TABLE sessao (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    familia_id uuid NOT NULL,
    refresh_hash bytea NOT NULL UNIQUE,
    substituida_por uuid REFERENCES sessao(id),
    expira_em timestamptz NOT NULL,
    revogada_em timestamptz,
    cliente varchar(10) NOT NULL CHECK (cliente IN ('WEB','MOBILE'))
);
CREATE INDEX sessao_usuario_revogada ON sessao(usuario_id, revogada_em);
CREATE INDEX sessao_substituida ON sessao(substituida_por);
CREATE TABLE desafio_conta (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    tipo varchar(40) NOT NULL CHECK (tipo IN ('EMAIL','RECUPERACAO')),
    token_hash bytea NOT NULL UNIQUE,
    expira_em timestamptz NOT NULL,
    consumido_em timestamptz,
    tentativas smallint NOT NULL DEFAULT 0 CHECK (tentativas >= 0)
);
CREATE INDEX desafio_usuario ON desafio_conta(usuario_id);
-- Relações financeiras e de pedido só serão adicionadas nas etapas autorizadas correspondentes.
CREATE TABLE outbox (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    tipo varchar(60) NOT NULL,
    chave varchar(160) NOT NULL UNIQUE,
    payload_saneado jsonb NOT NULL,
    estado varchar(40) NOT NULL CHECK (estado IN ('PENDENTE','EM_ENVIO','ENVIADO','RECONCILIAR')),
    tentativas integer NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    disponivel_em timestamptz NOT NULL,
    lease_ate timestamptz
);
CREATE INDEX outbox_disponivel ON outbox(estado, disponivel_em);
