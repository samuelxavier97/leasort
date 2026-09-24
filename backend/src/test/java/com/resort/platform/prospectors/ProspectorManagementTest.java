package com.resort.platform.prospectors;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ProspectorManagementTest extends IntegrationTestSupport {

    ApiClient admin;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        admin = loggedIn(Role.ADMIN);
    }

    @Test
    void listAndDetailBringUserData() throws Exception {
        User user = testData.user(Role.PROSPECTOR);
        Prospector prospector = testData.prospectorOf(user);

        JsonNode item = findInList(prospector.getId());
        assertThat(item.get("name").asString()).isEqualTo(user.getName());
        assertThat(item.get("email").asString()).isEqualTo(user.getEmail());
        assertThat(item.get("active").asBoolean()).isTrue();
        assertThat(item.get("employeeCode").asString()).isEqualTo(prospector.getEmployeeCode());
        admin.get("/api/prospectors/" + prospector.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()));
    }

    @Test
    void updateCodeAndPhoneIsAuditedAsProspector() throws Exception {
        Prospector prospector = testData.prospectorOf(testData.user(Role.PROSPECTOR));
        String newCode = TestData.uniqueCode();

        admin.put("/api/prospectors/" + prospector.getId(), Map.of("employeeCode", newCode, "phone", "11 3333-4444"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCode").value(newCode))
                .andExpect(jsonPath("$.phone").value("11 3333-4444"));

        String metadata = jdbc.sql("""
                        SELECT metadata::text FROM audit_logs
                        WHERE action = 'USER_UPDATED' AND entity_type = 'PROSPECTOR' AND entity_id = :id
                        """).param("id", prospector.getId()).query(String.class).single();
        assertThat(metadata).contains("employeeCode", "phone").doesNotContain(newCode);
    }

    @Test
    void duplicateCodeIsRejected() throws Exception {
        Prospector first = testData.prospectorOf(testData.user(Role.PROSPECTOR));
        Prospector second = testData.prospectorOf(testData.user(Role.PROSPECTOR));

        admin.put("/api/prospectors/" + second.getId(), Map.of("employeeCode", first.getEmployeeCode()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_CODE_ALREADY_EXISTS"));
    }

    @Test
    void unknownProspectorReturnsNotFound() throws Exception {
        admin.get("/api/prospectors/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROSPECTOR_NOT_FOUND"));
    }

    private JsonNode findInList(UUID id) throws Exception {
        for (int page = 0; ; page++) {
            JsonNode body = jsonMapper.readTree(admin.get("/api/prospectors?size=100&page=" + page)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for (JsonNode item : body.get("content")) {
                if (item.get("id").asString().equals(id.toString())) {
                    return item;
                }
            }
            if (page + 1 >= body.get("totalPages").asInt()) {
                throw new AssertionError("Prospector " + id + " não está na lista");
            }
        }
    }
}
