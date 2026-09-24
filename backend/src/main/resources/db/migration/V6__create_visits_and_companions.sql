CREATE TABLE visits (
    id             uuid        PRIMARY KEY,
    lead_id        uuid        NOT NULL REFERENCES leads (id) ON DELETE RESTRICT,
    -- Responsável no momento do agendamento; imutável (RN03, D-013).
    prospector_id  uuid        NOT NULL REFERENCES prospectors (id) ON DELETE RESTRICT,
    scheduled_date date        NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    notes          text,
    host_notes     text,
    cancelled_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT visits_status_ck CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'))
);

-- No máximo uma visita agendada por Lead (RN04, D-009).
CREATE UNIQUE INDEX visits_lead_scheduled_uk ON visits (lead_id) WHERE status = 'SCHEDULED';
CREATE INDEX visits_scheduled_date_status_ix ON visits (scheduled_date, status);
CREATE INDEX visits_prospector_ix ON visits (prospector_id);
CREATE INDEX visits_lead_ix ON visits (lead_id);

CREATE TABLE visit_companions (
    id           uuid         PRIMARY KEY,
    visit_id     uuid         NOT NULL REFERENCES visits (id) ON DELETE CASCADE,
    name         varchar(120) NOT NULL,
    cpf          char(11),
    birth_date   date         NOT NULL,
    relationship varchar(20)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT visit_companions_cpf_digits_ck CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT visit_companions_relationship_ck CHECK (relationship IN (
        'SPOUSE', 'CHILD', 'FATHER', 'MOTHER', 'SIBLING', 'GRANDPARENT', 'GRANDCHILD', 'FRIEND', 'OTHER'))
);

CREATE INDEX visit_companions_visit_ix ON visit_companions (visit_id);
