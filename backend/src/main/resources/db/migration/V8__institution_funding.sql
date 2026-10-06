ALTER TABLE papel_global DROP CONSTRAINT papel_global_papel_check;
ALTER TABLE papel_global ADD CONSTRAINT papel_global_papel_check CHECK (papel IN ('ANALISTA_OPERACIONAL','GESTOR_FINANCEIRO'));
ALTER TABLE documento DROP CONSTRAINT documento_categoria_check;
ALTER TABLE documento ADD CONSTRAINT documento_categoria_check CHECK (categoria IN ('IDENTIDADE','HABILITACAO','VEICULO','FOTO_OPERACIONAL','IDADE','DEFICIENCIA','RENDA','FINANCIAMENTO','COMPROVANTE'));
ALTER TABLE membro_instituicao DROP CONSTRAINT membro_instituicao_papel_check;
ALTER TABLE membro_instituicao ADD CONSTRAINT membro_instituicao_papel_check CHECK (papel IN ('ANALISTA_BENEFICIO','GESTOR_FINANCEIRO'));

CREATE TABLE programa (
    id uuid PRIMARY KEY,
    instituicao_id uuid NOT NULL REFERENCES instituicao(id),
    codigo varchar(80) NOT NULL,
    nome varchar(200) NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','HABILITADO','SUSPENSO')),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (instituicao_id,codigo),
    UNIQUE (id,instituicao_id)
);

CREATE TABLE aporte (
    id uuid PRIMARY KEY,
    programa_id uuid NOT NULL REFERENCES programa(id),
    registrado_por uuid NOT NULL REFERENCES usuario(id),
    revisado_por uuid REFERENCES usuario(id),
    revisado_em timestamptz,
    comprovante_id uuid NOT NULL REFERENCES documento(id),
    conciliacao_documento_id uuid REFERENCES documento(id),
    revisao_motivo_codigo varchar(200),
    valor numeric(19,2) NOT NULL CHECK (valor > 0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    origem_referencia varchar(160) NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','CONFIRMADO','REJEITADO')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (programa_id,origem_referencia)
);
CREATE INDEX aporte_programa_estado ON aporte(programa_id,estado,created_at,id);

CREATE TABLE lancamento_aporte (
    id uuid PRIMARY KEY,
    aporte_id uuid NOT NULL UNIQUE REFERENCES aporte(id),
    programa_id uuid NOT NULL REFERENCES programa(id),
    valor numeric(19,2) NOT NULL CHECK (valor > 0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    confirmado_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX lancamento_aporte_programa ON lancamento_aporte(programa_id,confirmado_em,id);

CREATE TABLE financiamento_idempotencia (
    ator_id uuid NOT NULL REFERENCES usuario(id),
    operacao varchar(60) NOT NULL,
    chave varchar(128) NOT NULL,
    request_hash bytea NOT NULL,
    aporte_id uuid NOT NULL REFERENCES aporte(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (ator_id,operacao,chave)
);

CREATE TABLE auditoria_financeira (
    id uuid PRIMARY KEY,
    ator_id uuid NOT NULL REFERENCES usuario(id),
    aporte_id uuid NOT NULL REFERENCES aporte(id),
    acao varchar(40) NOT NULL CHECK (acao IN ('REGISTRADO','CONFIRMADO','REJEITADO')),
    motivo_codigo varchar(200),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_financeira_aporte ON auditoria_financeira(aporte_id,created_at,id);
