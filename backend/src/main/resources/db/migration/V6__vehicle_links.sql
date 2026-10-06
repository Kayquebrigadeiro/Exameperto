CREATE TABLE veiculo (
    id uuid PRIMARY KEY,
    placa varchar(10) NOT NULL UNIQUE,
    marca varchar(80) NOT NULL,
    modelo varchar(80) NOT NULL,
    cor varchar(40) NOT NULL,
    ano_fabricacao smallint NOT NULL CHECK (ano_fabricacao >= 1886),
    ano_modelo smallint NOT NULL CHECK (ano_modelo >= ano_fabricacao AND ano_modelo <= ano_fabricacao + 1),
    estado varchar(20) NOT NULL CHECK (estado IN ('RASCUNHO','EM_ANALISE','APROVADO','REJEITADO','SUSPENSO')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE vinculo_veiculo (
    id uuid PRIMARY KEY,
    entregador_id uuid NOT NULL REFERENCES entregador(id),
    veiculo_id uuid NOT NULL REFERENCES veiculo(id),
    tipo varchar(20) NOT NULL CHECK (tipo IN ('PROPRIEDADE','LOCACAO','AUTORIZACAO')),
    estado varchar(20) NOT NULL CHECK (estado IN ('RASCUNHO','EM_ANALISE','APROVADO','REJEITADO','SUSPENSO')),
    valido_ate date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (id, entregador_id)
);
CREATE INDEX vinculo_veiculo_entregador ON vinculo_veiculo(entregador_id, estado, created_at DESC, id);

CREATE TABLE vinculo_documento (
    vinculo_id uuid NOT NULL REFERENCES vinculo_veiculo(id),
    documento_id uuid NOT NULL REFERENCES documento(id),
    finalidade varchar(30) NOT NULL CHECK (finalidade IN ('CRLV','FOTO','USO_AUTORIZADO')),
    criado_em timestamptz NOT NULL DEFAULT now(),
    substituido_em timestamptz,
    PRIMARY KEY (vinculo_id, documento_id)
);
CREATE UNIQUE INDEX vinculo_documento_corrente ON vinculo_documento(vinculo_id, finalidade) WHERE substituido_em IS NULL;

CREATE TABLE revisao_vinculo_veiculo (
    id uuid PRIMARY KEY,
    vinculo_id uuid NOT NULL REFERENCES vinculo_veiculo(id),
    versao_vinculo bigint NOT NULL CHECK (versao_vinculo > 0),
    analista_id uuid REFERENCES usuario(id),
    estado varchar(30) NOT NULL CHECK (estado IN ('PENDENTE','ATRIBUIDA','CONCLUIDA','BLOQUEADA')),
    decisao varchar(30) CHECK (decisao IN ('APROVAR','REJEITAR')),
    motivo_codigo varchar(80),
    criado_em timestamptz NOT NULL DEFAULT now(),
    atribuido_em timestamptz,
    decidido_em timestamptz
);
CREATE UNIQUE INDEX revisao_vinculo_ativa ON revisao_vinculo_veiculo(vinculo_id) WHERE estado IN ('PENDENTE','ATRIBUIDA');
CREATE INDEX revisao_vinculo_fila ON revisao_vinculo_veiculo(estado, criado_em, id);
CREATE INDEX revisao_vinculo_analista ON revisao_vinculo_veiculo(analista_id, estado, criado_em);

CREATE TABLE revisao_vinculo_documento (
    revisao_id uuid NOT NULL REFERENCES revisao_vinculo_veiculo(id),
    documento_id uuid NOT NULL REFERENCES documento(id),
    finalidade varchar(30) NOT NULL CHECK (finalidade IN ('CRLV','FOTO','USO_AUTORIZADO')),
    PRIMARY KEY (revisao_id, documento_id)
);
