package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/** QR (RN08, D-083) e reemissão (§7.2, §13, D-041, D-084). */
class InvitationQrAndReissueTest extends InvitationTestSupport {

    /** I13 */
    @Test
    void qrCodeIsAPngWithOnlyThePrefixAndTheCode() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID visit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(1));
        InvitationRow invitation = activeOf(visit);

        byte[] png = me.client().get("/api/invitations/" + invitation.id() + "/qr-code")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(QrCodesTest.decode(png)).isEqualTo("RSV:" + invitation.code());
    }

    /** I14 */
    @Test
    void qrCodeOfAnInactiveInvitationIsRefused() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID visit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(1));
        UUID invitation = activeOf(visit).id();
        me.client().patch("/api/visits/" + visit + "/cancel", null).andExpect(status().isOk());

        me.client().get("/api/invitations/" + invitation + "/qr-code")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_ACTIVE"));
    }

    /** I15 */
    @Test
    void ownerReissuesKeepingVisitCompanionsAndExpiry() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        UUID visit = scheduledVisit(me.client(), lead.getId(), calendar.today().plusDays(2));
        InvitationRow current = activeOf(visit);
        String visitBefore = me.client().get("/api/visits/" + visit).andReturn().getResponse().getContentAsString();

        JsonNode reissued = json(me.client().post("/api/invitations/" + current.id() + "/reissue", null)
                .andExpect(status().isOk()));

        List<InvitationRow> rows = invitationsOf(visit);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).id()).isEqualTo(current.id());
        assertThat(rows.get(0).status()).isEqualTo("CANCELLED");
        assertThat(rows.get(0).cancelledAt()).isNotNull();
        InvitationRow replacement = activeOf(visit);
        assertThat(replacement.code()).isNotEqualTo(current.code());
        assertThat(replacement.expiresAt()).isEqualTo(current.expiresAt());
        assertThat(reissued.get("id").asString()).isEqualTo(replacement.id().toString());
        assertThat(reissued.get("code").asString()).isEqualTo(replacement.code());
        assertThat(reissued.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(reissued.get("canReissue").asBoolean()).isTrue();

        JsonNode after = json(me.client().get("/api/visits/" + visit));
        JsonNode before = jsonMapper.readTree(visitBefore);
        assertThat(after.get("companions")).isEqualTo(before.get("companions"));
        assertThat(after.get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(after.get("invitation").get("id").asString()).isEqualTo(replacement.id().toString());
        assertThat(leadStatus(lead.getId())).isEqualTo("VISIT_SCHEDULED");
        assertThat(audits("INVITATION_REISSUED", current.id())).singleElement()
                .satisfies(row -> assertThat(row.get("metadata").toString()).contains(replacement.id().toString()));
        assertThat(audits("INVITATION_CREATED", replacement.id())).singleElement()
                .satisfies(row -> assertThat(row.get("metadata").toString()).contains(current.id().toString()));
    }

    /** I16 */
    @Test
    void adminReissues() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID visit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(2));
        InvitationRow current = activeOf(visit);

        loggedIn(Role.ADMIN).post("/api/invitations/" + current.id() + "/reissue", null).andExpect(status().isOk());

        assertThat(activeOf(visit).id()).isNotEqualTo(current.id());
    }

    /** I17: quem só lê e quem não tem relação recebem o mesmo 404 de um id inexistente (D-041). */
    @Test
    void readOnlyAndUnrelatedProspectorsGetTheSame404() throws Exception {
        ProspectorSession former = loggedInProspector();
        ProspectorSession current = loggedInProspector();
        ProspectorSession stranger = loggedInProspector();
        Lead lead = testData.lead(former.prospector());
        UUID visit = scheduledVisit(former.client(), lead.getId(), calendar.today().plusDays(2));
        InvitationRow invitation = activeOf(visit);
        loggedIn(Role.ADMIN).patch("/api/leads/assign",
                Map.of("leadIds", List.of(lead.getId()), "prospectorId", current.prospector().getId())).andExpect(status().isOk());

        // O responsável antigo lê o convite, mas não reemite.
        former.client().get("/api/invitations/" + invitation.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReissue").value(false))
                .andExpect(jsonPath("$.lead.accessible").value(false));
        String missing = withoutInstance(former.client().post("/api/invitations/" + UUID.randomUUID() + "/reissue", null)
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());
        for (ApiClient client : List.of(former.client(), stranger.client())) {
            String body = client.post("/api/invitations/" + invitation.id() + "/reissue", null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(withoutInstance(body)).isEqualTo(missing);
        }
        assertThat(invitationsOf(visit)).containsExactly(invitation);

        current.client().post("/api/invitations/" + invitation.id() + "/reissue", null).andExpect(status().isOk());
    }

    /** I18 */
    @Test
    void onlyActiveInvitationsOfScheduledVisitsCanBeReissued() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID cancelledVisit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(1));
        UUID cancelledInvitation = activeOf(cancelledVisit).id();
        me.client().patch("/api/visits/" + cancelledVisit + "/cancel", null).andExpect(status().isOk());

        UUID completedVisit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(1));
        InvitationRow stillActive = activeOf(completedVisit);
        // Estado que só a Fase 6 produz: visita concluída com convite ainda ACTIVE, preparado no banco.
        jdbc.sql("UPDATE visits SET status = 'COMPLETED' WHERE id = :id").param("id", completedVisit).update();

        for (UUID id : List.of(cancelledInvitation, stillActive.id())) {
            me.client().post("/api/invitations/" + id + "/reissue", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVITATION_NOT_ACTIVE"));
        }
        assertThat(invitationsOf(completedVisit)).containsExactly(stillActive);
    }

    private static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }
}
