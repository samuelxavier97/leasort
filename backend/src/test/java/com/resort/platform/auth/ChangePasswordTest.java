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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChangePasswordTest extends IntegrationTestSupport {

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingChanges() throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient client = client().login(user.getEmail(), TestData.PASSWORD);

        client.post("/api/auth/change-password", Map.of(
                        "currentPassword", "nao-e-a-senha-1", "newPassword", "nova-senha-segura-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(testData.reload(user).getPasswordHash()).isEqualTo(user.getPasswordHash());
    }

    /** Curta demais, ou 80 bytes em UTF-8 com só 40 caracteres (acima do limite do BCrypt). */
    @ParameterizedTest
    @ValueSource(strings = {"curta-123", "áááááááááááááááááááááááááááááááááááááááá"})
    void newPasswordOutsideTheRulesIsRejected(String newPassword) throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient client = client().login(user.getEmail(), TestData.PASSWORD);

        client.post("/api/auth/change-password", Map.of("currentPassword", TestData.PASSWORD, "newPassword", newPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void newPasswordEqualToCurrentIsRejected() throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient client = client().login(user.getEmail(), TestData.PASSWORD);

        client.post("/api/auth/change-password", Map.of(
                        "currentPassword", TestData.PASSWORD, "newPassword", TestData.PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
    }

    @Test
    void successKeepsCurrentSessionAndEndsTheOthers() throws Exception {
        User user = testData.user(Role.HOST);
        ApiClient current = client().login(user.getEmail(), TestData.PASSWORD);
        ApiClient otherDevice = client().login(user.getEmail(), TestData.PASSWORD);
        String newPassword = "nova-senha-segura-1";

        current.post("/api/auth/change-password", Map.of("currentPassword", TestData.PASSWORD, "newPassword", newPassword))
                .andExpect(status().isNoContent());

        current.get("/api/auth/me").andExpect(status().isOk());
        otherDevice.get("/api/auth/me").andExpect(status().isUnauthorized());
        client().post("/api/auth/login", Map.of("email", user.getEmail(), "password", newPassword))
                .andExpect(status().isOk());
        client().post("/api/auth/login", Map.of("email", user.getEmail(), "password", TestData.PASSWORD))
                .andExpect(status().isUnauthorized());
    }
}
