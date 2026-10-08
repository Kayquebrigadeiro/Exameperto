CREATE TABLE encerramento_conta (
    usuario_id uuid PRIMARY KEY REFERENCES usuario(id),
    solicitacao_id uuid NOT NULL UNIQUE REFERENCES solicitacao_privacidade(id),
    estado varchar(24) NOT NULL CHECK (estado IN ('PENDENTE','BLOQUEADA','EM_EXECUCAO','ENCERRADA','FALHA')),
    bloqueio_codigo varchar(80),
    anonimizado_em timestamptz,
    encerrado_em timestamptz,
    detalhe_codigo varchar(80),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

COMMENT ON TABLE encerramento_conta IS 'Fechamento transacional da conta: revoga credenciais/concessoes e transforma identificadores sob regras explicitas; nao declara anonimização integral.';
