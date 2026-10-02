CREATE TABLE paciente (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    usuario_id uuid NOT NULL UNIQUE REFERENCES usuario(id),
    cpf_cifrado bytea NOT NULL,
    cpf_busca bytea NOT NULL UNIQUE,
    nascimento date NOT NULL,
    identidade_estado varchar(20) NOT NULL CHECK (identidade_estado IN ('PENDENTE','VERIFICADA','REJEITADA')),
    verificado_em timestamptz
);

CREATE TABLE convite_familiar (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    destinatario_email_busca bytea NOT NULL,
    token_hash bytea NOT NULL UNIQUE,
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','ACEITO','CONFIRMADO','REVOGADO')),
    expira_em timestamptz NOT NULL,
    consumido_em timestamptz,
    aceito_por uuid REFERENCES usuario(id),
    aceito_em timestamptz,
    confirmado_em timestamptz,
    revogado_em timestamptz,
    CHECK ((consumido_em IS NULL) = (aceito_por IS NULL)),
    CHECK ((aceito_por IS NULL) = (aceito_em IS NULL))
);
CREATE INDEX convite_familiar_paciente ON convite_familiar(paciente_id, created_at DESC, id);
CREATE INDEX convite_familiar_destinatario ON convite_familiar(destinatario_email_busca, estado, expira_em);

CREATE TABLE convite_familiar_escopo (
    convite_id uuid NOT NULL REFERENCES convite_familiar(id),
    escopo varchar(30) NOT NULL CHECK (escopo IN ('PEDIDOS','BENEFICIOS','RASTREAMENTO','RECEBIMENTO')),
    PRIMARY KEY (convite_id, escopo)
);

CREATE TABLE autorizacao_paciente (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    familiar_id uuid NOT NULL REFERENCES usuario(id),
    concedida_por uuid NOT NULL REFERENCES usuario(id),
    convite_id uuid NOT NULL UNIQUE REFERENCES convite_familiar(id),
    confirmada_em timestamptz NOT NULL,
    expira_em timestamptz NOT NULL,
    revogada_em timestamptz,
    CHECK (familiar_id <> concedida_por),
    CHECK (expira_em > confirmada_em)
);
CREATE UNIQUE INDEX autorizacao_paciente_ativa
    ON autorizacao_paciente(paciente_id, familiar_id) WHERE revogada_em IS NULL;
CREATE INDEX autorizacao_familiar_vigencia
    ON autorizacao_paciente(familiar_id, revogada_em, expira_em);

CREATE TABLE autorizacao_escopo (
    autorizacao_id uuid NOT NULL REFERENCES autorizacao_paciente(id),
    escopo varchar(30) NOT NULL CHECK (escopo IN ('PEDIDOS','BENEFICIOS','RASTREAMENTO','RECEBIMENTO')),
    PRIMARY KEY (autorizacao_id, escopo)
);
