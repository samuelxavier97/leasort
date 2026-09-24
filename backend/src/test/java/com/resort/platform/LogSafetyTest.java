package com.resort.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Regra 5 do CLAUDE.md: nenhuma senha em log. O bootstrap é coberto em BootstrapAdminTest. */
@ExtendWith(OutputCaptureExtension.class)
class LogSafetyTest extends IntegrationTestSupport {

    @Test
    void passwordsNeverReachTheLogs(CapturedOutput output) throws Exception {
        String wrongPassword = "senha-errada-para-log";
        String newPassword = "nova-senha-para-log-1";
        User user = testData.user(Role.GATE);
        ApiClient admin = loggedIn(Role.ADMIN);

        client().post("/api/auth/login", Map.of("email", user.getEmail(), "password", wrongPassword))
                .andExpect(status().isUnauthorized());
        ApiClient session = client().login(user.getEmail(), TestData.PASSWORD);
        session.post("/api/auth/change-password", Map.of("currentPassword", TestData.PASSWORD, "newPassword", newPassword))
                .andExpect(status().isNoContent());
        String created = jsonMapper.readTree(admin.post("/api/users",
                                Map.of("name", "Log", "email", TestData.uniqueEmail("log"), "role", "HOST"))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("temporaryPassword").asString();
        String reset = jsonMapper.readTree(admin.post("/api/users/" + user.getId() + "/reset-password", null)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("temporaryPassword").asString();

        assertThat(output.getAll()).doesNotContain(wrongPassword, TestData.PASSWORD, newPassword, created, reset);
    }
}
