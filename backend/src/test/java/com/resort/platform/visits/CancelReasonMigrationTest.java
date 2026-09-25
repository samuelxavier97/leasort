package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.IntegrationTestSupport;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A V10 classifica as visitas canceladas anteriores a ela (D-098): remarcação pela auditoria
 * VISIT_RESCHEDULED, as demais como cancelamento pelo usuário. Roda as migrations num schema próprio, até a
 * V9, insere os dados antigos e aplica a V10.
 */
class CancelReasonMigrationTest extends IntegrationTestSupport {

    private static final String SCHEMA = "cancel_reason_migration";

    @Autowired
    DataSource dataSource;

    @Test
    void v10ClassifiesOldCancellationsFromTheAudit() {
        JdbcClient sql = JdbcClient.create(dataSource);
        sql.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
        flywayUpTo("9").migrate();

        UUID user = UUID.randomUUID();
        UUID prospector = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        UUID rescheduled = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        UUID scheduled = UUID.randomUUID();
        sql.sql("INSERT INTO " + SCHEMA + ".users (id, name, email, password_hash, role) VALUES (:id, 'X', 'mig@test.local', 'h', 'PROSPECTOR')")
                .param("id", user).update();
        sql.sql("INSERT INTO " + SCHEMA + ".prospectors (id, user_id, employee_code) VALUES (:id, :user, 'MIG-1')")
                .param("id", prospector).param("user", user).update();
        sql.sql("INSERT INTO " + SCHEMA + ".leads (id, name) VALUES (:id, 'Lead Fictício')").param("id", lead).update();
        for (UUID id : new UUID[] {rescheduled, cancelled}) {
            sql.sql("INSERT INTO " + SCHEMA + ".visits (id, lead_id, prospector_id, scheduled_date, status, cancelled_at) "
                            + "VALUES (:id, :lead, :prospector, current_date, 'CANCELLED', now())")
                    .param("id", id).param("lead", lead).param("prospector", prospector).update();
        }
        sql.sql("INSERT INTO " + SCHEMA + ".visits (id, lead_id, prospector_id, scheduled_date, status) "
                        + "VALUES (:id, :lead, :prospector, current_date, 'SCHEDULED')")
                .param("id", scheduled).param("lead", lead).param("prospector", prospector).update();
        sql.sql("INSERT INTO " + SCHEMA + ".audit_logs (id, user_id, action, entity_type, entity_id) "
                        + "VALUES (:id, :user, 'VISIT_RESCHEDULED', 'VISIT', :visit)")
                .param("id", UUID.randomUUID()).param("user", user).param("visit", rescheduled).update();
        sql.sql("INSERT INTO " + SCHEMA + ".audit_logs (id, user_id, action, entity_type, entity_id) "
                        + "VALUES (:id, :user, 'VISIT_CANCELLED', 'VISIT', :visit)")
                .param("id", UUID.randomUUID()).param("user", user).param("visit", cancelled).update();

        flywayUpTo("10").migrate();

        assertThat(reasonOf(sql, rescheduled)).isEqualTo("RESCHEDULED");
        assertThat(reasonOf(sql, cancelled)).isEqualTo("CANCELLED_BY_USER");
        assertThat(reasonOf(sql, scheduled)).isNull();
        sql.sql("DROP SCHEMA " + SCHEMA + " CASCADE").update();
    }

    private Flyway flywayUpTo(String version) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load();
    }

    private static String reasonOf(JdbcClient sql, UUID visit) {
        return sql.sql("SELECT cancel_reason FROM " + SCHEMA + ".visits WHERE id = :id").param("id", visit)
                .query(String.class).optional().orElse(null);
    }
}
