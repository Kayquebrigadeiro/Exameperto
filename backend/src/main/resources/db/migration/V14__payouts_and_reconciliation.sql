ALTER TABLE operacao_financeira DROP CONSTRAINT operacao_financeira_tipo_check;
ALTER TABLE operacao_financeira ADD CONSTRAINT operacao_financeira_tipo_check
    CHECK (tipo IN ('COBRANCA','RESERVA','LIBERACAO','LIQUIDACAO','REPASSE','ESTORNO'));
ALTER TABLE operacao_financeira ADD COLUMN destinatario_usuario_id uuid REFERENCES usuario(id);
ALTER TABLE operacao_financeira ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE financeiro_outbox DROP CONSTRAINT financeiro_outbox_tipo_check;
ALTER TABLE financeiro_outbox ADD CONSTRAINT financeiro_outbox_tipo_check
    CHECK (tipo IN ('CRIAR_COBRANCA','RECONCILIAR_COBRANCA','SOLICITAR_REPASSE','RECONCILIAR_REPASSE'));

CREATE TABLE apuracao_remuneracao (
    id uuid PRIMARY KEY,
    pedido_id uuid NOT NULL UNIQUE REFERENCES pedido(id),
    designacao_id uuid NOT NULL REFERENCES designacao(id),
    orcamento_id uuid NOT NULL REFERENCES orcamento(id),
    modalidade varchar(30) NOT NULL CHECK (modalidade IN ('SERVICO_COMPLETO','CANCELAMENTO','OCORRENCIA','SERVICO_PARCIAL')),
    estado varchar(20) NOT NULL CHECK (estado IN ('BLOQUEADA','CONCLUIDA')),
    regra_codigo varchar(80),
    valor_devido numeric(19,2),
    paciente_valor numeric(19,2),
    subsidio_valor numeric(19,2),
    moeda char(3),
    concluida_em timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((estado='BLOQUEADA' AND valor_devido IS NULL AND paciente_valor IS NULL AND subsidio_valor IS NULL AND concluida_em IS NULL)
        OR (estado='CONCLUIDA' AND regra_codigo IS NOT NULL AND valor_devido>0 AND paciente_valor>=0 AND subsidio_valor>=0
            AND paciente_valor+subsidio_valor=valor_devido AND moeda='BRL' AND concluida_em IS NOT NULL)),
    CHECK (modalidade='SERVICO_COMPLETO' OR estado='BLOQUEADA')
);

CREATE TABLE lancamento_financeiro (
    operacao_id uuid NOT NULL REFERENCES operacao_financeira(id),
    sequencia smallint NOT NULL CHECK (sequencia>0),
    conta_codigo varchar(40) NOT NULL CHECK (conta_codigo IN ('PACIENTE_A_PAGAR','SUBSIDIO_A_PAGAR','ENTREGADOR_A_PAGAR','CAIXA_REPASSE')),
    sentido varchar(7) NOT NULL CHECK (sentido IN ('DEBITO','CREDITO')),
    valor numeric(19,2) NOT NULL CHECK (valor>0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    pedido_id uuid NOT NULL REFERENCES pedido(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (operacao_id,sequencia)
);

CREATE FUNCTION validar_lancamentos_balanceados() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE op uuid; deb numeric; cred numeric;
BEGIN
    op := COALESCE(NEW.operacao_id,OLD.operacao_id);
    SELECT COALESCE(sum(valor) FILTER (WHERE sentido='DEBITO'),0), COALESCE(sum(valor) FILTER (WHERE sentido='CREDITO'),0)
      INTO deb,cred FROM lancamento_financeiro WHERE operacao_id=op;
    IF deb <> cred THEN RAISE EXCEPTION 'financial entries are not balanced'; END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER lancamentos_balanceados AFTER INSERT OR UPDATE OR DELETE ON lancamento_financeiro
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validar_lancamentos_balanceados();

CREATE TABLE repasse (
    id uuid PRIMARY KEY,
    obrigacao_operacao_id uuid NOT NULL UNIQUE REFERENCES operacao_financeira(id),
    operacao_id uuid UNIQUE REFERENCES operacao_financeira(id),
    pedido_id uuid NOT NULL UNIQUE REFERENCES pedido(id),
    entregador_id uuid NOT NULL REFERENCES entregador(id),
    instituicao_id uuid REFERENCES instituicao(id),
    referencia varchar(160) NOT NULL UNIQUE,
    valor numeric(19,2) NOT NULL CHECK (valor>0),
    moeda char(3) NOT NULL CHECK (moeda='BRL'),
    destinatario_referencia varchar(160) NOT NULL,
    estado varchar(30) NOT NULL CHECK (estado IN ('OBRIGACAO_REGISTRADA','SOLICITADO','CONFIRMADO','FALHOU','INCERTO','DIVERGENTE')),
    divergencia_codigo varchar(60),
    solicitado_em timestamptz,
    confirmado_em timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((estado='OBRIGACAO_REGISTRADA' AND operacao_id IS NULL AND solicitado_em IS NULL)
        OR (estado<>'OBRIGACAO_REGISTRADA' AND operacao_id IS NOT NULL AND solicitado_em IS NOT NULL)),
    CHECK ((estado='CONFIRMADO')=(confirmado_em IS NOT NULL))
);
CREATE INDEX repasse_instituicao_estado ON repasse(instituicao_id,estado,created_at,id);
CREATE INDEX repasse_entregador ON repasse(entregador_id,created_at,id);

CREATE TABLE evento_repasse (
    id uuid PRIMARY KEY,
    repasse_id uuid NOT NULL REFERENCES repasse(id),
    provedor varchar(80) NOT NULL,
    evento_externo_id varchar(160) NOT NULL,
    payload_hash bytea NOT NULL,
    referencia varchar(160) NOT NULL,
    resultado varchar(30) NOT NULL CHECK (resultado IN ('CONFIRMADO','FALHOU','INCERTO','DIVERGENTE','IGNORADO_FORA_ORDEM')),
    valor numeric(19,2),
    moeda char(3),
    destinatario_referencia varchar(160),
    ocorrido_em timestamptz NOT NULL,
    processado_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provedor,evento_externo_id)
);

CREATE TABLE repasse_idempotencia (
    ator_id uuid NOT NULL REFERENCES usuario(id),
    operacao varchar(80) NOT NULL,
    chave varchar(128) NOT NULL,
    request_hash bytea NOT NULL,
    repasse_id uuid NOT NULL REFERENCES repasse(id),
    PRIMARY KEY (ator_id,operacao,chave)
);

CREATE TABLE auditoria_repasse (
    id uuid PRIMARY KEY,
    repasse_id uuid NOT NULL REFERENCES repasse(id),
    ator_id uuid REFERENCES usuario(id),
    acao varchar(50) NOT NULL,
    estado_anterior varchar(30),
    estado_novo varchar(30) NOT NULL,
    motivo_codigo varchar(60),
    correlacao_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_repasse_alvo ON auditoria_repasse(repasse_id,created_at,id);

CREATE FUNCTION impedir_mutacao_evento_repasse() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'evento_repasse is append-only'; END $$;
CREATE TRIGGER evento_repasse_imutavel BEFORE UPDATE OR DELETE ON evento_repasse FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_evento_repasse();
CREATE TRIGGER auditoria_repasse_imutavel BEFORE UPDATE OR DELETE ON auditoria_repasse FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_aceite();
