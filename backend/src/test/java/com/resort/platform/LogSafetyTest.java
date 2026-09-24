package com.resort.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** Regra 5 do CLAUDE.md: nenhuma senha nem CPF em log. O bootstrap é coberto em BootstrapAdminTest. */
@ExtendWith(OutputCaptureExtension.class)
class LogSafetyTest extends IntegrationTestSupport {

    @Autowired
    LeadRepository leads;

    @Autowired
    TransactionTemplate transaction;

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

    @Test
    void cpfNeverReachesTheLogs(CapturedOutput output) throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        ProspectorSession prospector = loggedInProspector();
        String created = FakeCpf.generate();
        String replaced = FakeCpf.generate();
        String imported = FakeCpf.generate();
        String invalid = FakeCpf.invalid();

        String id = jsonMapper.readTree(admin.post("/api/leads", Map.of("name", "Lead Fictício", "cpf", created))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
        admin.put("/api/leads/" + id, Map.of("name", "Lead Fictício", "cpf", replaced)).andExpect(status().isOk());
        admin.post("/api/leads", Map.of("name", "Duplicado", "cpf", replaced)).andExpect(status().isConflict());
        admin.post("/api/leads", Map.of("name", "Inválido", "cpf", invalid)).andExpect(status().isBadRequest());
        prospector.client().put("/api/leads/" + testData.lead(prospector.prospector(), null, LeadStatus.NEW).getId(),
                Map.of("name", "X", "cpf", replaced)).andExpect(status().isConflict());
        String csv = "nome;cpf;telefone;email;data_nascimento;codigo_prospector;observacoes\n"
                + "Importado;" + imported + ";;;;;\n" + "Ruim;" + invalid + ";;;;;\n";
        admin.upload("/api/leads/import", "leads.csv", csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableContent());
        admin.upload("/api/leads/import", "leads.csv", csv.replace("Ruim;" + invalid, "Ok;").getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk());

        // Corrida que só a constraint do banco pega: a mensagem do PostgreSQL traz o valor da chave.
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    Lead duplicate = new Lead("Corrida");
                    duplicate.setCpf(imported);
                    leads.saveAndFlush(duplicate);
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        for (String cpf : List.of(created, replaced, imported, invalid)) {
            assertThat(output.getAll()).doesNotContain(cpf).doesNotContain(FakeCpf.formatted(cpf));
        }
    }
}
