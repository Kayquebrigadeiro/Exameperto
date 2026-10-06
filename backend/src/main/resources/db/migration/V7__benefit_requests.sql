ALTER TABLE documento DROP CONSTRAINT documento_categoria_check;
ALTER TABLE documento ADD CONSTRAINT documento_categoria_check CHECK (categoria IN ('IDENTIDADE','HABILITACAO','VEICULO','FOTO_OPERACIONAL','IDADE','DEFICIENCIA','RENDA'));

CREATE TABLE instituicao (
    id uuid PRIMARY KEY,
    nome varchar(200) NOT NULL,
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','HABILITADA','SUSPENSA')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE membro_instituicao (
    instituicao_id uuid NOT NULL REFERENCES instituicao(id),
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    papel varchar(30) NOT NULL CHECK (papel IN ('ANALISTA_BENEFICIO')),
    revogado_em timestamptz,
    PRIMARY KEY (instituicao_id,usuario_id,papel)
);
CREATE INDEX membro_instituicao_ativo ON membro_instituicao(usuario_id,instituicao_id) WHERE revogado_em IS NULL;

CREATE TABLE politica_beneficio (
    id uuid PRIMARY KEY,
    instituicao_id uuid NOT NULL REFERENCES instituicao(id),
    codigo varchar(80) NOT NULL,
    versao integer NOT NULL CHECK (versao > 0),
    criterios jsonb NOT NULL,
    percentual_maximo numeric(5,2) NOT NULL CHECK (percentual_maximo >= 0 AND percentual_maximo <= 100),
    vigencia_inicio timestamptz NOT NULL,
    vigencia_fim timestamptz,
    estado varchar(20) NOT NULL CHECK (estado IN ('RASCUNHO','ATIVA','ENCERRADA')),
    UNIQUE (instituicao_id,codigo,versao),
    UNIQUE (id,instituicao_id),
    CHECK (vigencia_fim IS NULL OR vigencia_fim > vigencia_inicio)
);
CREATE INDEX politica_beneficio_vigente ON politica_beneficio(instituicao_id,estado,vigencia_inicio);

CREATE TABLE solicitacao_beneficio (
    id uuid PRIMARY KEY,
    paciente_id uuid NOT NULL REFERENCES paciente(id),
    solicitante_id uuid NOT NULL REFERENCES usuario(id),
    instituicao_id uuid NOT NULL REFERENCES instituicao(id),
    politica_id uuid NOT NULL REFERENCES politica_beneficio(id),
    politica_versao integer NOT NULL,
    politica_snapshot jsonb NOT NULL,
    estado varchar(25) NOT NULL CHECK (estado IN ('EM_ANALISE','DECIDIDA','RECURSO','ENCERRADA')),
    decisao varchar(20) CHECK (decisao IN ('APROVADA','REJEITADA')),
    percentual numeric(5,2) CHECK (percentual IS NULL OR (percentual >= 0 AND percentual <= 100)),
    motivo_codigo varchar(80),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id,paciente_id),
    FOREIGN KEY (politica_id,instituicao_id) REFERENCES politica_beneficio(id,instituicao_id)
);
CREATE TABLE solicitacao_dimensao (
    solicitacao_id uuid NOT NULL REFERENCES solicitacao_beneficio(id),
    dimensao varchar(20) NOT NULL CHECK (dimensao IN ('IDADE','DEFICIENCIA','RENDA')),
    PRIMARY KEY (solicitacao_id,dimensao)
);
CREATE TABLE beneficio_evidencia (
    solicitacao_id uuid NOT NULL REFERENCES solicitacao_beneficio(id),
    documento_id uuid NOT NULL REFERENCES documento(id),
    finalidade varchar(20) NOT NULL CHECK (finalidade IN ('IDADE','DEFICIENCIA','RENDA')),
    criado_em timestamptz NOT NULL DEFAULT now(),
    substituido_em timestamptz,
    PRIMARY KEY (solicitacao_id,documento_id)
);
CREATE UNIQUE INDEX beneficio_evidencia_corrente ON beneficio_evidencia(solicitacao_id,finalidade) WHERE substituido_em IS NULL;

CREATE TABLE revisao_beneficio (
    id uuid PRIMARY KEY,
    solicitacao_id uuid NOT NULL REFERENCES solicitacao_beneficio(id),
    tipo varchar(15) NOT NULL CHECK (tipo IN ('INICIAL','RECURSO')),
    solicitacao_version bigint NOT NULL,
    analista_id uuid REFERENCES usuario(id),
    estado varchar(25) NOT NULL CHECK (estado IN ('PENDENTE','ATRIBUIDA','CONCLUIDA','BLOQUEADA')),
    decisao varchar(20) CHECK (decisao IN ('APROVADA','REJEITADA')),
    percentual numeric(5,2) CHECK (percentual IS NULL OR (percentual >= 0 AND percentual <= 100)),
    motivo_codigo varchar(80),
    criado_em timestamptz NOT NULL DEFAULT now(),
    atribuido_em timestamptz,
    decidido_em timestamptz
);
CREATE UNIQUE INDEX revisao_beneficio_ativa ON revisao_beneficio(solicitacao_id) WHERE estado IN ('PENDENTE','ATRIBUIDA');
CREATE INDEX revisao_beneficio_fila ON revisao_beneficio(estado,criado_em,id);
