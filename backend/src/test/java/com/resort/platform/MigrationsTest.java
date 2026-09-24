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
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                        """).query(String.class).list())
                .containsExactlyInAnyOrder(
                        "users", "prospectors", "audit_logs", "spring_session", "spring_session_attributes", "leads");
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
