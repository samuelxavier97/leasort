package com.resort.platform.production;

import static com.resort.platform.production.ProductionDatabase.APP;
import static com.resort.platform.production.ProductionDatabase.APP_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.PlatformApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A aplicação no perfil {@code prod}, como sobe no container: banco migrado pelo passo separado,
 * conexão como {@code resort_app} (D-057), readiness na porta interna de management só depois dos
 * ApplicationRunner, inclusive o ADMIN inicial (D-059), e nada de actuator na porta pública.
 */
@Testcontainers
class ProductionStartupTest {

    @Container
    static final PostgreSQLContainer postgres = ProductionDatabase.container();

    static final HttpClient http = HttpClient.newHttpClient();
    static ConfigurableApplicationContext app;
    static String publicUrl;
    static String managementUrl;

    @BeforeAll
    static void start() throws Exception {
        ProductionDatabase.migrate(postgres);
        managementPort = freePort();
        app = new SpringApplicationBuilder(PlatformApplication.class, Probes.class).run(
                "--spring.profiles.active=prod",
                "--DB_URL=" + postgres.getJdbcUrl(),
                "--DB_USER=" + APP,
                "--DB_PASSWORD=" + APP_PASSWORD,
                "--server.port=0",
                "--MANAGEMENT_PORT=" + managementPort,
                "--app.bootstrap-admin.email=admin@producao.local",
                "--app.bootstrap-admin.password=senha-inicial-de-teste");
        Environment env = app.getEnvironment();
        publicUrl = "http://localhost:" + env.getProperty("local.server.port");
        managementUrl = "http://localhost:" + managementPort;
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void readinessRefusesTrafficWhileTheRunnersRun() {
        assertThat(Probes.duringRunners).containsExactly(503);
    }

    @Test
    void readinessIsAcceptedOnlyWithTheInitialAdminInPlace() {
        assertThat(Probes.adminsWhenReady).containsExactly(1L);
    }

    @Test
    void readinessOnTheManagementPortAnswersOnlyTheStatus() throws Exception {
        HttpResponse<String> readiness = get(managementUrl + "/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get(managementUrl + "/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    @Test
    void thePublicPortHasNoActuator() throws Exception {
        for (String path : List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
                "/actuator/env", "/v3/api-docs")) {
            HttpResponse<String> response = get(publicUrl + path);
            assertThat(response.statusCode()).as(path).isIn(401, 403, 404);
            assertThat(response.body()).as(path).doesNotContain("\"status\":\"UP\"");
        }
    }

    @Test
    void theAppWorksAsTheDmlOnlyUserAndItsCookiesAreSecure() throws Exception {
        HttpResponse<String> me = get(publicUrl + "/api/auth/me");
        String xsrfCookie = setCookie(me, "XSRF-TOKEN");
        assertThat(xsrfCookie).contains("Secure").contains("SameSite=Lax").doesNotContain("HttpOnly");
        String token = xsrfCookie.substring("XSRF-TOKEN=".length(), xsrfCookie.indexOf(';'));

        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(publicUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .header("X-XSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"admin@producao.local\",\"password\":\"senha-inicial-de-teste\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(setCookie(login, "SESSION")).contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
        assertThat(setCookie(login, "XSRF-TOKEN")).contains("Secure");
        JdbcClient asApp = ProductionDatabase.as(postgres, APP, APP_PASSWORD);
        assertThat(asApp.sql("SELECT count(*) FROM audit_logs WHERE action IN ('USER_CREATED', 'LOGIN')")
                        .query(Long.class).single())
                .isEqualTo(2);
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String setCookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .reduce((first, last) -> last)
                .orElseThrow(() -> new AssertionError("Sem Set-Cookie " + name + ": " + response.headers().map()));
    }

    private static int freePort() throws Exception {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static int managementPort;

    /** Observa a prontidão durante a subida, sem depender da ordem dos ApplicationRunner. */
    @Configuration(proxyBeanMethods = false)
    static class Probes {

        static final List<Integer> duringRunners = new CopyOnWriteArrayList<>();
        static final List<Long> adminsWhenReady = new CopyOnWriteArrayList<>();

        /** Com o servidor de management já no ar, o readiness ainda recusa enquanto os runners rodam. */
        @Bean
        ApplicationRunner readinessWhileRunning() {
            return args -> duringRunners.add(get(
                    "http://localhost:" + managementPort + "/actuator/health/readiness").statusCode());
        }

        @EventListener
        void onReadiness(AvailabilityChangeEvent<ReadinessState> event) {
            if (event.getState() == ReadinessState.ACCEPTING_TRAFFIC) {
                adminsWhenReady.add(ProductionDatabase.as(postgres, APP, APP_PASSWORD)
                        .sql("SELECT count(*) FROM users WHERE role = 'ADMIN'").query(Long.class).single());
            }
        }
    }
}
