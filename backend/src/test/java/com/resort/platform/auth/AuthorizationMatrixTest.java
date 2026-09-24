package com.resort.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Matriz da SPEC §4.5 para as rotas desta fase: cada rota testada com cada perfil e sem login. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationMatrixTest extends IntegrationTestSupport {

    private static final String ANONYMOUS = "ANONYMOUS";

    private final Map<Role, ApiClient> clients = new EnumMap<>(Role.class);
    private User target;
    private String prospectorId;

    @BeforeAll
    void setUp() throws Exception {
        for (Role role : Role.values()) {
            clients.put(role, loggedIn(role));
        }
        target = testData.user(Role.GATE);
        prospectorId = testData.prospectorOf(testData.user(Role.PROSPECTOR)).getId().toString();
    }

    static Stream<Arguments> adminOnlyRoutes() {
        return Stream.of(
                Arguments.of("GET", "/api/users"),
                Arguments.of("GET", "/api/users/{user}"),
                Arguments.of("POST", "/api/users"),
                Arguments.of("PUT", "/api/users/{user}"),
                Arguments.of("PATCH", "/api/users/{user}/status"),
                Arguments.of("POST", "/api/users/{user}/reset-password"),
                Arguments.of("GET", "/api/prospectors"),
                Arguments.of("GET", "/api/prospectors/{prospector}"),
                Arguments.of("PUT", "/api/prospectors/{prospector}"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminOnlyRoutes")
    void adminOnlyRoutesFollowTheMatrix(String method, String template) throws Exception {
        String path = template.replace("{user}", target.getId().toString()).replace("{prospector}", prospectorId);

        assertThat(status(clients.get(Role.ADMIN), method, path)).as("ADMIN").isNotIn(401, 403);
        for (Role role : List.of(Role.PROSPECTOR, Role.GATE, Role.HOST)) {
            assertThat(status(clients.get(role), method, path)).as(role.name()).isEqualTo(403);
        }
        assertThat(status(client(), method, path)).as(ANONYMOUS).isEqualTo(401);
    }

    @ParameterizedTest(name = "GET /api/auth/me como {0}")
    @MethodSource("roles")
    void meIsAvailableToEveryRole(Role role) throws Exception {
        assertThat(status(clients.get(role), "GET", "/api/auth/me")).isEqualTo(200);
    }

    static Stream<Role> roles() {
        return Stream.of(Role.values());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("anonymousRoutes")
    void anonymousAccess(String method, String path, int expected) throws Exception {
        assertThat(status(client(), method, path)).isEqualTo(expected);
    }

    static Stream<Arguments> anonymousRoutes() {
        return Stream.of(
                Arguments.of("GET", "/actuator/health", 200),
                Arguments.of("GET", "/api/auth/me", 401),
                Arguments.of("POST", "/api/auth/logout", 401),
                Arguments.of("POST", "/api/auth/change-password", 401),
                Arguments.of("GET", "/index.html", 401));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pathsOutsideTheApi")
    void pathsOutsideTheApiAreDeniedEvenForAdmin(String path) throws Exception {
        assertThat(status(clients.get(Role.ADMIN), "GET", path)).isEqualTo(403);
    }

    /** O Swagger só existe no perfil dev (D-056). */
    static Stream<String> pathsOutsideTheApi() {
        return Stream.of("/index.html", "/actuator/env", "/v3/api-docs", "/swagger-ui/index.html");
    }

    private static int status(ApiClient client, String method, String path) throws Exception {
        Map<String, Object> body = Map.of("active", true, "name", "Nome", "email", TestData.uniqueEmail("matriz"), "role", "GATE");
        return (switch (method) {
                    case "GET" -> client.get(path);
                    case "POST" -> client.post(path, body);
                    case "PUT" -> client.put(path, body);
                    case "PATCH" -> client.patch(path, body);
                    default -> throw new IllegalArgumentException(method);
                })
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
