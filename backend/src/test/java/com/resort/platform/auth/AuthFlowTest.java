package com.resort.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.HttpBrowser;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

/** Fluxo de autenticação sobre HTTP real, com o Spring Session JDBC gravando no PostgreSQL. */
class AuthFlowTest extends IntegrationTestSupport {

    @LocalServerPort
    int port;

    OffsetDateTime testStart;

    @BeforeEach
    void markStart() {
        testStart = jdbc.sql("SELECT now()").query(OffsetDateTime.class).single();
    }

    @Test
    void xsrfCookieHasExpectedAttributesOverRealHttp() throws Exception {
        HttpResponse<String> response = new HttpBrowser(port).get("/api/auth/me");

        assertThat(response.statusCode()).isEqualTo(401);
        List<String> xsrf = HttpBrowser.setCookieHeaders(response, "XSRF-TOKEN");
        assertThat(xsrf).singleElement().satisfies(header -> {
            assertThat(header).doesNotContainIgnoringCase("HttpOnly");
            assertThat(header).containsIgnoringCase("SameSite=Lax");
            assertThat(header).contains("Path=/");
        });
    }

    @Test
    void successfulLoginCreatesJdbcSessionAndAuditsLogin() throws Exception {
        User user = testData.user(Role.GATE);
        HttpBrowser browser = new HttpBrowser(port);

        HttpResponse<String> response = browser.post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = jsonMapper.readTree(response.body());
        assertThat(body.get("id").asString()).isEqualTo(user.getId().toString());
        assertThat(body.get("role").asString()).isEqualTo("GATE");
        assertThat(body.get("mustChangePassword").asBoolean()).isFalse();
        assertThat(HttpBrowser.setCookieHeaders(response, "SESSION")).singleElement().satisfies(header -> {
            assertThat(header).containsIgnoringCase("HttpOnly");
            assertThat(header).containsIgnoringCase("SameSite=Lax");
        });
        assertThat(principalOfSession(browser)).isEqualTo(user.getId().toString());
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs
                        WHERE action = 'LOGIN' AND user_id = :userId AND ip_address IS NOT NULL
                        """).param("userId", user.getId()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void loginChangesTheSessionId() throws Exception {
        User first = testData.user(Role.GATE);
        User second = testData.user(Role.HOST);
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login", credentials(first.getEmail(), TestData.PASSWORD));
        String sessionBefore = sessionId(browser);

        HttpResponse<String> response = browser.post("/api/auth/login", credentials(second.getEmail(), TestData.PASSWORD));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(sessionId(browser)).isNotEqualTo(sessionBefore);
        assertThat(sessionExists(sessionBefore)).isFalse();
        assertThat(principalOfSession(browser)).isEqualTo(second.getId().toString());
    }

    @Test
    void responsesNeverExposePasswordHash() throws Exception {
        User user = testData.user(Role.HOST);
        HttpBrowser browser = new HttpBrowser(port);

        HttpResponse<String> login = browser.post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD));
        HttpResponse<String> me = browser.get("/api/auth/me");

        assertThat(me.statusCode()).isEqualTo(200);
        for (String body : List.of(login.body(), me.body())) {
            assertThat(jsonMapper.readTree(body).propertyNames())
                    .containsExactlyInAnyOrder("id", "name", "email", "role", "mustChangePassword");
            assertThat(body).doesNotContain(user.getPasswordHash());
        }
    }

    @Test
    void logoutDeletesSessionAndAuditsLogout() throws Exception {
        User user = testData.user(Role.GATE);
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD));
        String sessionCookie = browser.cookie("SESSION");
        String sessionId = sessionId(browser);

        HttpResponse<String> logout = browser.post("/api/auth/logout", null);

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(sessionExists(sessionId)).isFalse();
        browser.setCookie("SESSION", sessionCookie);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(auditCount("LOGOUT", user.getId())).isEqualTo(1);
    }

    @Test
    void wrongPasswordReturnsGenericErrorAndAuditsFailure() throws Exception {
        User user = testData.user(Role.GATE);

        HttpResponse<String> response = new HttpBrowser(port)
                .post("/api/auth/login", credentials(user.getEmail(), "senha-errada-123"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(failureReason(user.getId())).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void unknownEmailReturnsTheSameBodyAsWrongPassword() throws Exception {
        User user = testData.user(Role.GATE);
        String wrongPasswordBody = new HttpBrowser(port)
                .post("/api/auth/login", credentials(user.getEmail(), "senha-errada-123")).body();

        HttpResponse<String> response = new HttpBrowser(port)
                .post("/api/auth/login", credentials(TestData.uniqueEmail("ninguem"), "senha-errada-123"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).isEqualTo(wrongPasswordBody);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs
                        WHERE action = 'LOGIN_FAILED' AND user_id IS NULL AND created_at >= :start
                          AND metadata ->> 'reason' = 'INVALID_CREDENTIALS'
                        """).param("start", testStart).query(Long.class).single())
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE metadata::text LIKE '%ninguem%'")
                        .query(Long.class).single())
                .as("o e-mail digitado não vai para a auditoria (D-050)")
                .isZero();
    }

    @Test
    void inactiveUserGetsTheSameGenericError() throws Exception {
        User inactive = testData.user(Role.HOST, TestData.PASSWORD, false, false);
        User active = testData.user(Role.HOST);
        String wrongPasswordBody = new HttpBrowser(port)
                .post("/api/auth/login", credentials(active.getEmail(), "senha-errada-123")).body();

        HttpResponse<String> response = new HttpBrowser(port)
                .post("/api/auth/login", credentials(inactive.getEmail(), TestData.PASSWORD));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).isEqualTo(wrongPasswordBody);
        assertThat(failureReason(inactive.getId())).isEqualTo("USER_INACTIVE");
    }

    @Test
    void loginIgnoresEmailCase() throws Exception {
        User user = testData.user(Role.GATE);

        HttpResponse<String> response = new HttpBrowser(port)
                .post("/api/auth/login", credentials("  " + user.getEmail().toUpperCase() + " ", TestData.PASSWORD));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void sessionTimeoutComesFromConfiguration() throws Exception {
        User user = testData.user(Role.GATE);
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD));

        int maxInactive = jdbc.sql("SELECT max_inactive_interval FROM spring_session WHERE session_id = :id")
                .param("id", sessionId(browser)).query(Integer.class).single();

        assertThat(maxInactive).isEqualTo(8 * 60 * 60);
    }

    private String credentials(String email, String password) {
        return jsonMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    /** O cookie do Spring Session guarda o id da sessão em Base64. */
    private static String sessionId(HttpBrowser browser) {
        return new String(Base64.getDecoder().decode(browser.cookie("SESSION")), StandardCharsets.UTF_8);
    }

    private String principalOfSession(HttpBrowser browser) {
        return jdbc.sql("SELECT principal_name FROM spring_session WHERE session_id = :id")
                .param("id", sessionId(browser)).query(String.class).single();
    }

    private boolean sessionExists(String sessionId) {
        return jdbc.sql("SELECT count(*) FROM spring_session WHERE session_id = :id")
                .param("id", sessionId).query(Long.class).single() > 0;
    }

    private long auditCount(String action, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = :action AND user_id = :userId")
                .param("action", action).param("userId", userId).query(Long.class).single();
    }

    private String failureReason(UUID userId) {
        return jdbc.sql("""
                        SELECT metadata ->> 'reason' FROM audit_logs
                        WHERE action = 'LOGIN_FAILED' AND user_id = :userId
                        """).param("userId", userId).query(String.class).single();
    }
}
