package com.resort.platform.production;

import static com.resort.platform.production.ProductionDatabase.APP;
import static com.resort.platform.production.ProductionDatabase.APP_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.PlatformApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * IP real do cliente no perfil {@code prod} (D-108): o X-Forwarded-For só é aceito quando o pedido vem
 * do proxy confiável ({@code TRUSTED_PROXY_IP}, o IP interno do Nginx). Aqui o "cliente" é sempre
 * 127.0.0.1; ora ele é o proxy confiável, ora não. O IP vai para a auditoria e para o limite de login.
 */
@Testcontainers
class ProductionForwardedIpTest {

    private static final String FORGED = "203.0.113.9";

    @Container
    static final PostgreSQLContainer postgres = ProductionDatabase.container();

    static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void migrate() throws Exception {
        ProductionDatabase.migrate(postgres);
    }

    @Test
    void theForwardedIpIsUsedWhenTheRequestComesFromTheTrustedProxy() throws Exception {
        try (ConfigurableApplicationContext app = start("127.0.0.1")) {
            failedLogin(app, FORGED);
            assertThat(auditedIp()).isEqualTo(FORGED);
        }
    }

    @Test
    void aForwardedIpFromAnyOtherAddressIsIgnored() throws Exception {
        try (ConfigurableApplicationContext app = start("172.30.0.10")) {
            failedLogin(app, FORGED);
            assertThat(auditedIp()).isEqualTo("127.0.0.1");
        }
    }

    @Test
    void theTrustedProxyIsMatchedLiterallyNotAsARegex() throws Exception {
        // "127.0.0.1" como regex aceitaria "127a0b0c1"; com \Q...\E, os pontos são literais.
        try (ConfigurableApplicationContext app = start("127.0.0.1")) {
            assertThat(app.getEnvironment().getProperty("server.tomcat.remoteip.internal-proxies"))
                    .isEqualTo("\\Q127.0.0.1\\E");
        }
    }

    private static ConfigurableApplicationContext start(String trustedProxy) {
        return new SpringApplicationBuilder(PlatformApplication.class).run(
                "--spring.profiles.active=prod",
                "--DB_URL=" + postgres.getJdbcUrl(),
                "--DB_USER=" + APP,
                "--DB_PASSWORD=" + APP_PASSWORD,
                "--server.port=0",
                "--MANAGEMENT_PORT=0",
                "--TRUSTED_PROXY_IP=" + trustedProxy,
                "--app.bootstrap-admin.email=admin@producao.local",
                "--app.bootstrap-admin.password=senha-inicial-de-teste");
    }

    /** Login com senha errada para um e-mail novo, com o X-Forwarded-For informado. */
    private static void failedLogin(ConfigurableApplicationContext app, String forwardedFor) throws Exception {
        String base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
        HttpResponse<String> me = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/me")).build(),
                HttpResponse.BodyHandlers.ofString());
        String cookie = me.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow();
        String token = cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'));
        String email = "falha-" + UUID.randomUUID() + "@producao.local";
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .header("X-XSRF-TOKEN", token)
                        .header("X-Forwarded-For", forwardedFor)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + email + "\",\"password\":\"senha-errada-qualquer\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(401);
    }

    /** O e-mail não é gravado (D-050); o último LOGIN_FAILED sem usuário é o deste teste. */
    private static String auditedIp() {
        return ProductionDatabase.as(postgres, APP, APP_PASSWORD)
                .sql("SELECT ip_address FROM audit_logs WHERE action = 'LOGIN_FAILED' ORDER BY created_at DESC LIMIT 1")
                .query(String.class)
                .single();
    }
}
