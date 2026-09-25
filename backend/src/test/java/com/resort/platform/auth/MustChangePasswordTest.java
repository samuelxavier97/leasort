package com.resort.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.ResultActions;

class MustChangePasswordTest extends IntegrationTestSupport {

    @Test
    void onlyMeChangePasswordAndLogoutAreAvailable() throws Exception {
        ApiClient client = loginWithPendingChange(Role.GATE);
        client.get("/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        client.post("/api/auth/logout", null).andExpect(status().isNoContent());

        ApiClient another = loginWithPendingChange(Role.HOST);
        another.post("/api/auth/change-password", Map.of(
                        "currentPassword", TestData.PASSWORD, "newPassword", "nova-senha-segura-1"))
                .andExpect(status().isNoContent());
    }

    static Stream<Arguments> blockedRoutes() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("GET", "/api/users"),
                Arguments.of("GET", "/api/users/" + id),
                Arguments.of("POST", "/api/users"),
                Arguments.of("PUT", "/api/users/" + id),
                Arguments.of("PATCH", "/api/users/" + id + "/status"),
                Arguments.of("POST", "/api/users/" + id + "/reset-password"),
                Arguments.of("GET", "/api/prospectors"),
                Arguments.of("GET", "/api/prospectors/" + id),
                Arguments.of("PUT", "/api/prospectors/" + id),
                Arguments.of("GET", "/api/leads"),
                Arguments.of("GET", "/api/leads/" + id),
                Arguments.of("POST", "/api/leads"),
                Arguments.of("PUT", "/api/leads/" + id),
                Arguments.of("PATCH", "/api/leads/" + id + "/status"),
                Arguments.of("PATCH", "/api/leads/assign"),
                Arguments.of("POST", "/api/leads/import"),
                Arguments.of("GET", "/api/leads/import/template"),
                Arguments.of("GET", "/api/visits"),
                Arguments.of("GET", "/api/visits/" + id),
                Arguments.of("POST", "/api/visits"),
                Arguments.of("PUT", "/api/visits/" + id),
                Arguments.of("POST", "/api/visits/" + id + "/reschedule"),
                Arguments.of("PATCH", "/api/visits/" + id + "/cancel"),
                Arguments.of("GET", "/api/invitations"),
                Arguments.of("GET", "/api/invitations/" + id),
                Arguments.of("GET", "/api/invitations/" + id + "/qr-code"),
                Arguments.of("POST", "/api/invitations/" + id + "/reissue"),
                Arguments.of("POST", "/api/access/validate"),
                Arguments.of("POST", "/api/access/register"),
                Arguments.of("GET", "/api/access/recent"),
                Arguments.of("GET", "/api/arrivals"),
                Arguments.of("GET", "/api/visits/" + id + "/sheet"),
                Arguments.of("GET", "/api/dashboard/summary"),
                Arguments.of("GET", "/api/dashboard/visits-by-day"),
                Arguments.of("GET", "/api/dashboard/access-by-day"),
                Arguments.of("GET", "/api/exports/leads"),
                Arguments.of("GET", "/api/exports/visits"),
                Arguments.of("GET", "/api/exports/companions"),
                Arguments.of("GET", "/api/exports/access"),
                Arguments.of("GET", "/api/audit"),
                Arguments.of("GET", "/api/rota-inexistente"),
                Arguments.of("POST", "/api/rota-inexistente"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("blockedRoutes")
    void everyOtherRouteIsBlockedEvenForAdmin(String method, String path) throws Exception {
        ApiClient admin = loginWithPendingChange(Role.ADMIN);

        request(admin, method, path)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void afterChangingPasswordTheSameSessionGetsFullAccess() throws Exception {
        User admin = testData.user(Role.ADMIN, TestData.PASSWORD, true, true);
        ApiClient client = client().login(admin.getEmail(), TestData.PASSWORD);
        client.get("/api/users").andExpect(status().isForbidden());

        client.post("/api/auth/change-password", Map.of(
                        "currentPassword", TestData.PASSWORD, "newPassword", "nova-senha-segura-1"))
                .andExpect(status().isNoContent());

        client.get("/api/users").andExpect(status().isOk());
        client.get("/api/auth/me").andExpect(jsonPath("$.mustChangePassword").value(false));
        assertThat(testData.reload(admin).isMustChangePassword()).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'PASSWORD_CHANGED' AND user_id = :id")
                        .param("id", admin.getId()).query(Long.class).single())
                .isEqualTo(1);
    }

    private ApiClient loginWithPendingChange(Role role) throws Exception {
        User user = testData.user(role, TestData.PASSWORD, true, true);
        return client().login(user.getEmail(), TestData.PASSWORD);
    }

    private static ResultActions request(ApiClient client, String method, String path) throws Exception {
        return switch (method) {
            case "GET" -> client.get(path);
            case "POST" -> client.post(path, Map.of());
            case "PUT" -> client.put(path, Map.of());
            case "PATCH" -> client.patch(path, Map.of());
            default -> throw new IllegalArgumentException(method);
        };
    }
}
