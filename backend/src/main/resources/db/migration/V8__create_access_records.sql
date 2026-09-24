CREATE TABLE access_records (
    id                   uuid        PRIMARY KEY,
    -- Nulo só quando o código não corresponde a nenhum convite (INVALID_CODE).
    invitation_id        uuid        REFERENCES invitations (id) ON DELETE RESTRICT,
    -- Valor normalizado recebido. Único lugar em que o código tentado é guardado (D-044).
    attempted_code       varchar(20) NOT NULL,
    validated_by_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    gate                 varchar(40) NOT NULL,
    result               varchar(20) NOT NULL,
    denial_reason        varchar(20),
    entry_at             timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT access_records_result_ck CHECK (result IN ('AUTHORIZED', 'DENIED')),
    CONSTRAINT access_records_denial_reason_ck CHECK (denial_reason IN (
        'INVALID_CODE', 'CANCELLED', 'ALREADY_USED', 'EXPIRED', 'WRONG_DATE')),
    CONSTRAINT access_records_consistency_ck CHECK (
        (result = 'AUTHORIZED' AND entry_at IS NOT NULL AND invitation_id IS NOT NULL AND denial_reason IS NULL)
        OR (result = 'DENIED' AND entry_at IS NULL AND denial_reason IS NOT NULL
            AND (invitation_id IS NOT NULL OR denial_reason = 'INVALID_CODE')))
);

-- Uma única entrada por convite: último seguro contra entrada dupla (RN11, D-089).
CREATE UNIQUE INDEX access_records_invitation_authorized_uk ON access_records (invitation_id) WHERE result = 'AUTHORIZED';
CREATE INDEX access_records_created_at_ix ON access_records (created_at);
CREATE INDEX access_records_invitation_ix ON access_records (invitation_id);

-- Acompanhantes que efetivamente entraram (D-021).
CREATE TABLE access_record_companions (
    access_record_id uuid NOT NULL REFERENCES access_records (id) ON DELETE CASCADE,
    companion_id     uuid NOT NULL REFERENCES visit_companions (id) ON DELETE RESTRICT,
    PRIMARY KEY (access_record_id, companion_id)
);
