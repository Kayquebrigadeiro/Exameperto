ALTER TABLE ocorrencia_entrega
  ADD COLUMN IF NOT EXISTS executada_em timestamptz,
  ADD COLUMN IF NOT EXISTS execucao_evidencia_id uuid REFERENCES documento(id);
ALTER TABLE evento_custodia DROP CONSTRAINT IF EXISTS evento_custodia_tipo_check;
ALTER TABLE evento_custodia ADD CONSTRAINT evento_custodia_tipo_check CHECK (tipo IN ('RETIRADA','INICIO_ENTREGA','ENTREGA_COMPROVADA','OCORRENCIA','RESOLUCAO_EXECUTADA','RETORNO_ENCERRADO'));
