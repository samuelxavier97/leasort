package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Leitura e lista de convites conforme a D-041 e a D-072; rotas só para ADMIN e PROSPECTOR. */
class InvitationAccessTest extends InvitationTestSupport {

    /** I21 */
    @Test
    void adminResponsibleAndCurrentOwnerReadTheInvitationWithoutPersonalData() throws Exception {
        ProspectorSession former = loggedInProspector();
        ProspectorSession current = loggedInProspector();
        ApiClient admin = loggedIn(Role.ADMIN);
        String leadCpf = FakeCpf.generate();
        String companionCpf = FakeCpf.generate();
        Lead lead = testData.lead(former.prospector(), leadCpf, LeadStatus.CONTACTED);
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", lead.getId());
        body.put("scheduledDate", calendar.today().plusDays(2).toString());
        body.put("companions", List.of(Map.of("name", "Acompanhante Fictício", "cpf", companionCpf,
                "birthDate", "1990-01-01", "relationship", "SPOUSE")));
        UUID visit = UUID.fromString(json(former.client().post("/api/visits", body).andExpect(status().isCreated()))
                .get("id").asString());
        InvitationRow invitation = activeOf(visit);

        JsonNode read = json(former.client().get("/api/invitations/" + invitation.id()).andExpect(status().isOk()));
        assertThat(read.get("code").asString()).isEqualTo(invitation.code());
        assertThat(read.get("formattedCode").asString()).isEqualTo(InvitationCodeGenerator.format(invitation.code()));
        assertThat(read.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(read.get("expiresAt").asString()).isEqualTo(invitation.expiresAt().toString());
        assertThat(read.get("visit").get("id").asString()).isEqualTo(visit.toString());
        assertThat(read.get("visit").get("companionsCount").asInt()).isEqualTo(1);
        assertThat(read.get("lead").get("name").asString()).isEqualTo(lead.getName());
        assertThat(read.get("prospector").get("name").asString()).isEqualTo(former.user().getName());
        assertThat(read.get("canReissue").asBoolean()).isTrue();
        assertThat(read.get("visit").get("canEdit").asBoolean()).isTrue();

        loggedIn(Role.ADMIN).patch("/api/leads/assign",
                Map.of("leadIds", List.of(lead.getId()), "prospectorId", current.prospector().getId())).andExpect(status().isOk());

        for (ApiClient reader : List.of(former.client(), current.client(), admin)) {
            String response = reader.get("/api/invitations/" + invitation.id()).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            for (String cpf : List.of(leadCpf, companionCpf)) {
                assertThat(response).doesNotContain(cpf).doesNotContain(FakeCpf.formatted(cpf)).doesNotContain(cpf.substring(3, 9));
            }
        }
        former.client().get("/api/invitations/" + invitation.id()).andExpect(jsonPath("$.canReissue").value(false));
        current.client().get("/api/invitations/" + invitation.id()).andExpect(jsonPath("$.canReissue").value(true));
        admin.get("/api/invitations/" + invitation.id()).andExpect(jsonPath("$.canReissue").value(true));
    }

    /** I22 */
    @Test
    void unrelatedProspectorGetsTheSame404AsANonexistentInvitation() throws Exception {
        ProspectorSession owner = loggedInProspector();
        ProspectorSession stranger = loggedInProspector();
        UUID visit = scheduledVisit(owner.client(), testData.lead(owner.prospector()).getId(), calendar.today().plusDays(1));
        UUID invitation = activeOf(visit).id();

        String missing = stranger.client().get("/api/invitations/" + UUID.randomUUID())
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for (String path : List.of("/api/invitations/" + invitation, "/api/invitations/" + invitation + "/qr-code")) {
            String body = stranger.client().get(path)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(withoutInstance(body)).isEqualTo(withoutInstance(missing));
        }
        assertThat(ids(stranger.client(), "/api/invitations?size=100")).doesNotContain(invitation.toString());
    }

    /** I23 */
    @Test
    void listFilters() throws Exception {
        ProspectorSession first = loggedInProspector();
        ProspectorSession second = loggedInProspector();
        ApiClient admin = loggedIn(Role.ADMIN);
        Lead lead = testData.lead(first.prospector());
        UUID early = scheduledVisit(first.client(), lead.getId(), calendar.today().plusDays(1));
        UUID cancelled = activeOf(early).id();
        UUID late = UUID.fromString(json(first.client().post("/api/visits/" + early + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(9).toString())).andExpect(status().isOk())).get("id").asString());
        UUID active = activeOf(late).id();
        UUID otherVisit = scheduledVisit(second.client(), testData.lead(second.prospector()).getId(), calendar.today().plusDays(3));
        UUID other = activeOf(otherVisit).id();

        assertThat(ids(first.client(), "/api/invitations?size=100")).containsExactly(cancelled.toString(), active.toString());
        assertThat(ids(first.client(), "/api/invitations?order=desc")).containsExactly(active.toString(), cancelled.toString());
        assertThat(ids(first.client(), "/api/invitations?status=ACTIVE")).containsExactly(active.toString());
        assertThat(ids(first.client(), "/api/invitations?visitId=" + early)).containsExactly(cancelled.toString());
        assertThat(ids(first.client(), "/api/invitations?leadId=" + lead.getId() + "&status=CANCELLED"))
                .containsExactly(cancelled.toString());
        // O filtro de Prospector só vale para o ADMIN.
        assertThat(ids(first.client(), "/api/invitations?prospectorId=" + second.prospector().getId()))
                .containsExactly(cancelled.toString(), active.toString());
        assertThat(ids(admin, "/api/invitations?prospectorId=" + second.prospector().getId())).containsExactly(other.toString());
        assertThat(ids(second.client(), "/api/invitations")).containsExactly(other.toString());
        JsonNode page = json(first.client().get("/api/invitations?size=1&page=1"));
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("id").asString()).isEqualTo(active.toString());
    }

    /** I24 */
    @Test
    void gateHostAndAnonymousAreDenied() throws Exception {
        UUID id = UUID.randomUUID();
        List<String> paths = List.of("/api/invitations", "/api/invitations/" + id, "/api/invitations/" + id + "/qr-code");
        for (Role role : List.of(Role.GATE, Role.HOST)) {
            ApiClient client = loggedIn(role);
            for (String path : paths) {
                client.get(path).andExpect(status().isForbidden());
            }
            client.post("/api/invitations/" + id + "/reissue", null).andExpect(status().isForbidden());
        }
        for (String path : paths) {
            client().get(path).andExpect(status().isUnauthorized());
        }
    }

    /** I26: não há criação nem cancelamento isolado de convite (D-010). */
    @Test
    void thereIsNoCreationNorStandaloneCancellation() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        admin.post("/api/invitations", Map.of()).andExpect(status().isMethodNotAllowed());
        admin.put("/api/invitations/" + UUID.randomUUID(), Map.of()).andExpect(status().isMethodNotAllowed());
        admin.patch("/api/invitations/" + UUID.randomUUID() + "/cancel", null).andExpect(status().isNotFound());
    }

    private List<String> ids(ApiClient client, String path) throws Exception {
        JsonNode page = json(client.get(path).andExpect(status().isOk()));
        return page.get("content").valueStream().map(item -> item.get("id").asString()).toList();
    }

    private static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }
}
