-- Motivo do cancelamento da visita (D-098): estado do domínio, não derivado da auditoria. A remarcação cancela a
-- visita antiga (D-076) e não conta como cancelamento no dashboard nem na exportação.
ALTER TABLE visits ADD COLUMN cancel_reason varchar(20);

-- Visitas canceladas antes desta migration: a remarcação é reconhecida pela auditoria VISIT_RESCHEDULED gravada
-- na visita antiga; as demais ficam como cancelamento pelo usuário.
UPDATE visits v
SET cancel_reason = 'RESCHEDULED'
WHERE v.status = 'CANCELLED'
  AND EXISTS (SELECT 1 FROM audit_logs a
              WHERE a.entity_type = 'VISIT' AND a.entity_id = v.id AND a.action = 'VISIT_RESCHEDULED');

UPDATE visits
SET cancel_reason = 'CANCELLED_BY_USER'
WHERE status = 'CANCELLED' AND cancel_reason IS NULL;

ALTER TABLE visits
    ADD CONSTRAINT visits_cancel_reason_ck
        CHECK (cancel_reason IN ('RESCHEDULED', 'CANCELLED_BY_USER', 'LEAD_DISCARDED')),
    ADD CONSTRAINT visits_cancel_reason_status_ck
        CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL));
