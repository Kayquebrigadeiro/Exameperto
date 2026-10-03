CREATE TABLE papel_global (
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    papel varchar(30) NOT NULL CHECK (papel IN ('ANALISTA_OPERACIONAL')),
    concedido_por uuid REFERENCES usuario(id),
    concedido_em timestamptz NOT NULL DEFAULT now(),
    revogado_em timestamptz,
    motivo text,
    PRIMARY KEY (usuario_id, papel)
);
CREATE INDEX papel_global_ativo ON papel_global(usuario_id, papel) WHERE revogado_em IS NULL;

CREATE TABLE entregador (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL UNIQUE REFERENCES usuario(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    nascimento date NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('RASCUNHO','EM_ANALISE','APROVADO','REJEITADO','SUSPENSO')),
    foto_aprovada_id uuid
);

CREATE TABLE documento (
    id uuid PRIMARY KEY,
    proprietario_id uuid NOT NULL REFERENCES usuario(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    categoria varchar(30) NOT NULL CHECK (categoria IN ('IDENTIDADE','HABILITACAO','VEICULO','FOTO_OPERACIONAL')),
    objeto_chave varchar(300) NOT NULL UNIQUE,
    sha256 bytea NOT NULL,
    mime varchar(100) NOT NULL,
    tamanho bigint NOT NULL CHECK (tamanho > 0 AND tamanho <= 10485760),
    estado varchar(30) NOT NULL CHECK (estado IN ('QUARENTENA','INSPECAO_PENDENTE','INSPECAO_APROVADA','REJEITADO','EXPURGADO')),
    inspeccionado_em timestamptz,
    expurgar_em timestamptz
);
CREATE INDEX documento_proprietario ON documento(proprietario_id, created_at DESC, id);

CREATE TABLE entregador_documento (
    entregador_id uuid NOT NULL REFERENCES entregador(id),
    documento_id uuid NOT NULL UNIQUE REFERENCES documento(id),
    finalidade varchar(30) NOT NULL,
    criado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (entregador_id, documento_id)
);

CREATE TABLE revisao_entregador (
    id uuid PRIMARY KEY,
    entregador_id uuid NOT NULL REFERENCES entregador(id),
    documento_id uuid REFERENCES documento(id),
    analista_id uuid REFERENCES usuario(id),
    estado varchar(30) NOT NULL CHECK (estado IN ('PENDENTE','ATRIBUIDA','CONCLUIDA','BLOQUEADA')),
    decisao varchar(30) CHECK (decisao IN ('APROVAR','REJEITAR')),
    motivo_codigo varchar(80),
    criado_em timestamptz NOT NULL DEFAULT now(),
    atribuido_em timestamptz,
    decidido_em timestamptz,
    CHECK (documento_id IS NOT NULL OR analista_id IS NULL OR estado <> 'CONCLUIDA')
);
CREATE INDEX revisao_fila ON revisao_entregador(estado, criado_em, id);
CREATE INDEX revisao_analista ON revisao_entregador(analista_id, estado, criado_em);

ALTER TABLE entregador ADD CONSTRAINT entregador_foto_fk FOREIGN KEY (foto_aprovada_id) REFERENCES documento(id);
