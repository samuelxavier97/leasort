package com.resort.platform.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class UserManagementTest extends IntegrationTestSupport {

    ApiClient admin;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        admin = loggedIn(Role.ADMIN);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "GATE", "HOST"})
    void createReturnsTemporaryPasswordOnceAndForcesChange(Role role) throws Exception {
        String email = TestData.uniqueEmail("novo");

        JsonNode body = json(admin.post("/api/users", Map.of("name", "Novo Usuário", "email", email, "role", role))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.user.role").value(role.name()))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn());
        String temporaryPassword = body.get("temporaryPassword").asString();
        UUID id = UUID.fromString(body.get("user").get("id").asString());

        assertThat(temporaryPassword).hasSize(12);
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE id = :id").param("id", id)
                .query(String.class).single();
        assertThat(hash).startsWith("$2").isNotEqualTo(temporaryPassword);
        String metadata = jdbc.sql("""
                        SELECT metadata::text FROM audit_logs WHERE action = 'USER_CREATED' AND entity_id = :id
                        """).param("id", id).query(String.class).single();
        assertThat(metadata).contains(role.name()).doesNotContain(temporaryPassword);

        client().login(email, temporaryPassword).get("/api/auth/me")
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void prospectorRequiresEmployeeCode() throws Exception {
        admin.post("/api/users", Map.of("name", "Prospector", "email", TestData.uniqueEmail("p"), "role", "PROSPECTOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void prospectorIsCreatedWithItsProspectorRecord() throws Exception {
        String code = TestData.uniqueCode();

        JsonNode body = json(admin.post("/api/users", Map.of(
                        "name", "Prospector", "email", TestData.uniqueEmail("p"), "role", "PROSPECTOR",
                        "employeeCode", code, "phone", "(11) 99999-0000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.employeeCode").value(code))
                .andReturn());

        UUID userId = UUID.fromString(body.get("user").get("id").asString());
        assertThat(jdbc.sql("SELECT employee_code FROM prospectors WHERE user_id = :id").param("id", userId)
                        .query(String.class).single())
                .isEqualTo(code);
    }

    @Test
    void duplicateEmployeeCodeCreatesNothing() throws Exception {
        String code = testData.prospectorOf(testData.user(Role.PROSPECTOR)).getEmployeeCode();
        String email = TestData.uniqueEmail("dup");

        admin.post("/api/users", Map.of(
                        "name", "Prospector", "email", email, "role", "PROSPECTOR", "employeeCode", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_ALREADY_EXISTS"));

        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = :email").param("email", email)
                        .query(Long.class).single())
                .isZero();
    }

    @Test
    void duplicateEmailIgnoringCaseIsRejected() throws Exception {
        User existing = testData.user(Role.GATE);

        admin.post("/api/users", Map.of("name", "Outro", "email", existing.getEmail().toUpperCase(), "role", "HOST"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void getByIdAndUnknownId() throws Exception {
        User user = testData.user(Role.HOST);

        admin.get("/api/users/" + user.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("HOST"))
                .andExpect(jsonPath("$.active").value(true));
        admin.get("/api/users/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void listIsPaginatedWithDefaultAndMaximumSize() throws Exception {
        admin.get("/api/users")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.content").isArray());
        admin.get("/api/users?size=500").andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void updateAuditsChangedFieldsWithoutValues() throws Exception {
        User user = testData.user(Role.GATE);
        String newEmail = TestData.uniqueEmail("alterado");

        admin.put("/api/users/" + user.getId(), Map.of("name", "Nome Alterado", "email", newEmail, "role", "GATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Alterado"))
                .andExpect(jsonPath("$.email").value(newEmail));

        String metadata = jdbc.sql("""
                        SELECT metadata::text FROM audit_logs WHERE action = 'USER_UPDATED' AND entity_id = :id
                        """).param("id", user.getId()).query(String.class).single();
        assertThat(metadata).contains("changedFields", "email", "name").doesNotContain(newEmail, "Nome Alterado");
    }

    @Test
    void roleChangeBetweenAdminGateAndHostEndsTargetSessions() throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient targetSession = client().login(user.getEmail(), TestData.PASSWORD);

        admin.put("/api/users/" + user.getId(), Map.of("name", user.getName(), "email", user.getEmail(), "role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HOST"));

        targetSession.get("/api/auth/me").andExpect(status().isUnauthorized());
        User otherAdmin = testData.user(Role.ADMIN);
        admin.put("/api/users/" + otherAdmin.getId(),
                        Map.of("name", otherAdmin.getName(), "email", otherAdmin.getEmail(), "role", "GATE"))
                .andExpect(status().isOk());
    }

    @Test
    void roleChangeToOrFromProspectorIsRejected() throws Exception {
        User gate = testData.user(Role.GATE);
        User prospector = testData.user(Role.PROSPECTOR);

        admin.put("/api/users/" + gate.getId(), Map.of("name", gate.getName(), "email", gate.getEmail(), "role", "PROSPECTOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_CHANGE_NOT_ALLOWED"));
        admin.put("/api/users/" + prospector.getId(),
                        Map.of("name", prospector.getName(), "email", prospector.getEmail(), "role", "GATE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void deactivationEndsSessionsAndBlocksLogin() throws Exception {
        User user = testData.user(Role.HOST);
        ApiClient targetSession = client().login(user.getEmail(), TestData.PASSWORD);

        admin.patch("/api/users/" + user.getId() + "/status", Map.of("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        targetSession.get("/api/auth/me").andExpect(status().isUnauthorized());
        client().post("/api/auth/login", Map.of("email", user.getEmail(), "password", TestData.PASSWORD))
                .andExpect(status().isUnauthorized());
        assertThat(auditCount("USER_STATUS_CHANGED", user.getId())).isEqualTo(1);
    }

    @Test
    void resetPasswordReturnsTemporaryPasswordAndEndsSessions() throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient targetSession = client().login(user.getEmail(), TestData.PASSWORD);

        String temporaryPassword = json(admin.post("/api/users/" + user.getId() + "/reset-password", null)
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn())
                .get("temporaryPassword").asString();

        targetSession.get("/api/auth/me").andExpect(status().isUnauthorized());
        client().post("/api/auth/login", Map.of("email", user.getEmail(), "password", TestData.PASSWORD))
                .andExpect(status().isUnauthorized());
        client().login(user.getEmail(), temporaryPassword).get("/api/auth/me")
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        assertThat(auditCount("PASSWORD_RESET", user.getId())).isEqualTo(1);
    }

    @Test
    void userResponsesNeverContainPasswordHash() throws Exception {
        User user = testData.user(Role.PROSPECTOR);
        Map<String, Object> update = new HashMap<>(Map.of("name", "Nome", "email", user.getEmail(), "role", "PROSPECTOR"));

        for (MvcResult result : new MvcResult[] {
            admin.get("/api/users").andReturn(),
            admin.get("/api/users/" + user.getId()).andReturn(),
            admin.put("/api/users/" + user.getId(), update).andReturn(),
            admin.patch("/api/users/" + user.getId() + "/status", Map.of("active", true)).andReturn(),
            admin.post("/api/users", Map.of("name", "N", "email", TestData.uniqueEmail("n"), "role", "HOST")).andReturn()
        }) {
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("passwordHash").doesNotContain("password_hash").doesNotContain("$2a$");
        }
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private long auditCount(String action, UUID entityId) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = :action AND entity_id = :id")
                .param("action", action).param("id", entityId).query(Long.class).single();
    }
}
