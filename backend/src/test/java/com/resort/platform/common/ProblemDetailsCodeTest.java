package com.resort.platform.common;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.users.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Erros gerados pelo próprio Spring também saem com {@code code} estável (D-033). */
class ProblemDetailsCodeTest extends IntegrationTestSupport {

    @Test
    void springGeneratedErrorsCarryACode() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);

        admin.get("/api/rota-inexistente")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        admin.patch("/api/leads", Map.of())
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        admin.get("/api/leads/nao-e-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
