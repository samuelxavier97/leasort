package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.FakeCpf;
import com.resort.platform.leads.Lead;
import com.resort.platform.users.Role;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/** Remarcação, cancelamento e descarte (§7.2, D-009, D-039, D-063, D-076, D-077). */
class VisitLifecycleTest extends VisitTestSupport {

    @Test
    void rescheduleCancelsOldAndCopiesEverythingIntoANewVisit() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        String cpf = FakeCpf.generate();
        JsonNode old = json(schedule(me.client(), lead.getId(), calendar.today().plusDays(2),
                List.of(companion("Cônjuge", cpf, "1980-02-02", "SPOUSE"), companion("Filho"))));
        UUID oldId = UUID.fromString(old.get("id").asString());

        JsonNode fresh = json(me.client().post("/api/visits/" + oldId + "/reschedule",
                        Map.of("scheduledDate", calendar.today().plusDays(9).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledDate").value(calendar.today().plusDays(9).toString()))
                .andExpect(jsonPath("$.notes").value("Nota operacional fictícia"))
                .andExpect(jsonPath("$.hostNotes").value("Prefere conhecer a área de lazer"))
                .andExpect(jsonPath("$.prospector.id").value(me.prospector().getId().toString())));
        UUID newId = UUID.fromString(fresh.get("id").asString());

        assertThat(newId).isNotEqualTo(oldId);
        assertThat(visitStatus(oldId)).isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT cancelled_at IS NOT NULL FROM visits WHERE id = :id").param("id", oldId)
                .query(Boolean.class).single()).isTrue();
        List<String> oldCompanionIds = old.get("companions").valueStream().map(c -> c.get("id").asString()).toList();
        List<String> newCompanionIds = fresh.get("companions").valueStream().map(c -> c.get("id").asString()).toList();
        assertThat(newCompanionIds).hasSize(2).doesNotContainAnyElementsOf(oldCompanionIds);
        assertThat(jdbc.sql("SELECT cpf FROM visit_companions WHERE visit_id = :id AND cpf IS NOT NULL")
                .param("id", newId).query(String.class).list()).containsExactly(cpf);
        assertThat(leadStatus(lead.getId())).isEqualTo("VISIT_SCHEDULED");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE action = 'VISIT_RESCHEDULED' AND entity_id = :old
                          AND metadata ->> 'newVisitId' = :new
                        """).param("old", oldId).param("new", newId.toString()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE action = 'VISIT_CREATED' AND entity_id = :new
                          AND metadata ->> 'rescheduledFrom' = :old
                        """).param("new", newId).param("old", oldId.toString()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'LEAD_STATUS_CHANGED' AND entity_id = :id")
                .param("id", lead.getId()).query(Long.class).single()).as("só o do agendamento original").isEqualTo(1);
    }

    @Test
    void rescheduleValidations() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID id = scheduledId(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(3), List.of());

        reschedule(me, id, calendar.today().minusDays(1)).andExpect(jsonPath("$.code").value("SCHEDULED_DATE_IN_PAST"));
        reschedule(me, id, calendar.today().plusDays(3)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SAME_DATE"));
        reschedule(me, id, calendar.today().plusMonths(12).plusDays(1)).andExpect(jsonPath("$.code").value("SCHEDULED_DATE_TOO_FAR"));
        UUID newId = UUID.fromString(json(reschedule(me, id, calendar.today().plusMonths(12)).andExpect(status().isOk())).get("id").asString());

        reschedule(me, id, calendar.today().plusDays(5)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_TRANSITION"));
        assertThat(scheduledCount(testDataLeadOf(newId))).isEqualTo(1);
    }

    @Test
    void rescheduleWithTheMaximumOfCompanionsKeepsTheIndexHappy() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID id = scheduledId(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), companions(6));

        reschedule(me, id, calendar.today().plusDays(1)).andExpect(status().isOk()).andExpect(jsonPath("$.companions.length()").value(6));
    }

    @Test
    void cancelReturnsLeadToContactedAndAllowsANewSchedule() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        UUID id = scheduledId(me.client(), lead.getId(), calendar.today().plusDays(1), List.of());

        me.client().patch("/api/visits/" + id + "/cancel", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.canEdit").value(false));

        assertThat(leadStatus(lead.getId())).isEqualTo("CONTACTED");
        assertThat(auditCount("VISIT_CANCELLED", id)).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE action = 'LEAD_STATUS_CHANGED' AND entity_id = :id
                          AND metadata ->> 'from' = 'VISIT_SCHEDULED' AND metadata ->> 'to' = 'CONTACTED'
                        """).param("id", lead.getId()).query(Long.class).single()).isEqualTo(1);
        schedule(me.client(), lead.getId(), calendar.today().plusDays(2), List.of()).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "COMPLETED", "NO_SHOW"})
    void cancelOnlyFromScheduled(String status) throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID id = scheduledId(me.client(), testData.lead(me.prospector()).getId(), calendar.today(), List.of());
        forceVisitStatus(id, status);

        me.client().patch("/api/visits/" + id + "/cancel", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_TRANSITION"));
    }

    @Test
    void discardingTheLeadCancelsTheScheduledVisitInTheSameTransaction() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        UUID past = scheduledId(me.client(), lead.getId(), calendar.today(), List.of());
        forceVisitStatus(past, "COMPLETED");
        jdbc.sql("UPDATE leads SET status = 'VISITED' WHERE id = :id").param("id", lead.getId()).update();
        UUID scheduled = scheduledId(me.client(), lead.getId(), calendar.today().plusDays(4), List.of());

        me.client().patch("/api/leads/" + lead.getId() + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());

        assertThat(leadStatus(lead.getId())).isEqualTo("CANCELLED");
        assertThat(visitStatus(scheduled)).isEqualTo("CANCELLED");
        assertThat(visitStatus(past)).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE action = 'VISIT_CANCELLED' AND entity_id = :id
                          AND metadata ->> 'reason' = 'LEAD_DISCARDED'
                        """).param("id", scheduled).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void reactivationDoesNotRestoreTheVisitButAllowsANewOne() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        UUID visit = scheduledId(me.client(), lead.getId(), calendar.today().plusDays(1), List.of());
        me.client().patch("/api/leads/" + lead.getId() + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
        schedule(me.client(), lead.getId(), calendar.today().plusDays(1), List.of())
                .andExpect(jsonPath("$.code").value("LEAD_INACTIVE"));

        loggedIn(Role.ADMIN).patch("/api/leads/" + lead.getId() + "/status", Map.of("status", "NEW")).andExpect(status().isOk());

        assertThat(visitStatus(visit)).isEqualTo("CANCELLED");
        schedule(me.client(), lead.getId(), calendar.today().plusDays(1), List.of()).andExpect(status().isCreated());
    }

    @Test
    void reassignmentKeepsTheVisitResponsible() throws Exception {
        ProspectorSession former = loggedInProspector();
        ProspectorSession current = loggedInProspector();
        Lead lead = testData.lead(former.prospector());
        UUID visit = scheduledId(former.client(), lead.getId(), calendar.today().plusDays(1), List.of());

        loggedIn(Role.ADMIN).patch("/api/leads/assign",
                Map.of("leadIds", List.of(lead.getId()), "prospectorId", current.prospector().getId())).andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT prospector_id FROM visits WHERE id = :id").param("id", visit).query(UUID.class).single())
                .isEqualTo(former.prospector().getId());
        // A remarcação pelo novo dono cria a nova visita já com ele como responsável (RN03).
        current.client().post("/api/visits/" + visit + "/reschedule", Map.of("scheduledDate", calendar.today().plusDays(2).toString()))
                .andExpect(jsonPath("$.prospector.id").value(current.prospector().getId().toString()));
    }

    private org.springframework.test.web.servlet.ResultActions reschedule(ProspectorSession me, UUID id, java.time.LocalDate date)
            throws Exception {
        return me.client().post("/api/visits/" + id + "/reschedule", Map.of("scheduledDate", date.toString()));
    }

    private UUID testDataLeadOf(UUID visitId) {
        return jdbc.sql("SELECT lead_id FROM visits WHERE id = :id").param("id", visitId).query(UUID.class).single();
    }
}
