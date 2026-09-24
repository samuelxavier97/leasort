package com.resort.platform.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.users.Role;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** CPF por perfil, com máscara no DTO (SPEC §15, D-060, D-061). */
class LeadCpfVisibilityTest extends IntegrationTestSupport {

    @Test
    void adminReceivesFullFormattedCpfEverywhere() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        String cpf = FakeCpf.generate();
        String formatted = FakeCpf.formatted(cpf);

        String id = json(admin.post("/api/leads", Map.of("name", "Lead Fictício", "cpf", cpf))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.cpf").value(formatted)))
                .get("id").asString();

        admin.get("/api/leads/" + id).andExpect(jsonPath("$.cpf").value(formatted));
        admin.get("/api/leads?cpf=" + cpf).andExpect(jsonPath("$.content[0].cpf").value(formatted));
        admin.put("/api/leads/" + id, Map.of("name", "Lead Fictício Editado")).andExpect(jsonPath("$.cpf").value(formatted));
    }

    /** Percorre toda resposta que o PROSPECTOR pode obter, inclusive as de erro. */
    @Test
    void prospectorNeverReceivesTheFullCpf() throws Exception {
        ProspectorSession me = loggedInProspector();
        ApiClient client = me.client();
        String cpf = FakeCpf.generate();
        Lead lead = testData.lead(me.prospector(), cpf, LeadStatus.NEW);
        Lead withoutCpf = testData.lead(me.prospector(), null, LeadStatus.NEW);
        String masked = "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
        String path = "/api/leads/" + lead.getId();

        assertMaskedOnly(client.get("/api/leads?size=100"), cpf, masked);
        assertMaskedOnly(client.get("/api/leads?q=" + lead.getName()), cpf, masked);
        assertMaskedOnly(client.get("/api/leads?cpf=" + cpf), cpf, null);
        assertMaskedOnly(client.get(path).andExpect(jsonPath("$.cpf").value(masked)), cpf, masked);
        assertMaskedOnly(client.put(path, update(lead.getName() + " editado", null)).andExpect(status().isOk()), cpf, masked);
        assertMaskedOnly(client.patch(path + "/status", Map.of("status", "CONTACTED")).andExpect(status().isOk()), cpf, masked);
        assertMaskedOnly(client.put(path, Map.of("name", "X", "email", "nao-e-email")).andExpect(status().isBadRequest()), cpf, null);
        assertMaskedOnly(client.put(path, update("X", cpf)).andExpect(status().isConflict()), cpf, null);
        assertMaskedOnly(client.put(path, update("X", FakeCpf.generate())).andExpect(status().isConflict()), cpf, null);
        assertMaskedOnly(client.put("/api/leads/" + withoutCpf.getId(), update("Y", cpf))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CPF_ALREADY_EXISTS")), cpf, null);
        assertMaskedOnly(client.put("/api/leads/" + withoutCpf.getId(), update("Y", FakeCpf.invalid()))
                .andExpect(status().isBadRequest()), cpf, null);
        assertMaskedOnly(client.patch(path + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk()), cpf, masked);
    }

    @Test
    void prospectorFillsMissingCpfButNeverChangesAnExistingOne() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead withoutCpf = testData.lead(me.prospector(), null, LeadStatus.NEW);
        String newCpf = FakeCpf.generate();

        me.client().put("/api/leads/" + withoutCpf.getId(), update(withoutCpf.getName(), FakeCpf.formatted(newCpf)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(masked(newCpf)));
        assertThat(testData.reloadLead(withoutCpf).getCpf()).isEqualTo(newCpf);

        // Mesmo valor igual ao gravado é recusado: a resposta não pode servir de oráculo do CPF mascarado.
        for (String attempt : new String[] {newCpf, FakeCpf.generate(), ""}) {
            me.client().put("/api/leads/" + withoutCpf.getId(), update(withoutCpf.getName(), attempt))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CPF_CHANGE_NOT_ALLOWED"));
        }
        assertThat(testData.reloadLead(withoutCpf).getCpf()).isEqualTo(newCpf);
    }

    @Test
    void adminChangesAndRemovesCpf() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        Lead lead = testData.lead(null);
        String replacement = FakeCpf.generate();

        admin.put("/api/leads/" + lead.getId(), update(lead.getName(), replacement))
                .andExpect(jsonPath("$.cpf").value(FakeCpf.formatted(replacement)));
        admin.put("/api/leads/" + lead.getId(), update(lead.getName(), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").isEmpty());
        assertThat(testData.reloadLead(lead).getCpf()).isNull();
    }

    @Test
    void auditMetadataNeverContainsCpf() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        String cpf = FakeCpf.generate();
        String replacement = FakeCpf.generate();
        String id = json(admin.post("/api/leads", Map.of("name", "Lead Fictício", "cpf", cpf))).get("id").asString();
        admin.put("/api/leads/" + id, update("Lead Fictício", replacement)).andExpect(status().isOk());

        String metadata = String.join("|", jdbc.sql("SELECT coalesce(metadata::text, '') FROM audit_logs WHERE entity_id = :id")
                .param("id", java.util.UUID.fromString(id)).query(String.class).list());
        assertThat(metadata).contains("cpf").doesNotContain(cpf, replacement, cpf.substring(3, 9), replacement.substring(3, 9));
    }

    private static Map<String, Object> update(String name, String cpf) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("cpf", cpf);
        return body;
    }

    private static String masked(String cpf) {
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }

    private static void assertMaskedOnly(ResultActions result, String cpf, String masked) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(cpf).doesNotContain(FakeCpf.formatted(cpf));
        if (masked != null) {
            assertThat(body).contains(masked);
        }
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
