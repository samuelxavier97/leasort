package com.resort.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

class CsrfTest extends IntegrationTestSupport {

    @Test
    void firstGetIssuesReadableXsrfCookieEvenWhenUnauthenticated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn();

        String setCookie = xsrfSetCookie(result.getResponse());
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).doesNotContainIgnoringCase("HttpOnly");
        assertThat(setCookie).contains("Path=/");
        // SameSite=Lax é verificado em HTTP real (AuthFlowTest): o MockHttpServletResponse descarta o atributo.
    }

    @Test
    void loginWithoutHeaderIsRejectedAndCreatesNoSession() throws Exception {
        User user = testData.user(Role.GATE);
        String token = freshToken();
        long sessionsBefore = sessionCount();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        assertThat(sessionCount()).isEqualTo(sessionsBefore);
    }

    @Test
    void loginWithHeaderDifferentFromCookieIsRejected() throws Exception {
        User user = testData.user(Role.GATE);
        String token = freshToken();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", "outro-" + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void loginWithHeaderEqualToCookieSucceeds() throws Exception {
        User user = testData.user(Role.GATE);
        String token = freshToken();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user)))
                .andExpect(status().isOk());
    }

    /**
     * Double-submit com cookie: o servidor compara header e cookie. A rotação no login troca o cookie
     * do navegador, então o token antigo no header deixa de ser aceito.
     */
    @Test
    void loginRotatesTokenAndOldTokenIsRejected() throws Exception {
        User user = testData.user(Role.HOST);
        ApiClient client = client();
        client.get("/api/auth/me");
        String tokenBeforeLogin = client.cookie("XSRF-TOKEN");

        MvcResult login = client.post("/api/auth/login", Map.of("email", user.getEmail(), "password", TestData.PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        String tokenAfterLogin = client.cookie("XSRF-TOKEN");
        assertThat(xsrfSetCookie(login.getResponse())).as("novo token emitido na resposta do login").isNotNull();
        assertThat(tokenAfterLogin).isNotNull().isNotEqualTo(tokenBeforeLogin);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("SESSION", client.cookie("SESSION")), new Cookie("XSRF-TOKEN", tokenAfterLogin))
                        .header("X-XSRF-TOKEN", tokenBeforeLogin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        client.post("/api/auth/logout", null).andExpect(status().isNoContent());
    }

    @Test
    void getRequestsDoNotRequireToken() throws Exception {
        ApiClient client = loggedIn(Role.GATE);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("SESSION", client.cookie("SESSION"))))
                .andExpect(status().isOk());
    }

    private String freshToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")).andReturn();
        String setCookie = xsrfSetCookie(result.getResponse());
        return setCookie.split(";", 2)[0].substring("XSRF-TOKEN=".length());
    }

    private static String xsrfSetCookie(MockHttpServletResponse response) {
        List<String> headers = response.getHeaders("Set-Cookie");
        return headers.stream()
                .filter(header -> header.startsWith("XSRF-TOKEN=") && !header.startsWith("XSRF-TOKEN=;"))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String loginBody(User user) {
        return jsonMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", TestData.PASSWORD));
    }

    private long sessionCount() {
        return jdbc.sql("SELECT count(*) FROM spring_session").query(Long.class).single();
    }
}
