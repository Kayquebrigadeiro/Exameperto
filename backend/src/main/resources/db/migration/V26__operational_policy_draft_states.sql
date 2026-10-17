ALTER TABLE tarifa DROP CONSTRAINT IF EXISTS tarifa_estado_check;
ALTER TABLE tarifa ADD CONSTRAINT tarifa_estado_check CHECK (estado IN ('RASCUNHO','ATIVA','INATIVA'));

ALTER TABLE politica_cancelamento DROP CONSTRAINT IF EXISTS politica_cancelamento_estado_check;
ALTER TABLE politica_cancelamento ADD CONSTRAINT politica_cancelamento_estado_check CHECK (estado IN ('RASCUNHO','ATIVA','INATIVA'));

ALTER TABLE politica_designacao DROP CONSTRAINT IF EXISTS politica_designacao_estado_check;
ALTER TABLE politica_designacao ADD CONSTRAINT politica_designacao_estado_check CHECK (estado IN ('RASCUNHO','ATIVA','INATIVA'));
