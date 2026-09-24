package com.resort.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class MigrationsTest extends IntegrationTestSupport {

    @Autowired
    Flyway flyway;

    @Test
    void appliesMigrationsAndCreatesTables() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .extracting(Object::toString)
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                        """).query(String.class).list())
                .containsExactlyInAnyOrder(
                        "users", "prospectors", "audit_logs", "spring_session", "spring_session_attributes", "leads", "visits", "visit_companions");
    }

    @Test
    void usersRejectsUppercaseEmail() {
        assertThatThrownBy(() -> insertUser("Maiuscula-" + UUID.randomUUID() + "@test.local", "GATE"))
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

    private UUID insertVisit(UUID lead, UUID prospector, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO visits (id, lead_id, prospector_id, scheduled_date, status)
                        VALUES (:id, :lead, :prospector, current_date, :status)
                        """)
                .param("id", id).param("lead", lead).param("prospector", prospector).param("status", status).update();
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
