CREATE TABLE IF NOT EXISTS purge_completion (
    execution_id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    category varchar(40) NOT NULL CHECK (category='CONTA'),
    verified_at timestamptz NOT NULL,
    captured_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
REVOKE ALL ON purge_completion FROM PUBLIC;
