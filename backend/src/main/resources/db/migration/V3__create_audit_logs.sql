CREATE TABLE audit_logs (
    id          uuid        PRIMARY KEY,
    user_id     uuid        REFERENCES users (id) ON DELETE RESTRICT,
    action      varchar(40) NOT NULL,
    entity_type varchar(40),
    entity_id   uuid,
    metadata    jsonb,
    ip_address  varchar(45),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT audit_logs_action_ck CHECK (action IN (
        'LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'USER_CREATED', 'USER_UPDATED', 'USER_STATUS_CHANGED',
        'LEAD_CREATED', 'LEAD_UPDATED', 'LEAD_STATUS_CHANGED', 'LEAD_ASSIGNED', 'LEAD_IMPORTED',
        'VISIT_CREATED', 'VISIT_UPDATED', 'VISIT_RESCHEDULED', 'VISIT_CANCELLED', 'VISIT_NO_SHOW',
        'INVITATION_CREATED', 'INVITATION_REISSUED', 'INVITATION_CANCELLED', 'INVITATION_EXPIRED',
        'ACCESS_VALIDATED', 'ACCESS_DENIED',
        'EXPORT_GENERATED'
    ))
);

CREATE INDEX audit_logs_created_at_ix ON audit_logs (created_at);
CREATE INDEX audit_logs_entity_ix ON audit_logs (entity_type, entity_id);
CREATE INDEX audit_logs_user_id_ix ON audit_logs (user_id);

-- Auditoria somente de inserção (D-028). A aplicação roda sem ownership da tabela em produção (D-057).
CREATE FUNCTION audit_logs_block_changes() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only (%)', TG_OP;
END
$$;

CREATE TRIGGER audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_block_changes();

CREATE TRIGGER audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_block_changes();
