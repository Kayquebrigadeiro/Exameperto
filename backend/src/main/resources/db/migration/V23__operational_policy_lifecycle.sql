ALTER TABLE tarifa
  ADD COLUMN IF NOT EXISTS versao integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS escopo varchar(120) NOT NULL DEFAULT 'GLOBAL',
  ADD COLUMN IF NOT EXISTS responsavel_id uuid REFERENCES usuario(id),
  ADD COLUMN IF NOT EXISTS publicada_em timestamptz,
  ADD COLUMN IF NOT EXISTS desativada_em timestamptz,
  ADD COLUMN IF NOT EXISTS usada_em timestamptz;

ALTER TABLE politica_cancelamento
  ADD COLUMN IF NOT EXISTS versao integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS escopo varchar(120) NOT NULL DEFAULT 'GLOBAL',
  ADD COLUMN IF NOT EXISTS responsavel_id uuid REFERENCES usuario(id),
  ADD COLUMN IF NOT EXISTS publicada_em timestamptz,
  ADD COLUMN IF NOT EXISTS desativada_em timestamptz,
  ADD COLUMN IF NOT EXISTS usada_em timestamptz;

ALTER TABLE politica_designacao
  ADD COLUMN IF NOT EXISTS versao integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS escopo varchar(120) NOT NULL DEFAULT 'GLOBAL',
  ADD COLUMN IF NOT EXISTS responsavel_id uuid REFERENCES usuario(id),
  ADD COLUMN IF NOT EXISTS publicada_em timestamptz,
  ADD COLUMN IF NOT EXISTS desativada_em timestamptz,
  ADD COLUMN IF NOT EXISTS usada_em timestamptz;

CREATE TABLE IF NOT EXISTS auditoria_politica_operacional (
  id uuid PRIMARY KEY,
  tipo varchar(32) NOT NULL,
  politica_id uuid NOT NULL,
  versao integer NOT NULL,
  escopo varchar(120) NOT NULL,
  acao varchar(32) NOT NULL,
  ator_id uuid NOT NULL REFERENCES usuario(id),
  detalhes jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS ix_auditoria_politica_operacional_lookup
  ON auditoria_politica_operacional(tipo, politica_id, created_at);

CREATE INDEX IF NOT EXISTS ix_tarifa_publicada_vigencia
  ON tarifa(escopo, inicio, fim) WHERE estado = 'ATIVA';
CREATE INDEX IF NOT EXISTS ix_cancelamento_publicado_vigencia
  ON politica_cancelamento(escopo, inicio, fim) WHERE estado = 'ATIVA';
CREATE INDEX IF NOT EXISTS ix_designacao_publicada_vigencia
  ON politica_designacao(escopo, inicio, fim) WHERE estado = 'ATIVA';
