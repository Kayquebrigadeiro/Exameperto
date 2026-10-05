CREATE TABLE mfa_totp (
    usuario_id uuid PRIMARY KEY REFERENCES usuario(id),
    segredo_cifrado bytea NOT NULL,
    confirmado_em timestamptz,
    criado_em timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    falhas smallint NOT NULL DEFAULT 0 CHECK (falhas >= 0),
    janela_falhas_em timestamptz,
    ultimo_passo bigint
);

ALTER TABLE sessao ADD COLUMN mfa_verificada_ate timestamptz;
CREATE INDEX sessao_mfa_ativa ON sessao(id, mfa_verificada_ate)
    WHERE revogada_em IS NULL AND mfa_verificada_ate IS NOT NULL;
