package com.resort.platform.production;

import static com.resort.platform.production.ProductionDatabase.APP;
import static com.resort.platform.production.ProductionDatabase.APP_PASSWORD;
import static com.resort.platform.production.ProductionDatabase.BACKUP;
import static com.resort.platform.production.ProductionDatabase.BACKUP_PASSWORD;
import static com.resort.platform.production.ProductionDatabase.OWNER;
import static com.resort.platform.production.ProductionDatabase.OWNER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Papéis do banco de produção (D-057): a aplicação só faz DML e não consegue alterar nem desligar o
 * trigger de audit_logs (D-028), nem mudar a estrutura do banco.
 */
@Testcontainers
class DatabaseRolesTest {

    /** SQLSTATE de privilégio insuficiente (inclui "must be owner"). */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Container
    static final PostgreSQLContainer postgres = ProductionDatabase.container();

    static JdbcClient owner;
    static JdbcClient app;
    static JdbcClient backup;

    @BeforeAll
    static void migrate() throws Exception {
        ProductionDatabase.migrate(postgres);
        owner = ProductionDatabase.as(postgres, OWNER, OWNER_PASSWORD);
        app = ProductionDatabase.as(postgres, APP, APP_PASSWORD);
        backup = ProductionDatabase.as(postgres, BACKUP, BACKUP_PASSWORD);
    }

    @Test
    void appDoesDmlOnBusinessAndSessionTables() {
        UUID id = UUID.randomUUID();
        app.sql("INSERT INTO users (id, name, email, password_hash, role) VALUES (:id, 'Usuário Fictício', :email, 'hash', 'HOST')")
                .param("id", id).param("email", id + "@teste.local").update();
        app.sql("UPDATE users SET name = 'Outro Nome' WHERE id = :id").param("id", id).update();
        assertThat(app.sql("SELECT name FROM users WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("Outro Nome");
        assertThat(app.sql("DELETE FROM users WHERE id = :id").param("id", id).update()).isEqualTo(1);

        app.sql("""
                INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,
                    max_inactive_interval, expiry_time) VALUES ('p-1', 's-1', 0, 0, 60, 60)
                """).update();
        assertThat(app.sql("DELETE FROM spring_session WHERE primary_id = 'p-1'").update()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_no_update_delete",
        "ALTER TABLE audit_logs DISABLE TRIGGER ALL",
        "ALTER TABLE audit_logs ENABLE REPLICA TRIGGER audit_logs_no_update_delete",
        "DROP TRIGGER audit_logs_no_update_delete ON audit_logs",
        "DROP TRIGGER audit_logs_no_truncate ON audit_logs",
        "CREATE OR REPLACE FUNCTION audit_logs_block_changes() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NULL; END $$",
        "DROP FUNCTION audit_logs_block_changes() CASCADE",
        "SET session_replication_role = replica",
    })
    void appCannotTouchTheAuditTrigger(String sql) {
        assertDenied(sql);
        assertThat(owner.sql("SELECT count(*) FROM pg_trigger WHERE tgrelid = 'audit_logs'::regclass AND NOT tgisinternal AND tgenabled = 'O'")
                        .query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void auditLogsIsInsertOnlyForTheAppEvenBeforeTheTrigger() {
        UUID id = UUID.randomUUID();
        app.sql("INSERT INTO audit_logs (id, action) VALUES (:id, 'LOGIN')").param("id", id).update();
        assertThat(app.sql("SELECT count(*) FROM audit_logs WHERE id = :id").param("id", id).query(Long.class).single())
                .isEqualTo(1);

        assertDenied("UPDATE audit_logs SET action = 'LOGOUT' WHERE id = '" + id + "'");
        assertDenied("DELETE FROM audit_logs WHERE id = '" + id + "'");
        assertDenied("TRUNCATE audit_logs");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE TABLE intrusa (id int)",
        "CREATE VIEW intrusa AS SELECT 1",
        "CREATE FUNCTION intrusa() RETURNS int LANGUAGE sql AS 'SELECT 1'",
        "DROP TABLE leads",
        "DROP TABLE audit_logs",
        "ALTER TABLE leads ADD COLUMN intrusa int",
        "ALTER TABLE users DROP CONSTRAINT users_role_ck",
        "TRUNCATE leads",
        "CREATE INDEX intruso ON leads (name)",
        "SELECT count(*) FROM flyway_schema_history",
    })
    void appCannotChangeTheStructure(String sql) {
        assertDenied(sql);
    }

    @Test
    void tablesOfFutureMigrationsAreDmlAccessibleByDefault() {
        owner.sql("CREATE TABLE future_table (id int PRIMARY KEY)").update();
        try {
            app.sql("INSERT INTO future_table (id) VALUES (1)").update();
            assertThat(app.sql("SELECT count(*) FROM future_table").query(Long.class).single()).isEqualTo(1);
            assertDenied("DROP TABLE future_table");
        } finally {
            owner.sql("DROP TABLE future_table").update();
        }
    }

    @Test
    void migratingAgainChangesNothing() throws Exception {
        long before = owner.sql("SELECT count(*) FROM flyway_schema_history").query(Long.class).single();

        ProductionDatabase.migrate(postgres);

        assertThat(owner.sql("SELECT count(*) FROM flyway_schema_history").query(Long.class).single()).isEqualTo(before);
        assertDenied("UPDATE audit_logs SET action = 'LOGOUT'");
    }

    @Test
    void backupReadsEverythingAndWritesNothing() {
        assertThat(backup.sql("SELECT count(*) FROM audit_logs").query(Long.class).single()).isNotNegative();
        assertThat(backup.sql("SELECT count(*) FROM flyway_schema_history").query(Long.class).single()).isPositive();
        assertThatThrownBy(() -> backup.sql("INSERT INTO audit_logs (id, action) VALUES (:id, 'LOGIN')")
                        .param("id", UUID.randomUUID()).update())
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(INSUFFICIENT_PRIVILEGE));
        assertThatThrownBy(() -> backup.sql("DELETE FROM users").update())
                .satisfies(ex -> assertThat(sqlState(ex)).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static void assertDenied(String sql) {
        assertThatThrownBy(() -> app.sql(sql).update())
                .as(sql)
                .satisfies(ex -> assertThat(sqlState(ex)).as(sql).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static String sqlState(Throwable ex) {
        return NestedExceptionUtils.getMostSpecificCause(ex) instanceof SQLException sql ? sql.getSQLState() : null;
    }
}
