CREATE TABLE prospectors (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    employee_code varchar(30) NOT NULL,
    phone         varchar(20),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT prospectors_user_id_uk UNIQUE (user_id),
    CONSTRAINT prospectors_employee_code_uk UNIQUE (employee_code)
);
