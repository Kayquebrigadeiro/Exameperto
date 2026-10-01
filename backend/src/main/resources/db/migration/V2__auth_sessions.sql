ALTER TABLE sessao ADD COLUMN access_hash bytea;
ALTER TABLE sessao ADD COLUMN access_expira_em timestamptz;
CREATE UNIQUE INDEX sessao_access_hash ON sessao(access_hash) WHERE access_hash IS NOT NULL;
CREATE INDEX desafio_tipo_expiracao ON desafio_conta(tipo, expira_em, consumido_em);
CREATE INDEX sessao_familia ON sessao(familia_id);
ALTER TABLE sessao ADD CONSTRAINT sessao_access_pair CHECK ((access_hash IS NULL) = (access_expira_em IS NULL));
