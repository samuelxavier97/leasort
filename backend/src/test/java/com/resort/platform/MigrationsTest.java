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
    void appliesMigrationsV1ToV4AndCreatesTables() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getVersion)
                .extracting(Object::toString)
                .containsExactly("1", "2", "3", "4");
        assertThat(jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                        """).query(String.class).list())
                .containsExactlyInAnyOrder(
                        "users", "prospectors", "audit_logs", "spring_session", "spring_session_attributes");
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

    private void insertUser(String email, String role) {
        jdbc.sql("INSERT INTO users (id, name, email, password_hash, role) VALUES (:id, 'X', :email, 'hash', :role)")
                .param("id", UUID.randomUUID())
                .param("email", email)
                .param("role", role)
                .update();
    }
}
