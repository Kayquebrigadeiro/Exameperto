CREATE TABLE solicitacao_privacidade (
    id uuid PRIMARY KEY,
    protocolo varchar(40) NOT NULL UNIQUE,
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    idempotency_key varchar(128),
    request_hash bytea NOT NULL,
    tipo varchar(12) NOT NULL CHECK (tipo IN ('ACESSO','CORRECAO','EXCLUSAO')),
    descricao_cifrada bytea,
    estado varchar(24) NOT NULL CHECK (estado IN ('PENDENTE','EM_ANALISE','RESPONDIDA','NEGADA','EXPURGO_SOLICITADO','EXPURGADA','VERIFICADA')),
    responsavel_id uuid REFERENCES usuario(id),
    resposta_cifrada bytea,
    respondida_em timestamptz,
    expurgo_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0)
);
CREATE UNIQUE INDEX solicitacao_privacidade_idempotencia ON solicitacao_privacidade(usuario_id,idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX solicitacao_privacidade_usuario ON solicitacao_privacidade(usuario_id,created_at DESC,id);

CREATE TABLE politica_retencao (
    id uuid PRIMARY KEY,
    categoria varchar(40) NOT NULL,
    numero integer NOT NULL CHECK (numero > 0),
    finalidade_codigo varchar(80) NOT NULL,
    gatilho varchar(80) NOT NULL,
    prazo_dias integer CHECK (prazo_dias IS NULL OR prazo_dias >= 0),
    backup_prazo_dias integer CHECK (backup_prazo_dias IS NULL OR backup_prazo_dias >= 0),
    responsavel_id uuid REFERENCES usuario(id),
    base_validada boolean NOT NULL DEFAULT false,
    verificacao_descarte boolean NOT NULL DEFAULT false,
    validada_em timestamptz,
    desativada_em timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(categoria,numero),
    CHECK (desativada_em IS NULL OR (validada_em IS NOT NULL AND desativada_em > validada_em))
);
CREATE UNIQUE INDEX politica_retencao_ativa ON politica_retencao(categoria,finalidade_codigo,gatilho)
    WHERE validada_em IS NOT NULL AND desativada_em IS NULL AND prazo_dias IS NOT NULL AND responsavel_id IS NOT NULL AND base_validada AND verificacao_descarte;

CREATE TABLE execucao_expurgo (
    id uuid PRIMARY KEY,
    politica_id uuid NOT NULL REFERENCES politica_retencao(id),
    solicitacao_id uuid NOT NULL UNIQUE REFERENCES solicitacao_privacidade(id),
    alvo_referencia_cifrada bytea NOT NULL,
    alvo_referencia_hash bytea NOT NULL,
    estado varchar(16) NOT NULL CHECK (estado IN ('AUTORIZADA','EXECUTADA','VERIFICADA','FALHA')),
    autorizada_por uuid NOT NULL REFERENCES usuario(id),
    executada_por uuid REFERENCES usuario(id),
    verificada_por uuid REFERENCES usuario(id),
    solicitada_em timestamptz NOT NULL DEFAULT now(),
    executada_em timestamptz,
    verificada_em timestamptz,
    erro_codigo varchar(80),
    backup_residual_ate timestamptz
);
ALTER TABLE solicitacao_privacidade ADD CONSTRAINT solicitacao_privacidade_expurgo_fk FOREIGN KEY (expurgo_id) REFERENCES execucao_expurgo(id);

CREATE TABLE expurgo_alvo (
    id uuid PRIMARY KEY,
    execucao_id uuid NOT NULL REFERENCES execucao_expurgo(id),
    tipo varchar(20) NOT NULL CHECK (tipo IN ('BANCO','OBJETO','VERSAO','TEMPORARIO','FILA','CACHE','REFERENCIA','DISPOSITIVO','FORNECEDOR','BACKUP')),
    referencia_cifrada bytea NOT NULL,
    estado varchar(24) NOT NULL CHECK (estado IN ('PENDENTE','REMOVIDO','VERIFICADO','PRESERVADO','NAO_APLICAVEL','PROCEDIMENTO_PENDENTE','FALHA')),
    motivo_codigo varchar(80),
    tentativas integer NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    UNIQUE(execucao_id,tipo,referencia_cifrada)
);
CREATE INDEX expurgo_alvo_execucao_estado ON expurgo_alvo(execucao_id,estado,tipo);

CREATE TABLE tombstone_expurgo (
    id uuid PRIMARY KEY,
    execucao_id uuid NOT NULL REFERENCES execucao_expurgo(id),
    referencia_hash bytea NOT NULL,
    categoria varchar(40) NOT NULL,
    criado_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE(referencia_hash,categoria)
);

CREATE TABLE retencao_excecao (
    id uuid PRIMARY KEY,
    categoria varchar(40) NOT NULL,
    referencia_hash bytea NOT NULL,
    fundamento varchar(300) NOT NULL,
    escopo varchar(300) NOT NULL,
    responsavel_id uuid NOT NULL REFERENCES usuario(id),
    revisao_em timestamptz NOT NULL,
    ativa boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (length(trim(fundamento)) > 0 AND length(trim(escopo)) > 0)
);
CREATE INDEX retencao_excecao_ativa ON retencao_excecao(categoria,referencia_hash,revisao_em) WHERE ativa;

COMMENT ON TABLE retencao_excecao IS 'Conservacao excepcional; nao concede papel, sessao ou acesso ao dado conservado.';
COMMENT ON COLUMN politica_retencao.prazo_dias IS 'NULL significa politica incompleta e bloqueia o expurgo; nunca significa retencao infinita.';
