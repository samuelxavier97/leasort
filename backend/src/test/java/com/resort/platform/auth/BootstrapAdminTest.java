package com.resort.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resort.platform.PlatformApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe a aplicação de verdade contra um banco novo por cenário, para exercitar a criação do
 * primeiro ADMIN na inicialização (D-052).
 */
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminTest {

    private static final String PASSWORD = "senha-inicial-segura";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    private static final AtomicInteger databases = new AtomicInteger();

    static JdbcClient admin;

    @BeforeAll
    static void connect() {
        admin = JdbcClient.create(dataSource(postgres.getDatabaseName()));
    }

    @Test
    void createsAdminWithForcedPasswordChangeAndAudit(CapturedOutput output) {
        String db = newDatabase();

        start(db, "  Admin.Inicial@Resort.Local ", PASSWORD).close();

        JdbcClient jdbc = JdbcClient.create(dataSource(db));
        var row = jdbc.sql("SELECT id, email, role, must_change_password, password_hash FROM users")
                .query((rs, n) -> List.of(rs.getString("id"), rs.getString("email"), rs.getString("role"),
                        rs.getBoolean("must_change_password"), rs.getString("password_hash")))
                .single();
        assertThat(row.get(1)).isEqualTo("admin.inicial@resort.local");
        assertThat(row.get(2)).isEqualTo("ADMIN");
        assertThat(row.get(3)).isEqualTo(true);
        assertThat((String) row.get(4)).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs
                        WHERE action = 'USER_CREATED' AND user_id IS NULL AND entity_id = :id
                          AND metadata ->> 'source' = 'BOOTSTRAP'
                        """).param("id", UUID.fromString((String) row.get(0))).query(Long.class).single())
                .isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContainIgnoringCase("generated security password");
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        String db = newDatabase();
        start(db, "admin@resort.local", PASSWORD).close();
        JdbcClient jdbc = JdbcClient.create(dataSource(db));
        String hashBefore = jdbc.sql("SELECT password_hash FROM users").query(String.class).single();

        start(db, "outro@resort.local", "outra-senha-segura").close();

        assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT password_hash FROM users").query(String.class).single()).isEqualTo(hashBefore);
    }

    @Test
    void refusesToStartWithoutAdminAndWithoutVariables() {
        for (String[] variables : new String[][] {{"admin@resort.local", ""}, {"", PASSWORD}, {"", ""}}) {
            String db = newDatabase();

            assertThatThrownBy(() -> start(db, variables[0], variables[1]))
                    .satisfies(ex -> assertThat(cause(ex))
                            .contains("APP_BOOTSTRAP_ADMIN_EMAIL", "APP_BOOTSTRAP_ADMIN_PASSWORD"));
            assertThat(JdbcClient.create(dataSource(db)).sql("SELECT count(*) FROM users").query(Long.class).single())
                    .isZero();
        }
    }

    @Test
    void refusesInvalidEmailOrShortPassword() {
        assertThatThrownBy(() -> start(newDatabase(), "nao-e-email", PASSWORD))
                .satisfies(ex -> assertThat(cause(ex)).contains("APP_BOOTSTRAP_ADMIN_EMAIL"));
        assertThatThrownBy(() -> start(newDatabase(), "admin@resort.local", "curta"))
                .satisfies(ex -> assertThat(cause(ex)).contains("APP_BOOTSTRAP_ADMIN_PASSWORD"));
    }

    @Test
    void refusesEmailOfExistingNonAdminUser() {
        String db = newDatabase();
        Flyway.configure().dataSource(dataSource(db)).load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource(db));
        jdbc.sql("INSERT INTO users (id, name, email, password_hash, role) VALUES (:id, 'Porteiro', 'porteiro@resort.local', 'hash', 'GATE')")
                .param("id", UUID.randomUUID())
                .update();

        assertThatThrownBy(() -> start(db, "porteiro@resort.local", PASSWORD))
                .satisfies(ex -> assertThat(cause(ex)).contains("já pertence a um usuário"));
        assertThat(jdbc.sql("SELECT role FROM users WHERE email = 'porteiro@resort.local'").query(String.class).single())
                .isEqualTo("GATE");
        assertThat(jdbc.sql("SELECT password_hash FROM users WHERE email = 'porteiro@resort.local'")
                        .query(String.class).single())
                .isEqualTo("hash");
    }

    /** A falha do runner pode vir direta ou embrulhada; vale a mensagem mais específica. */
    private static String cause(Throwable ex) {
        return NestedExceptionUtils.getMostSpecificCause(ex).getMessage();
    }

    private static ConfigurableApplicationContext start(String db, String email, String password) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.datasource.url=" + jdbcUrl(db),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--server.port=0",
                "--app.bootstrap-admin.email=" + email,
                "--app.bootstrap-admin.password=" + password));
        return new SpringApplicationBuilder(PlatformApplication.class).run(args.toArray(String[]::new));
    }

    private static String newDatabase() {
        String name = "bootstrap_" + databases.incrementAndGet();
        admin.sql("CREATE DATABASE " + name).update();
        return name;
    }

    private static String jdbcUrl(String db) {
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + db);
    }

    private static DriverManagerDataSource dataSource(String db) {
        return new DriverManagerDataSource(jdbcUrl(db), postgres.getUsername(), postgres.getPassword());
    }
}
