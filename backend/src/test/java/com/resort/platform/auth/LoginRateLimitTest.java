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

class LoginRateLimitTest extends IntegrationTestSupport {

    private static final String WRONG = "senha-errada-123";

    @Test
    void sixthAttemptIsBlockedEvenWithCorrectPassword() throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient client = client().fromIp("10.1.0.1");
        failTimes(client, user, 5);

        client.post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));

        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs
                        WHERE action = 'LOGIN_FAILED' AND user_id = :userId AND metadata ->> 'reason' = 'RATE_LIMITED'
                        """).param("userId", user.getId()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void blockIsPerEmailAndIpPair() throws Exception {
        User blocked = testData.user(Role.GATE);
        User other = testData.user(Role.GATE);
        failTimes(client().fromIp("10.2.0.1"), blocked, 5);

        client().fromIp("10.2.0.2")
                .post("/api/auth/login", credentials(blocked.getEmail(), TestData.PASSWORD))
                .andExpect(status().isOk());
        client().fromIp("10.2.0.1")
                .post("/api/auth/login", credentials(other.getEmail(), TestData.PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheCount() throws Exception {
        User user = testData.user(Role.GATE);
        ApiClient client = client().fromIp("10.3.0.1");
        failTimes(client, user, 4);
        client.post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD)).andExpect(status().isOk());

        failTimes(client().fromIp("10.3.0.1"), user, 4);

        client().fromIp("10.3.0.1")
                .post("/api/auth/login", credentials(user.getEmail(), TestData.PASSWORD))
                .andExpect(status().isOk());
    }

    private void failTimes(ApiClient client, User user, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            client.post("/api/auth/login", credentials(user.getEmail(), WRONG))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    private static Map<String, String> credentials(String email, String password) {
        return Map.of("email", email, "password", password);
    }
}
