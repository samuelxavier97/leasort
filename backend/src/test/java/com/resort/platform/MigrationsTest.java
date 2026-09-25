package com.resort.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resort.platform.users.Role;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class MigrationsTest extends IntegrationTestSupport {

    @Autowired
    Flyway flyway;

    @Autowired
    javax.sql.DataSource dataSource;

    @Test
    void appliesMigrationsAndCreatesTables() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .extracting(Object::toString)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                        """).query(String.class).list())
                .containsExactlyInAnyOrder(
                        "users", "prospectors", "audit_logs", "spring_session", "spring_session_attributes", "leads", "visits", "visit_companions", "invitations",
                        "access_records", "access_record_companions");
    }

    @Test
    void usersRejectsUppercaseEmail() {
        assertThatThrownBy(() -> insertUser(TestSequence.next("Maiuscula-") + "@test.local", "GATE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_email_lowercase_ck");
    }

    @Test
    void usersRejectsUnknownRole() {
        assertThatThrownBy(() -> insertUser(TestData.uniqueEmail("role"), "OWNER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_role_ck");
    }

    @Test
    void leadsRejectsMalformedCpfAndUnknownStatus() {
        assertThatThrownBy(() -> insertLead("1234567890a", "NEW"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("leads_cpf_digits_ck");
        assertThatThrownBy(() -> insertLead("1234567890", "NEW"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLead(null, "CONVERTED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("leads_status_ck");
    }

    @Test
    void leadsCpfIsUniqueOnlyWhenPresent() {
        insertLead(null, "NEW");
        insertLead(null, "NEW");
        String cpf = FakeCpf.generate();
        insertLead(cpf, "NEW");

        assertThatThrownBy(() -> insertLead(cpf, "NEW"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("leads_cpf_uk");
    }

    @Test
    void visitsKeepOneScheduledPerLeadAndCascadeCompanions() {
        UUID lead = UUID.randomUUID();
        jdbc.sql("INSERT INTO leads (id, name, status) VALUES (:id, 'Lead Fictício', 'VISIT_SCHEDULED')").param("id", lead).update();
        UUID prospector = testData.prospectorOf(testData.user(com.resort.platform.users.Role.PROSPECTOR)).getId();
        UUID scheduled = insertVisit(lead, prospector, "SCHEDULED");
        insertVisit(lead, prospector, "CANCELLED");

        assertThatThrownBy(() -> insertVisit(lead, prospector, "SCHEDULED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visits_lead_scheduled_uk");
        assertThatThrownBy(() -> insertVisit(lead, prospector, "CONFIRMED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visits_status_ck");
        assertThatThrownBy(() -> insertCompanion(scheduled, "1234567890a", "CHILD"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visit_companions_cpf_digits_ck");
        assertThatThrownBy(() -> insertCompanion(scheduled, null, "COUSIN"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visit_companions_relationship_ck");

        insertCompanion(scheduled, null, "SPOUSE");
        jdbc.sql("DELETE FROM visits WHERE id = :id").param("id", scheduled).update();
        assertThat(jdbc.sql("SELECT count(*) FROM visit_companions WHERE visit_id = :id").param("id", scheduled)
                .query(Long.class).single()).isZero();
    }

    /** I1: V7 (§8.6, D-001, D-003). */
    @Test
    void invitationsEnforceCodeStatusAndOneActivePerVisit() {
        UUID lead = UUID.randomUUID();
        jdbc.sql("INSERT INTO leads (id, name, status) VALUES (:id, 'Lead Fictício', 'VISIT_SCHEDULED')").param("id", lead).update();
        UUID prospector = testData.prospectorOf(testData.user(com.resort.platform.users.Role.PROSPECTOR)).getId();
        UUID visit = insertVisit(lead, prospector, "SCHEDULED");
        String code = TestCodes.unique();

        insertInvitation(visit, code, "ACTIVE", false);
        insertInvitation(visit, TestCodes.unique(), "CANCELLED", true);

        assertThatThrownBy(() -> insertInvitation(visit, TestCodes.unique(), "ACTIVE", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("invitations_visit_active_uk");
        assertThatThrownBy(() -> insertInvitation(insertVisit(lead, prospector, "CANCELLED"), code, "CANCELLED", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("invitations_code_uk");
        for (String invalid : new String[] {"ABCDEFGHI", "ABCDEFGHIJ", "ABCDEFGHJL", "ABCDEFGHJO", "ABCDEFGHJU", "abcdefghjk", "ABCDE-FGHJ"}) {
            assertThatThrownBy(() -> insertInvitation(visit, invalid, "CANCELLED", true))
                    .as(invalid)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("invitations_code_ck");
        }
        assertThatThrownBy(() -> insertInvitation(visit, TestCodes.unique(), "PENDING", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("invitations_status_ck");
        assertThatThrownBy(() -> insertInvitation(visit, TestCodes.unique(), "CANCELLED", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("invitations_cancelled_at_ck");
        assertThatThrownBy(() -> insertInvitation(visit, TestCodes.unique(), "EXPIRED", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("invitations_cancelled_at_ck");
    }

    /**
     * I31: a V7 roda sobre um banco com visitas da Fase 4, que ficam sem convite (D-071). Usa um schema
     * separado no mesmo PostgreSQL, migrado até a V6, populado e então migrado até o fim.
     */
    @Test
    void v7RunsOverPhase4DataLeavingOldVisitsWithoutInvitation() {
        String schema = "fase4_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration")
                    .target("6").load().migrate();
            UUID user = UUID.randomUUID();
            UUID prospector = UUID.randomUUID();
            UUID lead = UUID.randomUUID();
            UUID visit = UUID.randomUUID();
            jdbc.sql("INSERT INTO %s.users (id, name, email, password_hash, role) VALUES (:id, 'P', :email, 'hash', 'PROSPECTOR')"
                    .formatted(schema)).param("id", user).param("email", TestData.uniqueEmail("fase4")).update();
            jdbc.sql("INSERT INTO %s.prospectors (id, user_id, employee_code) VALUES (:id, :user, 'F4-001')".formatted(schema))
                    .param("id", prospector).param("user", user).update();
            jdbc.sql("INSERT INTO %s.leads (id, name, status, prospector_id) VALUES (:id, 'Lead Fictício', 'VISIT_SCHEDULED', :p)"
                    .formatted(schema)).param("id", lead).param("p", prospector).update();
            jdbc.sql("""
                            INSERT INTO %s.visits (id, lead_id, prospector_id, scheduled_date, status)
                            VALUES (:id, :lead, :p, current_date + 1, 'SCHEDULED')
                            """.formatted(schema))
                    .param("id", visit).param("lead", lead).param("p", prospector).update();

            Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.sql("SELECT status FROM %s.visits WHERE id = :id".formatted(schema)).param("id", visit)
                    .query(String.class).single()).isEqualTo("SCHEDULED");
            assertThat(jdbc.sql("SELECT count(*) FROM %s.invitations".formatted(schema)).query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS " + schema + " CASCADE").update();
        }
    }

    /** G1: V8 (§8.7, §8.8, D-089). */
    @Test
    void accessRecordsEnforceConsistencyAndOneEntryPerInvitation() {
        UUID lead = UUID.randomUUID();
        jdbc.sql("INSERT INTO leads (id, name, status) VALUES (:id, 'Lead Fictício', 'VISIT_SCHEDULED')").param("id", lead).update();
        UUID prospector = testData.prospectorOf(testData.user(com.resort.platform.users.Role.PROSPECTOR)).getId();
        UUID visit = insertVisit(lead, prospector, "SCHEDULED");
        UUID invitation = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO invitations (id, visit_id, code, status, expires_at)
                        VALUES (:id, :visit, :code, 'ACTIVE', now() + interval '1 day')
                        """).param("id", invitation).param("visit", visit).param("code", TestCodes.unique()).update();
        UUID gate = testData.user(com.resort.platform.users.Role.GATE).getId();

        insertAccess(invitation, gate, "DENIED", "WRONG_DATE", false);
        insertAccess(null, gate, "DENIED", "INVALID_CODE", false);
        insertAccess(invitation, gate, "AUTHORIZED", null, true);

        assertThatThrownBy(() -> insertAccess(invitation, gate, "AUTHORIZED", null, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("access_records_invitation_authorized_uk");
        assertThatThrownBy(() -> insertAccess(invitation, gate, "AUTHORIZED", null, false))
                .as("AUTHORIZED sem entry_at")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("access_records_consistency_ck");
        assertThatThrownBy(() -> insertAccess(invitation, gate, "DENIED", null, false))
                .as("DENIED sem motivo")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("access_records_consistency_ck");
        assertThatThrownBy(() -> insertAccess(invitation, gate, "DENIED", "WRONG_DATE", true))
                .as("DENIED com entry_at")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("access_records_consistency_ck");
        assertThatThrownBy(() -> insertAccess(null, gate, "DENIED", "EXPIRED", false))
                .as("sem convite só com INVALID_CODE")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("access_records_consistency_ck");
        // Resultado fora da lista viola as duas CHECKs; o PostgreSQL relata a primeira pela ordem do nome.
        assertThatThrownBy(() -> insertAccess(invitation, gate, "PENDING", null, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageMatching("(?s).*access_records_(consistency|result)_ck.*");
        assertThatThrownBy(() -> insertAccess(invitation, gate, "DENIED", "LATE", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("access_records_denial_reason_ck");
    }

    /** V10 (D-098): motivo válido, presente se e somente se a visita está CANCELLED. */
    @Test
    void visitsRejectIncoherentCancelReason() {
        UUID prospector = testData.prospectorOf(testData.user(Role.PROSPECTOR)).getId();
        UUID lead = testData.lead(null).getId();
        assertThatThrownBy(() -> insertVisit(lead, prospector, "CANCELLED", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visits_cancel_reason_status_ck");
        assertThatThrownBy(() -> insertVisit(lead, prospector, "SCHEDULED", "CANCELLED_BY_USER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visits_cancel_reason_status_ck");
        assertThatThrownBy(() -> insertVisit(lead, prospector, "CANCELLED", "OTHER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visits_cancel_reason_ck");
        for (String reason : List.of("RESCHEDULED", "CANCELLED_BY_USER", "LEAD_DISCARDED")) {
            insertVisit(lead, prospector, "CANCELLED", reason);
        }
    }

    /** H1: V9, índice parcial das chegadas por entry_at (D-095). */
    @Test
    void arrivalsIndexIsPartialOnAuthorizedEntries() {
        String definition = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = 'access_records_entry_at_ix'")
                .query(String.class).single();
        assertThat(definition).contains("(entry_at)").contains("WHERE").contains("'AUTHORIZED'");
    }

    private void insertAccess(UUID invitation, UUID user, String result, String reason, boolean entered) {
        jdbc.sql("""
                        INSERT INTO access_records (id, invitation_id, attempted_code, validated_by_user_id, gate, result,
                                                    denial_reason, entry_at)
                        VALUES (:id, :invitation, 'X', :user, 'PRINCIPAL', :result, :reason, CASE WHEN :entered THEN now() END)
                        """)
                .param("id", UUID.randomUUID()).param("invitation", invitation).param("user", user)
                .param("result", result).param("reason", reason).param("entered", entered).update();
    }

    private void insertInvitation(UUID visit, String code, String status, boolean cancelled) {
        jdbc.sql("""
                        INSERT INTO invitations (id, visit_id, code, status, expires_at, cancelled_at)
                        VALUES (:id, :visit, :code, :status, now() + interval '1 day', CASE WHEN :cancelled THEN now() END)
                        """)
                .param("id", UUID.randomUUID()).param("visit", visit).param("code", code).param("status", status)
                .param("cancelled", cancelled).update();
    }

    private UUID insertVisit(UUID lead, UUID prospector, String status) {
        return insertVisit(lead, prospector, status, "CANCELLED".equals(status) ? "CANCELLED_BY_USER" : null);
    }

    private UUID insertVisit(UUID lead, UUID prospector, String status, String cancelReason) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO visits (id, lead_id, prospector_id, scheduled_date, status, cancel_reason, cancelled_at)
                        VALUES (:id, :lead, :prospector, current_date, :status, :reason,
                                CASE WHEN :status = 'CANCELLED' THEN now() END)
                        """)
                .param("id", id).param("lead", lead).param("prospector", prospector).param("status", status)
                .param("reason", cancelReason).update();
        return id;
    }

    private void insertCompanion(UUID visit, String cpf, String relationship) {
        jdbc.sql("""
                        INSERT INTO visit_companions (id, visit_id, name, cpf, birth_date, relationship)
                        VALUES (:id, :visit, 'Acompanhante Fictício', :cpf, DATE '2010-01-01', :relationship)
                        """)
                .param("id", UUID.randomUUID()).param("visit", visit).param("cpf", cpf).param("relationship", relationship)
                .update();
    }

    private void insertLead(String cpf, String status) {
        jdbc.sql("INSERT INTO leads (id, name, cpf, status) VALUES (:id, 'Lead Fictício', :cpf, :status)")
                .param("id", UUID.randomUUID())
                .param("cpf", cpf)
                .param("status", status)
                .update();
    }

    private void insertUser(String email, String role) {
        jdbc.sql("INSERT INTO users (id, name, email, password_hash, role) VALUES (:id, 'X', :email, 'hash', :role)")
                .param("id", UUID.randomUUID())
                .param("email", email)
                .param("role", role)
                .update();
    }
}
