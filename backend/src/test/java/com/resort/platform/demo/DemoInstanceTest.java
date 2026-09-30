package com.resort.platform.demo;

import static com.resort.platform.production.ProductionDatabase.APP;
import static com.resort.platform.production.ProductionDatabase.APP_PASSWORD;
import static com.resort.platform.production.ProductionDatabase.OWNER;
import static com.resort.platform.production.ProductionDatabase.OWNER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.HttpBrowser;
import com.resort.platform.PlatformApplication;
import com.resort.platform.production.ProductionDatabase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Instalação de demonstração de ponta a ponta (D-125), num banco como o da produção: migrado pelo passo
 * separado, a carga conectada como {@code resort_app} e a aplicação no perfil {@code prod}. Os testes rodam
 * em ordem, porque cada um parte do estado do anterior:
 * <ol>
 *   <li>trava 2: sem o ADMIN inicial, com um Lead ou um usuário a mais, e numa segunda carga, a carga recusa sem
 *       gravar nada;</li>
 *   <li>a carga num banco só com o ADMIN inicial (e a auditoria do login dele);</li>
 *   <li>o uso pela API: os usuários da demonstração entram sem troca de senha, um Lead da carga é editado sem erro de
 *       validação, a Portaria libera o convite de hoje e o dashboard conta o histórico;</li>
 *   <li>a proteção da recarga pelo conteúdo: só com usuários da demonstração e o ADMIN inicial.</li>
 * </ol>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoInstanceTest {

    @Container
    static final PostgreSQLContainer postgres = ProductionDatabase.container();

    static final String BOOTSTRAP_EMAIL = "admin@producao.local";
    static final String BOOTSTRAP_PASSWORD = "senha-inicial-de-teste";
    static final Map<String, String> PASSWORDS = Map.of(
            "DEMO_ADMIN_PASSWORD", "senha-admin-demo",
            "DEMO_PROSPECTOR_PASSWORD", "senha-prospector-demo",
            "DEMO_GATE_PASSWORD", "senha-portaria-demo",
            "DEMO_HOST_PASSWORD", "senha-anfitriao-demo");
    static final List<String> TABLES = List.of("users", "prospectors", "leads", "visits", "visit_companions", "invitations",
            "access_records", "access_record_companions");

    static final JsonMapper json = JsonMapper.builder().build();
    static JdbcClient owner;
    static ConfigurableApplicationContext app;
    static int port;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeAll
    static void migrate() throws Exception {
        ProductionDatabase.migrate(postgres);
        owner = ProductionDatabase.as(postgres, OWNER, OWNER_PASSWORD);
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.close();
        }
    }

    private int run(String... args) {
        Map<String, String> env = new HashMap<>(PASSWORDS);
        env.put("DEMO_INSTANCE", "true");
        env.put("DB_URL", postgres.getJdbcUrl());
        env.put("DB_USER", APP);
        env.put("DB_PASSWORD", APP_PASSWORD);
        env.put("APP_BOOTSTRAP_ADMIN_EMAIL", BOOTSTRAP_EMAIL);
        out.reset();
        err.reset();
        return DemoLoadMain.run(args, env, Clock.systemUTC(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
    }

    private static Map<String, Long> counts() {
        Map<String, Long> counts = new HashMap<>();
        TABLES.forEach(table -> counts.put(table, owner.sql("SELECT count(*) FROM " + table).query(Long.class).single()));
        return counts;
    }

    // 1. Trava 2.

    @Test
    @Order(1)
    void withoutTheInitialAdminTheLoadRefuses() {
        assertThat(run()).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(output()).contains("ADMIN inicial");
        assertThat(counts().values()).allMatch(count -> count == 0);
    }

    @Test
    @Order(2)
    void withTheInitialAdminCreatedByTheApplication() throws Exception {
        port = freePort();
        app = new SpringApplicationBuilder(PlatformApplication.class).run(
                "--spring.profiles.active=prod",
                "--DB_URL=" + postgres.getJdbcUrl(),
                "--DB_USER=" + APP,
                "--DB_PASSWORD=" + APP_PASSWORD,
                "--server.port=" + port,
                "--MANAGEMENT_PORT=" + freePort(),
                "--TRUSTED_PROXY_IP=172.30.0.10",
                "--app.bootstrap-admin.email=" + BOOTSTRAP_EMAIL,
                "--app.bootstrap-admin.password=" + BOOTSTRAP_PASSWORD);
        // O login do ADMIN inicial deixa auditoria; ela não impede a carga.
        assertThat(login(new HttpBrowser(port), BOOTSTRAP_EMAIL, BOOTSTRAP_PASSWORD).get("mustChangePassword").asBoolean()).isTrue();
        assertThat(owner.sql("SELECT count(*) FROM users").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void anExtraLeadOrUserIsRefusedWithoutWritingAnything() {
        owner.sql("INSERT INTO leads (id, name, status) VALUES (:id, 'Lead de verdade', 'NEW')").param("id", UUID.randomUUID()).update();
        Map<String, Long> before = counts();
        assertThat(run()).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(output()).contains("leads");
        assertThat(counts()).isEqualTo(before);
        owner.sql("DELETE FROM leads").update();

        owner.sql("""
                        INSERT INTO users (id, name, email, password_hash, role) VALUES (:id, 'Porteiro de verdade', 'porteiro@producao.local', 'x', 'GATE')""")
                .param("id", UUID.randomUUID()).update();
        before = counts();
        assertThat(run()).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(output()).contains("2 usuários").doesNotContain("porteiro@producao.local");
        assertThat(counts()).isEqualTo(before);
        owner.sql("DELETE FROM users WHERE email = 'porteiro@producao.local'").update();
    }

    // 2. A carga.

    @Test
    @Order(4)
    void loadsOnADatabaseWithOnlyTheInitialAdmin() {
        assertThat(run()).as(output()).isEqualTo(DemoLoadMain.OK);
        assertThat(output()).contains("Demonstração carregada").contains("Leads: ");
        PASSWORDS.values().forEach(password -> assertThat(output()).doesNotContain(password));
        owner.sql("SELECT code FROM invitations").query(String.class).list()
                .forEach(code -> assertThat(output()).doesNotContain(code));
        Map<String, Long> counts = counts();
        assertThat(counts.get("users")).isEqualTo(1 + DemoDataPlan.USER_EMAILS.size());
        assertThat(counts.get("prospectors")).isEqualTo(8);
        assertThat(counts.get("visits")).isGreaterThan(200);
        assertThat(owner.sql("SELECT count(*) FROM leads WHERE cpf IS NOT NULL").query(Long.class).single()).isZero();
        assertThat(owner.sql("SELECT count(*) FROM visit_companions WHERE cpf IS NOT NULL").query(Long.class).single()).isZero();
        assertThat(owner.sql("SELECT count(*) FROM audit_logs WHERE metadata ->> 'source' = 'DEMO'").query(Long.class).single())
                .isEqualTo(DemoDataPlan.USER_EMAILS.size());
        // O ADMIN inicial continua o mesmo, com troca obrigatória.
        assertThat(owner.sql("SELECT must_change_password FROM users WHERE email = :e").param("e", BOOTSTRAP_EMAIL)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    @Order(5)
    void aSecondLoadIsRefused() {
        Map<String, Long> before = counts();
        assertThat(run()).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(counts()).isEqualTo(before);
    }

    // 3. O uso pela API.

    @Test
    @Order(6)
    void theDemoUsersLogInWithoutChangingThePassword() throws Exception {
        Map<String, String> roles = Map.of(
                DemoDataPlan.ADMIN_EMAIL, "ADMIN", DemoDataPlan.PROSPECTOR_EMAIL, "PROSPECTOR",
                DemoDataPlan.GATE_EMAIL, "GATE", DemoDataPlan.HOST_EMAIL, "HOST");
        Map<String, String> passwords = Map.of(
                DemoDataPlan.ADMIN_EMAIL, PASSWORDS.get("DEMO_ADMIN_PASSWORD"),
                DemoDataPlan.PROSPECTOR_EMAIL, PASSWORDS.get("DEMO_PROSPECTOR_PASSWORD"),
                DemoDataPlan.GATE_EMAIL, PASSWORDS.get("DEMO_GATE_PASSWORD"),
                DemoDataPlan.HOST_EMAIL, PASSWORDS.get("DEMO_HOST_PASSWORD"));
        for (var entry : roles.entrySet()) {
            JsonNode me = login(new HttpBrowser(port), entry.getKey(), passwords.get(entry.getKey()));
            assertThat(me.get("role").asString()).isEqualTo(entry.getValue());
            assertThat(me.get("mustChangePassword").asBoolean()).isFalse();
        }
    }

    @Test
    @Order(7)
    void aLeadOfTheLoadIsEditedWithoutValidationErrors() throws Exception {
        HttpBrowser prospector = new HttpBrowser(port);
        login(prospector, DemoDataPlan.PROSPECTOR_EMAIL, PASSWORDS.get("DEMO_PROSPECTOR_PASSWORD"));
        JsonNode lead = json.readTree(prospector.get("/api/leads?size=1").body()).get("content").get(0);
        String id = lead.get("id").asString();
        JsonNode detail = json.readTree(prospector.get("/api/leads/" + id).body());
        assertThat(detail.get("phone").asString()).startsWith("(00) 9");

        // O que o formulário envia ao salvar sem mudar nada, e depois com as observações alteradas.
        String body = json.writeValueAsString(Map.of(
                "name", detail.get("name").asString(),
                "phone", detail.get("phone").asString(),
                "email", detail.get("email").asString(),
                "birthDate", detail.get("birthDate").asString(),
                "notes", "Editado durante a apresentação."));
        HttpResponse<String> saved = prospector.put("/api/leads/" + id, body);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        assertThat(json.readTree(saved.body()).get("phone").asString()).isEqualTo(detail.get("phone").asString());
    }

    @Test
    @Order(8)
    void theGateLetsTodaysVisitInAndTheHostSeesTheArrival() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        List<Map<String, Object>> todays = owner.sql("""
                        SELECT i.code, v.id AS visit_id, l.name FROM invitations i
                        JOIN visits v ON v.id = i.visit_id JOIN leads l ON l.id = v.lead_id
                        WHERE v.scheduled_date = :today AND i.status = 'ACTIVE' ORDER BY v.created_at""")
                .param("today", today).query().listOfRows();
        assertThat(todays).hasSize(DemoDataPlan.VISITS_TODAY);

        HttpBrowser gate = new HttpBrowser(port);
        login(gate, DemoDataPlan.GATE_EMAIL, PASSWORDS.get("DEMO_GATE_PASSWORD"));
        String code = (String) todays.getFirst().get("code");
        JsonNode validated = json.readTree(gate.post("/api/access/validate", json.writeValueAsString(Map.of("code", "RSV:" + code))).body());
        assertThat(validated.get("result").asString()).isEqualTo("AUTHORIZED");
        List<String> companions = validated.get("companions").valueStream().map(c -> c.get("id").asString()).toList();
        JsonNode registered = json.readTree(gate.post("/api/access/register", json.writeValueAsString(Map.of(
                "invitationId", validated.get("invitationId").asString(),
                "presentCompanionIds", companions.isEmpty() ? List.of() : companions.subList(0, 1)))).body());
        assertThat(registered.get("result").asString()).isEqualTo("AUTHORIZED");

        HttpBrowser host = new HttpBrowser(port);
        login(host, DemoDataPlan.HOST_EMAIL, PASSWORDS.get("DEMO_HOST_PASSWORD"));
        assertThat(host.get("/api/arrivals").body()).contains((String) todays.getFirst().get("name"));
    }

    @Test
    @Order(9)
    void theDashboardCountsTheHistory() throws Exception {
        HttpBrowser admin = new HttpBrowser(port);
        login(admin, DemoDataPlan.ADMIN_EMAIL, PASSWORDS.get("DEMO_ADMIN_PASSWORD"));
        JsonNode summary = json.readTree(admin.get("/api/dashboard/summary").body());
        for (String card : List.of("totalLeads", "scheduledVisits", "activeInvitations", "completedVisits", "noShows", "cancellations", "entries")) {
            assertThat(summary.get(card).asLong()).as(card).isPositive();
        }
    }

    // 4. A proteção da recarga pelo conteúdo.

    @Test
    @Order(10)
    void theResetCheckAcceptsOnlyDemoUsersAndTheInitialAdmin() throws Exception {
        assertThat(run("check-reset")).as(output()).isEqualTo(DemoLoadMain.OK);

        // Um usuário de verdade, criado pela tela de Usuários durante a apresentação: a recarga recusa.
        HttpBrowser admin = new HttpBrowser(port);
        login(admin, DemoDataPlan.ADMIN_EMAIL, PASSWORDS.get("DEMO_ADMIN_PASSWORD"));
        HttpResponse<String> created = admin.post("/api/users", json.writeValueAsString(Map.of(
                "name", "Pessoa de Verdade", "email", "pessoa@empresa.local", "role", "HOST")));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        Map<String, Long> before = counts();
        assertThat(run("check-reset")).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(output()).contains("1 usuário(s)").contains("nada foi apagado").doesNotContain("pessoa@empresa.local");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    @Order(11)
    void theInitialAdminCountsOnlyWithItsOwnEmail() {
        // Outro e-mail no .env: o ADMIN inicial deste banco passa a ser um usuário de fora.
        assertThat(DemoLoadMain.run(new String[] {"check-reset"}, Map.of(
                        "DEMO_INSTANCE", "true", "DB_URL", postgres.getJdbcUrl(), "DB_USER", APP, "DB_PASSWORD", APP_PASSWORD,
                        "APP_BOOTSTRAP_ADMIN_EMAIL", "outro@producao.local"),
                Clock.systemUTC(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8)))
                .isEqualTo(DemoLoadMain.REFUSED);
    }

    private static JsonNode login(HttpBrowser browser, String email, String password) throws Exception {
        HttpResponse<String> response = browser.post("/api/auth/login", json.writeValueAsString(Map.of("email", email, "password", password)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
