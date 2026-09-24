package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.leads.Lead;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * I6: visita e convite estão na mesma transação (RN06, D-071, D-081). Triggers temporários, criados e
 * removidos pelo próprio teste, fazem a transação falhar em pontos escolhidos.
 */
class InvitationRollbackTest extends InvitationTestSupport {

    @BeforeEach
    void createFailingFunction() {
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION test_forced_failure() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'falha forçada pelo teste';
                END
                $$ LANGUAGE plpgsql
                """).update();
    }

    @AfterEach
    void dropTriggers() {
        jdbc.sql("DROP TRIGGER IF EXISTS test_fail_invitation_insert ON invitations").update();
        jdbc.sql("DROP TRIGGER IF EXISTS test_fail_audit_insert ON audit_logs").update();
        jdbc.sql("DROP FUNCTION IF EXISTS test_forced_failure()").update();
    }

    @Test
    void failureInsertingTheInvitationRollsBackTheVisit() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        long audits = auditCount();
        jdbc.sql("""
                CREATE TRIGGER test_fail_invitation_insert BEFORE INSERT ON invitations
                FOR EACH ROW EXECUTE FUNCTION test_forced_failure()
                """).update();

        schedule(me.client(), lead.getId(), calendar.today().plusDays(2)).andExpect(status().isInternalServerError());

        assertNothingScheduled(lead, audits);
    }

    @Test
    void failureAfterTheInvitationWasInsertedRollsBackBoth() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        long audits = auditCount();
        // Último passo da criação: auditoria da mudança de status do Lead, depois do convite gravado.
        jdbc.sql("""
                CREATE TRIGGER test_fail_audit_insert BEFORE INSERT ON audit_logs
                FOR EACH ROW WHEN (NEW.action = 'LEAD_STATUS_CHANGED' AND NEW.entity_id = '%s')
                EXECUTE FUNCTION test_forced_failure()
                """.formatted(lead.getId())).update();

        schedule(me.client(), lead.getId(), calendar.today().plusDays(2)).andExpect(status().isInternalServerError());

        assertNothingScheduled(lead, audits);
    }

    @Test
    void failureWhileReschedulingKeepsTheOldVisitAndInvitation() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        UUID old = scheduledVisit(me.client(), lead.getId(), calendar.today().plusDays(2));
        InvitationRow invitation = activeOf(old);
        // Último passo da remarcação: auditoria da nova visita, depois do novo convite gravado.
        jdbc.sql("""
                CREATE TRIGGER test_fail_audit_insert BEFORE INSERT ON audit_logs
                FOR EACH ROW WHEN (NEW.action = 'VISIT_CREATED' AND NEW.metadata ->> 'rescheduledFrom' = '%s')
                EXECUTE FUNCTION test_forced_failure()
                """.formatted(old)).update();

        me.client().post("/api/visits/" + old + "/reschedule", Map.of("scheduledDate", calendar.today().plusDays(4).toString()))
                .andExpect(status().isInternalServerError());

        assertThat(visitStatus(old)).isEqualTo("SCHEDULED");
        assertThat(invitationsOf(old)).containsExactly(invitation);
        assertThat(jdbc.sql("SELECT count(*) FROM visits WHERE lead_id = :id").param("id", lead.getId())
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM invitations i JOIN visits v ON v.id = i.visit_id WHERE v.lead_id = :id
                        """).param("id", lead.getId()).query(Long.class).single()).isEqualTo(1);
        assertThat(leadStatus(lead.getId())).isEqualTo("VISIT_SCHEDULED");
    }

    private void assertNothingScheduled(Lead lead, long auditsBefore) {
        assertThat(jdbc.sql("SELECT count(*) FROM visits WHERE lead_id = :id").param("id", lead.getId())
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM invitations i JOIN visits v ON v.id = i.visit_id WHERE v.lead_id = :id
                        """).param("id", lead.getId()).query(Long.class).single()).isZero();
        assertThat(leadStatus(lead.getId())).isEqualTo("NEW");
        assertThat(auditCount()).isEqualTo(auditsBefore);
    }

    private long auditCount() {
        return jdbc.sql("SELECT count(*) FROM audit_logs").query(Long.class).single();
    }
}
