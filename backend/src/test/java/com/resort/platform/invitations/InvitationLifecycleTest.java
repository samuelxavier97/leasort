package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** RN06 e D-071: o convite nasce, é trocado e é cancelado junto com a visita. */
class InvitationLifecycleTest extends InvitationTestSupport {

    /** I5 */
    @Test
    void schedulingCreatesExactlyOneActiveInvitation() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        LocalDate date = calendar.today().plusDays(3);

        String body = schedule(me.client(), lead.getId(), date).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode visit = jsonMapper.readTree(body);
        UUID visitId = UUID.fromString(visit.get("id").asString());

        InvitationRow invitation = activeOf(visitId);
        assertThat(invitationsOf(visitId)).hasSize(1);
        assertThat(invitation.code()).matches("^[0-9A-HJKMNP-TV-Z]{10}$");
        assertThat(invitation.expiresAt()).isEqualTo(calendar.endOfDay(date));
        assertThat(visit.get("invitation").get("id").asString()).isEqualTo(invitation.id().toString());
        assertThat(visit.get("invitation").get("status").asString()).isEqualTo("ACTIVE");
        // D-086: o código não sai na resposta da visita.
        assertThat(body).doesNotContain(invitation.code());
        assertThat(audits("INVITATION_CREATED", invitation.id()))
                .singleElement()
                .satisfies(row -> assertThat(row.get("metadata").toString()).contains(visitId.toString()));
    }

    /** I7 */
    @Test
    void rescheduleCancelAndDiscardFollowTheVisit() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        UUID first = scheduledVisit(me.client(), lead.getId(), calendar.today().plusDays(2));
        InvitationRow original = activeOf(first);

        // Editar a visita não troca o código (§6.2).
        me.client().put("/api/visits/" + first, Map.of("notes", "Nota fictícia", "companions", List.of()))
                .andExpect(status().isOk());
        assertThat(activeOf(first)).isEqualTo(original);

        LocalDate newDate = calendar.today().plusDays(5);
        UUID second = UUID.fromString(json(me.client().post("/api/visits/" + first + "/reschedule",
                        Map.of("scheduledDate", newDate.toString())).andExpect(status().isOk()))
                .get("id").asString());
        InvitationRow cancelled = invitationsOf(first).get(0);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(reasonOf(cancelled.id())).isEqualTo("VISIT_RESCHEDULED");
        InvitationRow replacement = activeOf(second);
        assertThat(replacement.code()).isNotEqualTo(original.code());
        assertThat(replacement.expiresAt()).isEqualTo(calendar.endOfDay(newDate));

        me.client().patch("/api/visits/" + second + "/cancel", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitation.status").value("CANCELLED"));
        assertThat(activeCount(second)).isZero();
        assertThat(reasonOf(replacement.id())).isEqualTo("VISIT_CANCELLED");

        UUID third = scheduledVisit(me.client(), lead.getId(), calendar.today().plusDays(7));
        InvitationRow thirdInvitation = activeOf(third);
        me.client().patch("/api/leads/" + lead.getId() + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
        assertThat(activeCount(third)).isZero();
        assertThat(reasonOf(thirdInvitation.id())).isEqualTo("LEAD_DISCARDED");
        assertInvariant(lead.getId());
    }

    /** I9: visitas da Fase 4, sem convite, continuam funcionando (D-071). */
    @Test
    void legacyVisitsWithoutInvitationAreTolerated() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID toRead = legacyVisit(me);
        UUID toCancel = legacyVisit(me);
        UUID toReschedule = legacyVisit(me);
        UUID toDiscard = legacyVisit(me);

        me.client().get("/api/visits/" + toRead).andExpect(status().isOk()).andExpect(jsonPath("$.invitation").isEmpty());
        me.client().patch("/api/visits/" + toCancel + "/cancel", null).andExpect(status().isOk());
        assertThat(visitStatus(toCancel)).isEqualTo("CANCELLED");

        UUID rescheduled = UUID.fromString(json(me.client().post("/api/visits/" + toReschedule + "/reschedule",
                        Map.of("scheduledDate", calendar.today().plusDays(4).toString())).andExpect(status().isOk()))
                .get("id").asString());
        assertThat(invitationsOf(toReschedule)).isEmpty();
        assertThat(activeOf(rescheduled).expiresAt()).isEqualTo(calendar.endOfDay(calendar.today().plusDays(4)));

        UUID discardLead = jdbc.sql("SELECT lead_id FROM visits WHERE id = :id").param("id", toDiscard).query(UUID.class).single();
        me.client().patch("/api/leads/" + discardLead + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
        assertThat(visitStatus(toDiscard)).isEqualTo("CANCELLED");
    }

    /** I27: expires_at no fuso da operação, com o relógio controlado. */
    @Test
    void expiresAtIsTheEndOfTheVisitDayInTheOperationTimezone() throws Exception {
        // 23h30 de 09/03 em São Paulo; já é 10/03 em UTC.
        clock.set(Instant.parse("2026-03-10T02:30:00Z"));
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());

        UUID visit = scheduledVisit(me.client(), lead.getId(), LocalDate.parse("2026-03-09"));
        assertThat(activeOf(visit).expiresAt()).isEqualTo(Instant.parse("2026-03-10T03:00:00Z"));

        UUID moved = UUID.fromString(json(me.client().post("/api/visits/" + visit + "/reschedule",
                        Map.of("scheduledDate", "2026-03-12")).andExpect(status().isOk()))
                .get("id").asString());
        assertThat(activeOf(moved).expiresAt()).isEqualTo(Instant.parse("2026-03-13T03:00:00Z"));
    }

    /** I29: nenhum código de convite na auditoria (regra 5, D-044). */
    @Test
    void auditNeverContainsInvitationCodes() throws Exception {
        ProspectorSession me = loggedInProspector();
        ApiClient admin = loggedIn(Role.ADMIN);
        Lead lead = testData.lead(me.prospector());
        UUID visit = scheduledVisit(me.client(), lead.getId(), calendar.today().plusDays(2));
        UUID invitation = activeOf(visit).id();
        UUID reissued = UUID.fromString(json(me.client().post("/api/invitations/" + invitation + "/reissue", null)
                .andExpect(status().isOk())).get("id").asString());
        admin.post("/api/invitations/" + reissued + "/reissue", null).andExpect(status().isOk());
        UUID moved = UUID.fromString(json(me.client().post("/api/visits/" + visit + "/reschedule",
                        Map.of("scheduledDate", calendar.today().plusDays(6).toString())).andExpect(status().isOk()))
                .get("id").asString());
        me.client().patch("/api/visits/" + moved + "/cancel", null).andExpect(status().isOk());

        List<String> codes = new ArrayList<>();
        invitationsOf(visit).forEach(row -> codes.add(row.code()));
        invitationsOf(moved).forEach(row -> codes.add(row.code()));
        assertThat(codes).hasSize(4);
        String everything = String.join("\n", jdbc.sql("SELECT coalesce(metadata::text, '') FROM audit_logs")
                .query(String.class).list());
        for (String code : codes) {
            assertThat(everything).doesNotContain(code).doesNotContain(InvitationCodeGenerator.format(code));
        }
    }

    private String reasonOf(UUID invitationId) {
        return jdbc.sql("""
                        SELECT metadata ->> 'reason' FROM audit_logs
                        WHERE action = 'INVITATION_CANCELLED' AND entity_id = :id
                        """)
                .param("id", invitationId).query(String.class).single();
    }

    /** Visita SCHEDULED gravada direto no banco, como as criadas antes da Fase 5. */
    private UUID legacyVisit(ProspectorSession me) {
        Lead lead = testData.lead(me.prospector(), null, LeadStatus.VISIT_SCHEDULED);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO visits (id, lead_id, prospector_id, scheduled_date, status)
                        VALUES (:id, :lead, :prospector, :date, 'SCHEDULED')
                        """)
                .param("id", id).param("lead", lead.getId()).param("prospector", me.prospector().getId())
                .param("date", calendar.today().plusDays(1)).update();
        return id;
    }
}
