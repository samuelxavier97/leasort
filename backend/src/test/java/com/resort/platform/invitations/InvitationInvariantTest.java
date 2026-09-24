package com.resort.platform.invitations;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.leads.Lead;
import com.resort.platform.users.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * I8: depois de cada passo de todos os fluxos, toda visita SCHEDULED tem exatamente um convite ACTIVE e
 * nenhuma outra visita tem convite ACTIVE (RN06, D-071).
 */
class InvitationInvariantTest extends InvitationTestSupport {

    @Test
    void everyScheduledVisitHasExactlyOneActiveInvitationAfterEveryStep() throws Exception {
        ProspectorSession me = loggedInProspector();
        ApiClient admin = loggedIn(Role.ADMIN);
        Lead lead = testData.lead(me.prospector());
        UUID leadId = lead.getId();

        UUID visit = scheduledVisit(me.client(), leadId, calendar.today().plusDays(1));
        assertInvariant(leadId);

        me.client().put("/api/visits/" + visit, Map.of("hostNotes", "Fictício", "companions", List.of())).andExpect(status().isOk());
        assertInvariant(leadId);

        me.client().post("/api/invitations/" + activeOf(visit).id() + "/reissue", null).andExpect(status().isOk());
        assertInvariant(leadId);

        visit = UUID.fromString(json(me.client().post("/api/visits/" + visit + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(3).toString())).andExpect(status().isOk())).get("id").asString());
        assertInvariant(leadId);

        admin.post("/api/invitations/" + activeOf(visit).id() + "/reissue", null).andExpect(status().isOk());
        assertInvariant(leadId);

        me.client().patch("/api/visits/" + visit + "/cancel", null).andExpect(status().isOk());
        assertInvariant(leadId);

        scheduledVisit(me.client(), leadId, calendar.today().plusDays(4));
        assertInvariant(leadId);

        me.client().patch("/api/leads/" + leadId + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
        assertInvariant(leadId);

        admin.patch("/api/leads/" + leadId + "/status", Map.of("status", "NEW")).andExpect(status().isOk());
        assertInvariant(leadId);

        scheduledVisit(admin, leadId, calendar.today().plusDays(6));
        assertInvariant(leadId);
    }
}
