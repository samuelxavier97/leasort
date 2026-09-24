package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.TestData;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;

class VisitCreationTest extends VisitTestSupport {

    @Test
    void ownerSchedulesVisitWithCompanions() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());

        var result = schedule(me.client(), lead.getId(), calendar.today().plusDays(3), List.of(companion("Filha Fictícia")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.prospector.id").value(me.prospector().getId().toString()))
                .andExpect(jsonPath("$.lead.id").value(lead.getId().toString()))
                .andExpect(jsonPath("$.companions.length()").value(1))
                .andExpect(jsonPath("$.hostNotes").value("Prefere conhecer a área de lazer"))
                .andExpect(jsonPath("$.canEdit").value(true));

        UUID visitId = UUID.fromString(json(result).get("id").asString());
        assertThat(leadStatus(lead.getId())).isEqualTo("VISIT_SCHEDULED");
        assertThat(auditCount("VISIT_CREATED", visitId)).isEqualTo(1);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE action = 'LEAD_STATUS_CHANGED' AND entity_id = :id
                          AND metadata ->> 'from' = 'NEW' AND metadata ->> 'to' = 'VISIT_SCHEDULED'
                          AND metadata ->> 'cause' = 'VISIT_CREATED'
                        """).param("id", lead.getId()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void adminSchedulesAndResponsibleIsTheCurrentOwner() throws Exception {
        ProspectorSession owner = loggedInProspector();
        Lead lead = testData.lead(owner.prospector());

        schedule(loggedIn(Role.ADMIN), lead.getId(), calendar.today(), List.of())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prospector.id").value(owner.prospector().getId().toString()));
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"NEW", "CONTACTED", "VISITED"})
    void allowedOriginStatuses(LeadStatus origin) throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector(), null, origin);

        schedule(me.client(), lead.getId(), calendar.today(), List.of()).andExpect(status().isCreated());
    }

    @Test
    void rejectedLeadsCreateNothing() throws Exception {
        ApiClient admin = loggedIn(Role.ADMIN);
        ProspectorSession me = loggedInProspector();
        Lead cancelled = testData.lead(me.prospector(), null, LeadStatus.CANCELLED);
        Lead unassigned = testData.lead(null);
        Lead alreadyScheduled = testData.lead(me.prospector());
        schedule(me.client(), alreadyScheduled.getId(), calendar.today(), List.of()).andExpect(status().isCreated());
        var inactiveOwner = testData.prospectorOf(testData.user(Role.PROSPECTOR, TestData.PASSWORD, false, false));
        Lead ofInactive = testData.lead(inactiveOwner);

        assertRejected(admin, cancelled.getId(), 409, "LEAD_INACTIVE");
        assertRejected(admin, unassigned.getId(), 409, "LEAD_NOT_ASSIGNED");
        assertRejected(admin, alreadyScheduled.getId(), 409, "VISIT_ALREADY_SCHEDULED");
        assertRejected(admin, ofInactive.getId(), 409, "PROSPECTOR_INACTIVE");
        assertThat(scheduledCount(alreadyScheduled.getId())).isEqualTo(1);
    }

    @Test
    void prospectorCannotScheduleForAnotherWallet() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead others = testData.lead(loggedInProspector().prospector());

        assertRejected(me.client(), others.getId(), 404, "LEAD_NOT_FOUND");
        assertRejected(me.client(), UUID.randomUUID(), 404, "LEAD_NOT_FOUND");
        assertThat(leadStatus(others.getId())).isEqualTo("NEW");
    }

    @Test
    void dateMustBeFromTodayUpToTwelveMonths() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate today = calendar.today();

        schedule(me.client(), testData.lead(me.prospector()).getId(), today.minusDays(1), List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULED_DATE_IN_PAST"));
        schedule(me.client(), testData.lead(me.prospector()).getId(), today.plusMonths(12).plusDays(1), List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULED_DATE_TOO_FAR"));
        schedule(me.client(), testData.lead(me.prospector()).getId(), today, List.of()).andExpect(status().isCreated());
        schedule(me.client(), testData.lead(me.prospector()).getId(), today.plusMonths(12), List.of()).andExpect(status().isCreated());
    }

    /** "Hoje" no fuso da operação (America/Sao_Paulo, UTC-3), e não no do servidor (UTC). */
    @Test
    void todayIsComputedInTheOperationTimezoneAcrossMidnight() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate march9 = LocalDate.of(2026, 3, 9);

        clock.set(Instant.parse("2026-03-10T00:30:00Z")); // 21h30 de 09/03 em São Paulo; já 10/03 em UTC
        schedule(me.client(), testData.lead(me.prospector()).getId(), march9, List.of()).andExpect(status().isCreated());

        clock.set(Instant.parse("2026-03-10T02:59:59Z")); // 23h59m59s de 09/03
        schedule(me.client(), testData.lead(me.prospector()).getId(), march9, List.of()).andExpect(status().isCreated());
        schedule(me.client(), testData.lead(me.prospector()).getId(), march9.minusDays(1), List.of())
                .andExpect(jsonPath("$.code").value("SCHEDULED_DATE_IN_PAST"));

        clock.set(Instant.parse("2026-03-10T03:00:00Z")); // 00h00 de 10/03
        schedule(me.client(), testData.lead(me.prospector()).getId(), march9, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHEDULED_DATE_IN_PAST"));
        schedule(me.client(), testData.lead(me.prospector()).getId(), LocalDate.of(2027, 3, 10), List.of())
                .andExpect(status().isCreated());
        schedule(me.client(), testData.lead(me.prospector()).getId(), LocalDate.of(2027, 3, 11), List.of())
                .andExpect(jsonPath("$.code").value("SCHEDULED_DATE_TOO_FAR"));
    }

    @Test
    void databaseIndexIsTheLastSafeguard() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        insertScheduled(lead.getId(), me.prospector().getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insertScheduled(lead.getId(), me.prospector().getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("visits_lead_scheduled_uk");
    }

    @Test
    void inconsistentStateIsStillRejectedWith409() throws Exception {
        ProspectorSession me = loggedInProspector();
        Lead lead = testData.lead(me.prospector());
        // Estado que só uma corrida produziria: visita agendada com o Lead ainda em NEW.
        insertScheduled(lead.getId(), me.prospector().getId());
        jdbc.sql("UPDATE leads SET status = 'NEW' WHERE id = :id").param("id", lead.getId()).update();

        schedule(me.client(), lead.getId(), calendar.today(), List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_ALREADY_SCHEDULED"));
    }

    private void insertScheduled(UUID leadId, UUID prospectorId) {
        jdbc.sql("""
                        INSERT INTO visits (id, lead_id, prospector_id, scheduled_date, status)
                        VALUES (:id, :lead, :prospector, current_date, 'SCHEDULED')
                        """)
                .param("id", UUID.randomUUID()).param("lead", leadId).param("prospector", prospectorId).update();
    }

    private void assertRejected(ApiClient client, UUID leadId, int status, String code) throws Exception {
        schedule(client, leadId, calendar.today().plusDays(1), List.of())
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }
}
