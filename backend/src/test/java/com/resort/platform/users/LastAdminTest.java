package com.resort.platform.users;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Roda num contexto (e banco) próprio, em que um único ADMIN fica ativo; no banco compartilhado há
 * sempre vários ADMINs criados por outros testes.
 */
@TestPropertySource(properties = "test.isolated-database=last-admin")
class LastAdminTest extends IntegrationTestSupport {

    User onlyAdmin;
    ApiClient client;

    @BeforeEach
    void leaveASingleActiveAdmin() throws Exception {
        onlyAdmin = testData.user(Role.ADMIN);
        jdbc.sql("UPDATE users SET active = false WHERE role = 'ADMIN' AND id <> :id")
                .param("id", onlyAdmin.getId())
                .update();
        client = client().login(onlyAdmin.getEmail(), TestData.PASSWORD);
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() throws Exception {
        client.patch("/api/users/" + onlyAdmin.getId() + "/status", Map.of("active", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        client.put("/api/users/" + onlyAdmin.getId(),
                        Map.of("name", onlyAdmin.getName(), "email", onlyAdmin.getEmail(), "role", "HOST"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }
}
