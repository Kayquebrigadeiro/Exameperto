ALTER TABLE ocorrencia_entrega DROP CONSTRAINT IF EXISTS ocorrencia_entrega_tipo_check;
ALTER TABLE ocorrencia_entrega ADD CONSTRAINT ocorrencia_entrega_tipo_check CHECK (tipo IN ('UNIDADE_FECHADA','DESTINATARIO_AUSENTE','ATRASO','LACRE_COMPROMETIDO','AVARIA','RECUSA','OUTRA'));
ALTER TABLE ocorrencia_entrega
  ADD COLUMN IF NOT EXISTS responsavel_id uuid REFERENCES usuario(id),
  ADD COLUMN IF NOT EXISTS decisao varchar(32),
  ADD COLUMN IF NOT EXISTS destino_tipo varchar(20),
  ADD COLUMN IF NOT EXISTS destino_unidade_id uuid REFERENCES unidade_retirada(id),
  ADD COLUMN IF NOT EXISTS instrucoes varchar(1000),
  ADD COLUMN IF NOT EXISTS cobertura_snapshot jsonb,
  ADD COLUMN IF NOT EXISTS apuracao jsonb,
  ADD COLUMN IF NOT EXISTS evidencia_referencia varchar(300),
  ADD COLUMN IF NOT EXISTS resolvida_em timestamptz;
ALTER TABLE custodia DROP CONSTRAINT IF EXISTS custodia_destino_tipo_check;
ALTER TABLE custodia ADD CONSTRAINT custodia_destino_tipo_check CHECK (destino_tipo IN ('ENTREGA','RETORNO'));
