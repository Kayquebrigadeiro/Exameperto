CREATE TABLE IF NOT EXISTS purge_completion (
    execution_id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    category varchar(40) NOT NULL CHECK (category='CONTA'),
    verified_at timestamptz NOT NULL,
    captured_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
REVOKE ALL ON purge_completion FROM PUBLIC;

CREATE TABLE IF NOT EXISTS account_closure_completion (
    execution_id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    email_cifrado bytea NOT NULL,
    email_busca bytea NOT NULL,
    nome_cifrado bytea NOT NULL,
    senha_hash text NOT NULL,
    cpf_cifrado bytea,
    cpf_busca bytea,
    nascimento date,
    identidade_estado varchar(20),
    completed_at timestamptz NOT NULL
);
REVOKE ALL ON account_closure_completion FROM PUBLIC;

CREATE TABLE IF NOT EXISTS account_closure_order (
    execution_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    order_id uuid NOT NULL,
    origem_cifrada bytea NOT NULL,
    destino_cifrada bytea NOT NULL,
    origem_lat numeric(9,6) NOT NULL,
    origem_lon numeric(9,6) NOT NULL,
    destino_lat numeric(9,6) NOT NULL,
    destino_lon numeric(9,6) NOT NULL,
    PRIMARY KEY (execution_id,order_id)
);
REVOKE ALL ON account_closure_order FROM PUBLIC;
