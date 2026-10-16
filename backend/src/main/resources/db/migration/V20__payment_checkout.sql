ALTER TABLE operacao_financeira ADD COLUMN checkout_url text;
ALTER TABLE operacao_financeira ADD COLUMN checkout_expires_at timestamptz;
ALTER TABLE operacao_financeira ADD COLUMN checkout_attempt integer NOT NULL DEFAULT 0 CHECK (checkout_attempt >= 0);
