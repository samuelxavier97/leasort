CREATE TABLE users (
    id                   uuid         PRIMARY KEY,
    name                 varchar(120) NOT NULL,
    email                varchar(160) NOT NULL,
    password_hash        varchar(100) NOT NULL,
    role                 varchar(20)  NOT NULL,
    active               boolean      NOT NULL DEFAULT true,
    must_change_password boolean      NOT NULL DEFAULT false,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_email_lowercase_ck CHECK (email = lower(email)),
    CONSTRAINT users_role_ck CHECK (role IN ('ADMIN', 'PROSPECTOR', 'GATE', 'HOST'))
);
