ALTER TABLE papel_global DROP CONSTRAINT papel_global_papel_check;
ALTER TABLE papel_global ADD CONSTRAINT papel_global_papel_check
    CHECK (papel IN ('ANALISTA_OPERACIONAL','GESTOR_FINANCEIRO','OPERADOR_PRIVACIDADE'));

CREATE TABLE auditoria_acesso_local (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    instituicao_id uuid REFERENCES instituicao(id),
    papel varchar(30) NOT NULL CHECK (papel IN ('ANALISTA_OPERACIONAL','GESTOR_FINANCEIRO','OPERADOR_PRIVACIDADE','ANALISTA_BENEFICIO')),
    escopo varchar(20) NOT NULL CHECK (escopo IN ('GLOBAL','INSTITUICAO')),
    acao varchar(12) NOT NULL CHECK (acao IN ('CONCEDIDO','REVOGADO')),
    motivo varchar(80) NOT NULL CHECK (motivo='AVALIACAO_LOCAL_NOMINAL'),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((escopo='GLOBAL' AND instituicao_id IS NULL) OR (escopo='INSTITUICAO' AND instituicao_id IS NOT NULL))
);
CREATE INDEX auditoria_acesso_local_usuario ON auditoria_acesso_local(usuario_id,created_at,id);

