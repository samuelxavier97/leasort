package com.resort.platform.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestSequence;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.users.Role;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

class LeadCrudTest extends IntegrationTestSupport {

    ApiClient admin;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        admin = loggedIn(Role.ADMIN);
    }

    @Test
    void createsLeadWithNameOnly() throws Exception {
        JsonNode lead = json(admin.post("/api/leads", Map.of("name", "  Lead Fictício  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Lead Fictício"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.prospector").isEmpty())
                .andReturn().getResponse().getContentAsString());

        assertThat(actions(UUID.fromString(lead.get("id").asString()))).containsExactly("LEAD_CREATED");
    }

    @Test
    void createsAssignedLeadWithAllFields() throws Exception {
        Prospector prospector = testData.prospectorOf(testData.user(Role.PROSPECTOR));
        Map<String, Object> body = new HashMap<>(Map.of(
                "name", "Lead Fictício", "cpf", FakeCpf.formatted(FakeCpf.generate()), "phone", "(11) 98888-7777",
                "email", "Lead.Ficticio@Test.Local", "birthDate", "1985-04-12", "notes", "Prefere contato à tarde.",
                "prospectorId", prospector.getId()));

        JsonNode lead = json(admin.post("/api/leads", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("lead.ficticio@test.local"))
                .andExpect(jsonPath("$.birthDate").value("1985-04-12"))
                .andExpect(jsonPath("$.prospector.id").value(prospector.getId().toString()))
                .andReturn().getResponse().getContentAsString());

        UUID id = UUID.fromString(lead.get("id").asString());
        assertThat(actions(id)).containsExactlyInAnyOrder("LEAD_CREATED", "LEAD_ASSIGNED");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE entity_id = :id AND action = 'LEAD_ASSIGNED'
                          AND metadata ->> 'fromProspectorId' IS NULL AND metadata ->> 'toProspectorId' = :to
                        """).param("id", id).param("to", prospector.getId().toString()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void createRejectsUnknownOrInactiveProspector() throws Exception {
        admin.post("/api/leads", Map.of("name", "Lead Fictício", "prospectorId", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROSPECTOR_NOT_FOUND"));

        var inactive = testData.user(Role.PROSPECTOR, "senha-de-teste-123", false, false);
        admin.post("/api/leads", Map.of("name", "Lead Fictício", "prospectorId", testData.prospectorOf(inactive).getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROSPECTOR_INACTIVE"));
    }

    static List<Map<String, Object>> invalidBodies() {
        return List.of(
                Map.of("name", ""),
                Map.of("name", "x".repeat(121)),
                Map.of("name", "Lead", "cpf", FakeCpf.invalid()),
                Map.of("name", "Lead", "email", "nao-e-email"),
                Map.of("name", "Lead", "phone", "abc"),
                Map.of("name", "Lead", "birthDate", LocalDate.now().plusDays(1).toString()),
                Map.of("name", "Lead", "birthDate", "1899-12-31"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void validationErrors(Map<String, Object> body) throws Exception {
        admin.post("/api/leads", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void duplicateCpfWithOrWithoutPunctuationIsRejected() throws Exception {
        String cpf = FakeCpf.generate();
        testData.lead(null, cpf, LeadStatus.NEW);

        admin.post("/api/leads", Map.of("name", "Outro", "cpf", FakeCpf.formatted(cpf)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CPF_ALREADY_EXISTS"));
        admin.post("/api/leads", Map.of("name", "Outro", "cpf", cpf))
                .andExpect(status().isConflict());
    }

    @Test
    void listFiltersAndPagination() throws Exception {
        Prospector prospector = testData.prospectorOf(testData.user(Role.PROSPECTOR));
        Lead contacted = testData.lead(prospector, FakeCpf.generate(), LeadStatus.CONTACTED);
        Lead unassigned = testData.lead(null);
        String uniqueWord = TestSequence.next("Zeta");
        String named = json(admin.post("/api/leads", Map.of("name", "Lead " + uniqueWord))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        assertThat(ids("/api/leads?prospectorId=" + prospector.getId())).containsExactly(contacted.getId().toString());
        assertThat(ids("/api/leads?prospectorId=" + prospector.getId() + "&status=NEW")).isEmpty();
        assertThat(ids("/api/leads?unassigned=true&size=100&q=" + unassigned.getName())).containsExactly(unassigned.getId().toString());
        assertThat(ids("/api/leads?q=" + uniqueWord.toLowerCase())).containsExactly(named);
        admin.get("/api/leads").andExpect(jsonPath("$.size").value(20));
        admin.get("/api/leads?size=500").andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void updateAuditsChangedFieldsOnlyWhenSomethingChanges() throws Exception {
        Lead lead = testData.lead(null);
        Map<String, Object> body = new HashMap<>();
        body.put("name", lead.getName());
        body.put("phone", "11 91111-2222");
        body.put("email", lead.getEmail());

        admin.put("/api/leads/" + lead.getId(), body).andExpect(status().isOk());
        admin.put("/api/leads/" + lead.getId(), body).andExpect(status().isOk());

        List<String> metadata = jdbc.sql("""
                        SELECT metadata::text FROM audit_logs WHERE entity_id = :id AND action = 'LEAD_UPDATED'
                        """).param("id", lead.getId()).query(String.class).list();
        assertThat(metadata).hasSize(1);
        assertThat(metadata.getFirst()).contains("phone").doesNotContain("name", "91111");
    }

    private List<String> ids(String path) throws Exception {
        return json(admin.get(path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("content").valueStream().map(item -> item.get("id").asString()).toList();
    }

    private List<String> actions(UUID entityId) {
        return jdbc.sql("SELECT action FROM audit_logs WHERE entity_id = :id").param("id", entityId).query(String.class).list();
    }

    private JsonNode json(String body) {
        return jsonMapper.readTree(body);
    }
}
