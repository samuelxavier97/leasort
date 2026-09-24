package com.resort.platform;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de teste que se comporta como o navegador: guarda os cookies de sessão e CSRF entre
 * requisições e envia {@code X-XSRF-TOKEN} nas escritas. Nenhum atalho de segurança é usado.
 */
public class ApiClient {

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private String remoteAddr = "127.0.0.1";

    public ApiClient(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    public ApiClient fromIp(String ip) {
        this.remoteAddr = ip;
        return this;
    }

    public ApiClient login(String email, String password) throws Exception {
        post("/api/auth/login", Map.of("email", email, "password", password)).andExpect(status().isOk());
        return this;
    }

    public ResultActions get(String path) throws Exception {
        return perform(MockMvcRequestBuilders.get(path), false);
    }

    public ResultActions post(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.post(path), body), true);
    }

    public ResultActions put(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.put(path), body), true);
    }

    public ResultActions patch(String path, Object body) throws Exception {
        return perform(withBody(MockMvcRequestBuilders.patch(path), body), true);
    }

    /** Upload multipart no campo {@code file}, com o mesmo CSRF das demais escritas. */
    public ResultActions upload(String path, String filename, byte[] content) throws Exception {
        return perform(MockMvcRequestBuilders.multipart(path)
                .file(new MockMultipartFile("file", filename, "text/csv", content)), true);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    public JsonMapper json() {
        return jsonMapper;
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) {
        if (body == null) {
            return builder;
        }
        return builder.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> builder, boolean unsafe) throws Exception {
        if (unsafe && !cookies.containsKey("XSRF-TOKEN")) {
            // Como o frontend: um GET inicial entrega o cookie CSRF (D-054).
            get("/api/auth/me");
        }
        cookies.forEach((name, value) -> builder.cookie(new Cookie(name, value)));
        if (unsafe && cookies.containsKey("XSRF-TOKEN")) {
            builder.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        }
        builder.with(request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        });
        ResultActions result = mockMvc.perform(builder);
        updateCookies(result.andReturn().getResponse());
        return result;
    }

    private void updateCookies(MockHttpServletResponse response) {
        for (String header : response.getHeaders("Set-Cookie")) {
            String nameValue = header.split(";", 2)[0];
            int separator = nameValue.indexOf('=');
            String name = nameValue.substring(0, separator).trim();
            String value = nameValue.substring(separator + 1).trim();
            boolean expired = header.toLowerCase().contains("max-age=0") || value.isEmpty();
            if (expired) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
