CREATE TABLE leads (
    id            uuid         PRIMARY KEY,
    name          varchar(120) NOT NULL,
    cpf           char(11),
    phone         varchar(20),
    email         varchar(160),
    birth_date    date,
    notes         text,
    status        varchar(20)  NOT NULL DEFAULT 'NEW',
    prospector_id uuid         REFERENCES prospectors (id) ON DELETE RESTRICT,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT leads_cpf_digits_ck CHECK (cpf ~ '^[0-9]{11}$'),
    CONSTRAINT leads_status_ck CHECK (status IN ('NEW', 'CONTACTED', 'VISIT_SCHEDULED', 'VISITED', 'CANCELLED'))
);

-- CPF opcional e único quando informado (D-012).
CREATE UNIQUE INDEX leads_cpf_uk ON leads (cpf) WHERE cpf IS NOT NULL;
CREATE INDEX leads_prospector_status_ix ON leads (prospector_id, status);
CREATE INDEX leads_created_at_ix ON leads (created_at);
