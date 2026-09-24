package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.leads.Lead;
import com.resort.platform.users.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** D-041 e D-072: leitura para o responsável ou o dono atual; escrita só para o dono atual ou ADMIN. */
class VisitAccessTest extends VisitTestSupport {

    @Test
    void formerResponsibleReadsButCannotWriteAfterReassignment() throws Exception {
        ProspectorSession former = loggedInProspector();
        ProspectorSession current = loggedInProspector();
        Lead lead = testData.lead(former.prospector());
        UUID visitId = scheduledId(former.client(), lead.getId(), calendar.today().plusDays(2), List.of(companion("A")));
        loggedIn(Role.ADMIN).patch("/api/leads/assign",
                Map.of("leadIds", List.of(lead.getId()), "prospectorId", current.prospector().getId())).andExpect(status().isOk());

        former.client().get("/api/visits/" + visitId).andExpect(status().isOk()).andExpect(jsonPath("$.canEdit").value(false));
        assertThat(ids(former.client(), "/api/visits?size=100")).contains(visitId.toString());
        former.client().put("/api/visits/" + visitId, Map.of("companions", List.of())).andExpect(status().isNotFound());
        former.client().post("/api/visits/" + visitId + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(4).toString())).andExpect(status().isNotFound());
        former.client().patch("/api/visits/" + visitId + "/cancel", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VISIT_NOT_FOUND"));
        assertThat(visitStatus(visitId)).isEqualTo("SCHEDULED");

        current.client().get("/api/visits/" + visitId).andExpect(jsonPath("$.canEdit").value(true));
        current.client().put("/api/visits/" + visitId, Map.of("companions", List.of())).andExpect(status().isOk());
    }

    @Test
    void unrelatedProspectorGetsTheSame404AsANonexistentVisit() throws Exception {
        ProspectorSession owner = loggedInProspector();
        ProspectorSession stranger = loggedInProspector();
        UUID visitId = scheduledId(owner.client(), testData.lead(owner.prospector()).getId(), calendar.today(), List.of());
        String nonexistent = withoutInstance(stranger.client().get("/api/visits/" + UUID.randomUUID())
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());

        assertThat(withoutInstance(stranger.client().get("/api/visits/" + visitId)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString())).isEqualTo(nonexistent);
        stranger.client().put("/api/visits/" + visitId, Map.of("companions", List.of())).andExpect(status().isNotFound());
        stranger.client().post("/api/visits/" + visitId + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(1).toString())).andExpect(status().isNotFound());
        stranger.client().patch("/api/visits/" + visitId + "/cancel", null).andExpect(status().isNotFound());
        assertThat(ids(stranger.client(), "/api/visits?size=100")).doesNotContain(visitId.toString());
        assertThat(visitStatus(visitId)).isEqualTo("SCHEDULED");
    }

    @Test
    void gateHostAndAnonymousAreDenied() throws Exception {
        String id = UUID.randomUUID().toString();
        for (Role role : List.of(Role.GATE, Role.HOST)) {
            ApiClient client = loggedIn(role);
            client.get("/api/visits").andExpect(status().isForbidden());
            client.get("/api/visits/" + id).andExpect(status().isForbidden());
            client.post("/api/visits", Map.of()).andExpect(status().isForbidden());
            client.put("/api/visits/" + id, Map.of()).andExpect(status().isForbidden());
            client.post("/api/visits/" + id + "/reschedule", Map.of()).andExpect(status().isForbidden());
            client.patch("/api/visits/" + id + "/cancel", null).andExpect(status().isForbidden());
        }
        client().get("/api/visits").andExpect(status().isUnauthorized());
        client().post("/api/visits", Map.of()).andExpect(status().isUnauthorized());
    }

    @Test
    void listFilters() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        ProspectorSession first = loggedInProspector();
        ProspectorSession second = loggedInProspector();
        Lead lead = testData.lead(first.prospector());
        UUID old = scheduledId(first.client(), lead.getId(), calendar.today().plusDays(1), List.of());
        first.client().patch("/api/visits/" + old + "/cancel", null).andExpect(status().isOk());
        UUID next = scheduledId(first.client(), lead.getId(), calendar.today().plusDays(10), List.of());
        loggedIn(Role.ADMIN).patch("/api/leads/assign",
                Map.of("leadIds", List.of(lead.getId()), "prospectorId", second.prospector().getId())).andExpect(status().isOk());
        String from = calendar.today().plusDays(5).toString();

        assertThat(ids(admin, "/api/visits?leadId=" + lead.getId())).containsExactly(old.toString(), next.toString());
        assertThat(ids(admin, "/api/visits?leadId=" + lead.getId() + "&order=desc")).containsExactly(next.toString(), old.toString());
        assertThat(ids(admin, "/api/visits?leadId=" + lead.getId() + "&status=CANCELLED")).containsExactly(old.toString());
        assertThat(ids(admin, "/api/visits?leadId=" + lead.getId() + "&from=" + from)).containsExactly(next.toString());
        assertThat(ids(admin, "/api/visits?leadId=" + lead.getId() + "&to=" + from)).containsExactly(old.toString());
        assertThat(ids(admin, "/api/visits?prospectorId=" + first.prospector().getId())).containsExactly(old.toString(), next.toString());
        // O dono atual vê as visitas agendadas pelo dono anterior; o filtro de Prospector é ignorado para ele.
        assertThat(ids(second.client(), "/api/visits?leadId=" + lead.getId() + "&prospectorId=" + UUID.randomUUID()))
                .containsExactly(old.toString(), next.toString());
        admin.get("/api/visits?size=500").andExpect(jsonPath("$.size").value(100));
    }

    private List<String> ids(ApiClient client, String path) throws Exception {
        JsonNode page = jsonMapper.readTree(client.get(path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return page.get("content").valueStream().map(item -> item.get("id").asString()).toList();
    }

    private static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }
}
