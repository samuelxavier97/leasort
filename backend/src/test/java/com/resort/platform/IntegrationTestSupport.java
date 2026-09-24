package com.resort.platform;

import com.resort.platform.users.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de integração: um único contexto e um único PostgreSQL para toda a suíte.
 * {@code audit_logs} é imutável e {@code users} é referenciada por ela, então nenhum teste limpa
 * o banco; cada teste cria seus próprios dados, com e-mails e códigos únicos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestData.class})
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected TestData testData;

    @Autowired
    protected JsonMapper jsonMapper;

    /** Novo "navegador" sem cookies. */
    protected ApiClient client() {
        return new ApiClient(mockMvc, jsonMapper);
    }

    /** Navegador já autenticado como um novo usuário do perfil informado. */
    protected ApiClient loggedIn(Role role) throws Exception {
        return client().login(testData.user(role).getEmail(), TestData.PASSWORD);
    }
}
