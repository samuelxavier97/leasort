CREATE TABLE invitations (
    id           uuid        PRIMARY KEY,
    visit_id     uuid        NOT NULL REFERENCES visits (id) ON DELETE RESTRICT,
    -- Crockford Base32 sem hífen, em claro (D-001, D-003). Nunca vai para log nem auditoria.
    code         char(10)    NOT NULL,
    status       varchar(20) NOT NULL DEFAULT 'ACTIVE',
    -- Primeiro instante do dia seguinte à visita em APP_TIMEZONE, limite exclusivo (D-085).
    expires_at   timestamptz NOT NULL,
    used_at      timestamptz,
    cancelled_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT invitations_code_uk UNIQUE (code),
    CONSTRAINT invitations_code_ck CHECK (code ~ '^[0-9A-HJKMNP-TV-Z]{10}$'),
    CONSTRAINT invitations_status_ck CHECK (status IN ('ACTIVE', 'USED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT invitations_cancelled_at_ck CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

-- No máximo um convite ativo por visita (§8.6). Visitas criadas antes desta fase ficam sem convite (D-071).
CREATE UNIQUE INDEX invitations_visit_active_uk ON invitations (visit_id) WHERE status = 'ACTIVE';
CREATE INDEX invitations_visit_ix ON invitations (visit_id);
CREATE INDEX invitations_status_expires_ix ON invitations (status, expires_at);
