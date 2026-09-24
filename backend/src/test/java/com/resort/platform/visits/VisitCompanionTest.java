package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.leads.Lead;
import com.resort.platform.users.Role;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Acompanhantes (RN13, D-020, D-022, D-043, D-075) e CPF por perfil (D-060, D-061). */
class VisitCompanionTest extends VisitTestSupport {

    @Test
    void acceptsUpToTheConfiguredLimit() throws Exception {
        ProspectorSession me = loggedInProspector();

        schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), companions(6))
                .andExpect(status().isCreated());
        schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), companions(7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_COMPANIONS"))
                .andExpect(jsonPath("$.detail").value("No máximo 6 acompanhantes por visita."));
    }

    static List<Map<String, Object>> invalidCompanions() {
        String future = LocalDate.now().plusYears(1).toString();
        return List.of(
                companion(null, null, "2012-01-01", "CHILD"),
                companion("", null, "2012-01-01", "CHILD"),
                companion("Sem nascimento", null, null, "CHILD"),
                companion("Sem parentesco", null, "2012-01-01", null),
                companion("Parentesco inválido", null, "2012-01-01", "COUSIN"),
                companion("CPF inválido", FakeCpf.invalid(), "2012-01-01", "CHILD"),
                companion("Nascimento futuro", null, future, "CHILD"),
                companion("Nascimento antigo", null, "1899-12-31", "CHILD"),
                companion("x".repeat(121), null, "2012-01-01", "CHILD"));
    }

    @ParameterizedTest
    @MethodSource("invalidCompanions")
    void validatesRequiredAndOptionalFields(Map<String, Object> invalid) throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());

        schedule(me.client(), lead.getId(), calendar.today(), List.of(invalid)).andExpect(status().isBadRequest());
        assertThat(scheduledCount(lead.getId())).isZero();
    }

    @Test
    void companionWithoutCpfIsAccepted() throws Exception {
        ProspectorSession me = loggedInProspector();

        schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(),
                        List.of(companion("Sem CPF", null, "2015-07-01", "GRANDCHILD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companions[0].cpf").isEmpty());
    }

    @Test
    void updateReplacesTheListMatchingById() throws Exception {
        ProspectorSession me = loggedInProspector();
        JsonNode visit = json(schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(2),
                List.of(companion("Mantido"), companion("Alterado"), companion("Removido"))));
        String visitId = visit.get("id").asString();
        String keptId = visit.get("companions").get(0).get("id").asString();
        String changedId = visit.get("companions").get(1).get("id").asString();

        Map<String, Object> kept = withId(companion("Mantido"), keptId);
        Map<String, Object> changed = withId(companion("Alterado Novo", null, "2011-11-11", "SIBLING"), changedId);
        JsonNode updated = json(me.client().put("/api/visits/" + visitId,
                        Map.of("notes", "Nota nova", "companions", List.of(kept, changed, companion("Novo"))))
                .andExpect(status().isOk()));

        List<String> ids = updated.get("companions").valueStream().map(c -> c.get("id").asString()).toList();
        assertThat(ids).hasSize(3).contains(keptId, changedId);
        assertThat(updated.get("companions").valueStream().map(c -> c.get("name").asString()).toList())
                .containsExactlyInAnyOrder("Mantido", "Alterado Novo", "Novo");
        String metadata = jdbc.sql("SELECT metadata::text FROM audit_logs WHERE action = 'VISIT_UPDATED' AND entity_id = :id")
                .param("id", UUID.fromString(visitId)).query(String.class).single();
        assertThat(metadata).contains("\"companionsAdded\": 1", "\"companionsUpdated\": 1", "\"companionsRemoved\": 1", "notes")
                .doesNotContain("Alterado Novo", "Removido", "Nota nova");
    }

    @Test
    void companionIdFromAnotherVisitIsRejected() throws Exception {
        ProspectorSession me = loggedInProspector();
        JsonNode first = json(schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), List.of(companion("A"))));
        JsonNode second = json(schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), List.of(companion("B"))));
        String foreignId = first.get("companions").get(0).get("id").asString();

        me.client().put("/api/visits/" + second.get("id").asString(),
                        Map.of("companions", List.of(withId(companion("Intruso"), foreignId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPANION_NOT_FOUND"));
        me.client().get("/api/visits/" + second.get("id").asString())
                .andExpect(jsonPath("$.companions[0].name").value("B"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED", "NO_SHOW"})
    void onlyScheduledVisitsAreEditable(String status) throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID visitId = scheduledId(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), List.of());
        forceVisitStatus(visitId, status);

        me.client().put("/api/visits/" + visitId, Map.of("companions", List.of(companion("Tarde demais"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_NOT_EDITABLE"));
    }

    @Test
    void scheduledVisitWithPastDateIsStillEditable() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID visitId = scheduledId(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), List.of());
        jdbc.sql("UPDATE visits SET scheduled_date = scheduled_date - 1 WHERE id = :id").param("id", visitId).update();

        me.client().put("/api/visits/" + visitId, Map.of("hostNotes", "Ainda editável", "companions", List.of()))
                .andExpect(status().isOk());
    }

    @Test
    void adminReceivesFullCompanionCpf() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        String cpf = FakeCpf.generate();

        schedule(admin, testData.lead(testData.prospectorOf(testData.user(Role.PROSPECTOR))).getId(), calendar.today(),
                        List.of(companion("Com CPF", cpf, "2000-01-01", "SPOUSE")))
                .andExpect(jsonPath("$.companions[0].cpf").value(FakeCpf.formatted(cpf)));
    }

    /** Percorre toda resposta que o PROSPECTOR pode obter, inclusive as de erro. */
    @Test
    void prospectorNeverReceivesTheFullCompanionCpf() throws Exception {
        ProspectorSession me = loggedInProspector();
        ApiClient client = me.client();
        String cpf = FakeCpf.generate();
        String masked = "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
        Lead lead = testData.lead(me.prospector());

        ResultActions created = schedule(client, lead.getId(), calendar.today().plusDays(1),
                List.of(companion("Com CPF", cpf, "2000-01-01", "SPOUSE")));
        assertMaskedOnly(created, cpf, masked);
        JsonNode visit = json(created);
        String id = visit.get("id").asString();
        String companionId = visit.get("companions").get(0).get("id").asString();
        Map<String, Object> keep = withId(companion("Com CPF", null, "2000-01-01", "SPOUSE"), companionId);

        assertMaskedOnly(client.get("/api/visits/" + id), cpf, masked);
        assertMaskedOnly(client.get("/api/visits?leadId=" + lead.getId()), cpf, masked);
        assertMaskedOnly(client.put("/api/visits/" + id, Map.of("companions", List.of(keep))).andExpect(status().isOk()), cpf, masked);
        assertMaskedOnly(client.put("/api/visits/" + id, Map.of("companions",
                List.of(withCpf(keep, cpf)))).andExpect(status().isConflict()), cpf, null);
        assertMaskedOnly(client.put("/api/visits/" + id, Map.of("companions",
                List.of(withCpf(keep, FakeCpf.invalid())))).andExpect(status().isBadRequest()), cpf, null);
        ResultActions rescheduled = client.post("/api/visits/" + id + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(5).toString())).andExpect(status().isOk());
        assertMaskedOnly(rescheduled, cpf, masked);
        String newId = json(rescheduled).get("id").asString();
        assertMaskedOnly(client.patch("/api/visits/" + newId + "/cancel", null).andExpect(status().isOk()), cpf, masked);
    }

    @Test
    void prospectorNeverChangesAnExistingCompanionCpf() throws Exception {
        ProspectorSession me = loggedInProspector();
        String cpf = FakeCpf.generate();
        JsonNode visit = json(schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(),
                List.of(companion("Com CPF", cpf, "2000-01-01", "SPOUSE"), companion("Sem CPF"))));
        String id = visit.get("id").asString();
        Map<String, Object> withCpfId = withId(companion("Com CPF", null, "2000-01-01", "SPOUSE"),
                visit.get("companions").get(0).get("id").asString());
        Map<String, Object> withoutCpfId = withId(companion("Sem CPF"), visit.get("companions").get(1).get("id").asString());

        // Mesmo CPF igual ao gravado é recusado: a resposta não pode servir de oráculo do CPF mascarado.
        for (String attempt : new String[] {cpf, FakeCpf.formatted(cpf), FakeCpf.generate(), ""}) {
            me.client().put("/api/visits/" + id, Map.of("companions", List.of(withCpf(withCpfId, attempt), withoutCpfId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CPF_CHANGE_NOT_ALLOWED"));
        }
        String newCpf = FakeCpf.generate();
        me.client().put("/api/visits/" + id, Map.of("companions", List.of(withCpfId, withCpf(withoutCpfId, newCpf))))
                .andExpect(status().isOk());

        assertThat(storedCpfs(id)).containsExactlyInAnyOrder(cpf, newCpf);
    }

    @Test
    void adminChangesAndRemovesCompanionCpf() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        String cpf = FakeCpf.generate();
        JsonNode visit = json(schedule(admin, testData.lead(testData.prospectorOf(testData.user(Role.PROSPECTOR))).getId(),
                calendar.today(), List.of(companion("Com CPF", cpf, "2000-01-01", "SPOUSE"))));
        String id = visit.get("id").asString();
        Map<String, Object> item = withId(companion("Com CPF", null, "2000-01-01", "SPOUSE"),
                visit.get("companions").get(0).get("id").asString());
        String replacement = FakeCpf.generate();

        admin.put("/api/visits/" + id, Map.of("companions", List.of(withCpf(item, replacement))))
                .andExpect(jsonPath("$.companions[0].cpf").value(FakeCpf.formatted(replacement)));
        admin.put("/api/visits/" + id, Map.of("companions", List.of(withCpf(item, ""))))
                .andExpect(jsonPath("$.companions[0].cpf").isEmpty());
    }

    @Test
    void auditNeverContainsCompanionNamesOrCpf() throws Exception {
        ProspectorSession me = loggedInProspector();
        String cpf = FakeCpf.generate();
        JsonNode visit = json(schedule(me.client(), testData.lead(me.prospector()).getId(), calendar.today(),
                List.of(companion("Nome Sigiloso", cpf, "2000-01-01", "SPOUSE"))));
        String id = visit.get("id").asString();
        me.client().put("/api/visits/" + id, Map.of("companions", List.of(companion("Outro Sigiloso")))).andExpect(status().isOk());
        String newId = json(me.client().post("/api/visits/" + id + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(3).toString()))).get("id").asString();

        String metadata = String.join("|", jdbc.sql("""
                        SELECT coalesce(metadata::text, '') FROM audit_logs WHERE entity_id IN (:ids)
                        """).param("ids", List.of(UUID.fromString(id), UUID.fromString(newId))).query(String.class).list());
        assertThat(metadata).doesNotContain(cpf, cpf.substring(3, 9), "Nome Sigiloso", "Outro Sigiloso");
    }

    private List<String> storedCpfs(String visitId) {
        return jdbc.sql("SELECT cpf FROM visit_companions WHERE visit_id = :id AND cpf IS NOT NULL")
                .param("id", UUID.fromString(visitId)).query(String.class).list();
    }

    private static Map<String, Object> withId(Map<String, Object> companion, String id) {
        Map<String, Object> copy = new HashMap<>(companion);
        copy.put("id", id);
        return copy;
    }

    private static Map<String, Object> withCpf(Map<String, Object> companion, String cpf) {
        Map<String, Object> copy = new HashMap<>(companion);
        copy.put("cpf", cpf);
        return copy;
    }

    private static void assertMaskedOnly(ResultActions result, String cpf, String masked) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(cpf).doesNotContain(FakeCpf.formatted(cpf));
        if (masked != null) {
            assertThat(body).contains(masked);
        }
    }

}
