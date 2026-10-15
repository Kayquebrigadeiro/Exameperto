CREATE TABLE unidade_retirada (
    id uuid PRIMARY KEY,
    nome varchar(200) NOT NULL,
    cidade varchar(120) NOT NULL,
    estado varchar(2) NOT NULL CHECK (estado ~ '^[A-Z]{2}$'),
    estado_cadastro varchar(20) NOT NULL CHECK (estado_cadastro IN ('CADASTRADA','ATIVA','DESATIVADA')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    criado_por uuid NOT NULL REFERENCES usuario(id),
    atualizado_por uuid NOT NULL REFERENCES usuario(id),
    UNIQUE (nome,cidade,estado)
);

CREATE TABLE aceite_unidade_retirada (
    id uuid PRIMARY KEY,
    unidade_id uuid NOT NULL REFERENCES unidade_retirada(id),
    estado varchar(20) NOT NULL CHECK (estado IN ('PENDENTE','ACEITA','REVOGADA')),
    evidencia_referencia varchar(300) NOT NULL,
    observacao varchar(1000),
    aceito_por uuid REFERENCES usuario(id),
    aceito_em timestamptz,
    criado_por uuid NOT NULL REFERENCES usuario(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((estado='ACEITA') = (aceito_por IS NOT NULL AND aceito_em IS NOT NULL))
);
CREATE UNIQUE INDEX aceite_unidade_atual ON aceite_unidade_retirada(unidade_id) WHERE estado IN ('PENDENTE','ACEITA');

ALTER TABLE protocolo_custodia ADD CONSTRAINT protocolo_unidade_fk FOREIGN KEY (unidade_id) REFERENCES unidade_retirada(id);

CREATE TABLE auditoria_unidade_retirada (
    id uuid PRIMARY KEY,
    unidade_id uuid NOT NULL REFERENCES unidade_retirada(id),
    protocolo_id uuid REFERENCES protocolo_custodia(id),
    ator_id uuid NOT NULL REFERENCES usuario(id),
    acao varchar(40) NOT NULL CHECK (acao IN ('UNIDADE_CRIADA','UNIDADE_EDITADA','UNIDADE_ATIVADA','UNIDADE_DESATIVADA','PARCERIA_REGISTRADA','PARCERIA_ACEITA','PARCERIA_REVOGADA','PROTOCOLO_CRIADO','PROTOCOLO_EDITADO','PROTOCOLO_HABILITADO','PROTOCOLO_SUSPENSO')),
    detalhes jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_unidade_data ON auditoria_unidade_retirada(unidade_id,created_at DESC,id);

CREATE INDEX protocolo_unidade_disponivel ON protocolo_custodia(unidade_id,estado,inicio,fim);
