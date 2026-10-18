DO $$ DECLARE c record; BEGIN
  FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='apuracao_remuneracao'::regclass AND contype='c' LOOP
    EXECUTE format('ALTER TABLE apuracao_remuneracao DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE apuracao_remuneracao
  ADD COLUMN IF NOT EXISTS ocorrencia_id uuid REFERENCES ocorrencia_entrega(id),
  ADD COLUMN IF NOT EXISTS solicitacao_cancelamento_id uuid REFERENCES solicitacao_cancelamento(id),
  ADD COLUMN IF NOT EXISTS responsavel_id uuid REFERENCES usuario(id),
  ADD COLUMN IF NOT EXISTS revisor_id uuid REFERENCES usuario(id),
  ADD COLUMN IF NOT EXISTS motivo varchar(500),
  ADD COLUMN IF NOT EXISTS evidencias jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS proposta jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS valor_entregador numeric(19,2),
  ADD COLUMN IF NOT EXISTS valor_paciente numeric(19,2),
  ADD COLUMN IF NOT EXISTS valor_instituicao numeric(19,2),
  ADD COLUMN IF NOT EXISTS saldo_liberar numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS saldo_devolver numeric(19,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS versao bigint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT clock_timestamp();

ALTER TABLE apuracao_remuneracao
  ADD CONSTRAINT apuracao_remuneracao_modalidade_check CHECK (modalidade IN ('SERVICO_COMPLETO','CANCELAMENTO','OCORRENCIA','SERVICO_PARCIAL')),
  ADD CONSTRAINT apuracao_remuneracao_estado_check CHECK (estado IN ('BLOQUEADA','PENDENTE_REVISAO','COMPLEMENTO_SOLICITADO','REJEITADA','APROVADA','CONCLUIDA')),
  ADD CONSTRAINT apuracao_remuneracao_amounts_check CHECK (
    (valor_entregador IS NULL OR valor_entregador >= 0) AND
    (valor_paciente IS NULL OR valor_paciente >= 0) AND
    (valor_instituicao IS NULL OR valor_instituicao >= 0) AND
    saldo_liberar >= 0 AND saldo_devolver >= 0
  ),
  ADD CONSTRAINT apuracao_remuneracao_manual_check CHECK (
    modalidade='SERVICO_COMPLETO' OR
    (responsavel_id IS NOT NULL AND motivo IS NOT NULL AND jsonb_array_length(evidencias) > 0)
  );

CREATE INDEX apuracao_remuneracao_estado ON apuracao_remuneracao(estado,created_at,id);
CREATE INDEX apuracao_remuneracao_pedido ON apuracao_remuneracao(pedido_id,updated_at,id);

CREATE TABLE apuracao_idempotencia (
  ator_id uuid NOT NULL REFERENCES usuario(id),
  operacao varchar(60) NOT NULL,
  chave varchar(128) NOT NULL,
  request_hash bytea NOT NULL,
  apuracao_id uuid NOT NULL REFERENCES apuracao_remuneracao(id),
  PRIMARY KEY (ator_id,operacao,chave)
);

CREATE TABLE auditoria_apuracao (
  id uuid PRIMARY KEY,
  apuracao_id uuid NOT NULL REFERENCES apuracao_remuneracao(id),
  ator_id uuid REFERENCES usuario(id),
  acao varchar(50) NOT NULL,
  estado_anterior varchar(30),
  estado_novo varchar(30) NOT NULL,
  detalhe jsonb NOT NULL DEFAULT '{}'::jsonb,
  correlacao_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX auditoria_apuracao_lookup ON auditoria_apuracao(apuracao_id,created_at,id);
CREATE FUNCTION impedir_mutacao_auditoria_apuracao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'auditoria_apuracao is append-only'; END $$;
CREATE TRIGGER auditoria_apuracao_imutavel BEFORE UPDATE OR DELETE ON auditoria_apuracao
FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_auditoria_apuracao();
