package com.resort.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** GET /api/dashboard/summary (§16.7, D-098). */
class DashboardSummaryTest extends DashboardTestSupport {

    // D3
    @Test
    void adminSnapshotsCountLeadsVisitsAndInvitationsAsDefined() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ApiClient admin = admin();
        ApiClient gate = gate();
        clock.set(at(day, 8, 0));
        JsonNode globalBefore = summary(admin);

        // Fotografias no dia D: agendadas D+1 e D+2, hoje uma SCHEDULED, uma COMPLETED e uma CANCELLED.
        book(me, day.plusDays(1), 0);
        book(me, day.plusDays(2), 0);
        book(me, day, 0);
        Booking completed = book(me, day, 2);
        enter(gate, completed, at(day, 9, 0));
        Booking cancelled = book(me, day, 0);
        cancel(me, cancelled);
        // Ontem, SCHEDULED com convite ACTIVE ainda não processado pelo job: nem agendada nem convite ativo.
        book(me, day.minusDays(1), 0);
        // Lead descartado fica fora (D-008); Lead sem dono conta no total, não em atribuídos.
        discard(admin, testData.lead(me.prospector()).getId());
        testData.lead(null);
        clock.set(at(day, 12, 0));

        JsonNode mine = summary(admin, "prospectorId=" + me.prospector().getId());
        assertThat(mine.propertyNames()).containsExactlyInAnyOrder("from", "to", "today", "totalLeads", "assignedLeads",
                "scheduledVisits", "visitsToday", "activeInvitations", "completedVisits", "noShows", "cancellations", "entries");
        assertThat(mine.get("today").asString()).isEqualTo(day.toString());
        assertThat(mine.get("totalLeads").asLong()).isEqualTo(6);
        assertThat(mine.get("assignedLeads").asLong()).isEqualTo(6);
        assertThat(mine.get("scheduledVisits").asLong()).isEqualTo(3);
        assertThat(mine.get("visitsToday").asLong()).isEqualTo(2);
        assertThat(mine.get("activeInvitations").asLong()).isEqualTo(3);
        assertThat(mine.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(mine.get("noShows").asLong()).isZero();
        assertThat(mine.get("cancellations").asLong()).isEqualTo(1);
        assertThat(mine.get("entries").asLong()).isEqualTo(3);

        JsonNode globalAfter = summary(admin);
        assertThat(globalAfter.get("totalLeads").asLong() - globalBefore.get("totalLeads").asLong()).isEqualTo(7);
        assertThat(globalAfter.get("assignedLeads").asLong() - globalBefore.get("assignedLeads").asLong()).isEqualTo(6);
        assertThat(globalAfter.get("visitsToday").asLong()).isEqualTo(2);
        assertThat(globalAfter.get("entries").asLong()).isGreaterThanOrEqualTo(3);
    }

    // D4
    @Test
    void periodCountsIncludeBothEndsAndExcludeNeighbours() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ApiClient gate = gate();
        Booking outsideCompleted = book(me, day.minusDays(6), 1);
        enter(gate, outsideCompleted, at(day.minusDays(6), 10, 0));
        Booking firstDayCompleted = book(me, day.minusDays(5), 3);
        enter(gate, firstDayCompleted, firstDayCompleted.companionIds().subList(0, 2), at(day.minusDays(5), 9, 0));
        markNoShow(book(me, day.minusDays(3), 0));
        Booking lastDayCancelled = book(me, day.minusDays(1), 0);
        cancel(me, lastDayCancelled);
        markNoShow(book(me, day, 0));
        clock.set(at(day, 12, 0));
        String scope = "prospectorId=" + me.prospector().getId();

        JsonNode inside = summary(admin(), scope, period(day.minusDays(5), day.minusDays(1)));
        assertThat(inside.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(inside.get("noShows").asLong()).isEqualTo(1);
        assertThat(inside.get("cancellations").asLong()).isEqualTo(1);
        // Pessoas: o Lead mais os 2 acompanhantes presentes (1 desmarcado).
        assertThat(inside.get("entries").asLong()).isEqualTo(3);

        JsonNode wider = summary(admin(), scope, period(day.minusDays(6), day));
        assertThat(wider.get("completedVisits").asLong()).isEqualTo(2);
        assertThat(wider.get("noShows").asLong()).isEqualTo(2);
        assertThat(wider.get("entries").asLong()).isEqualTo(5);

        JsonNode before = summary(admin(), scope, period(day.minusDays(10), day.minusDays(7)));
        assertThat(before.get("completedVisits").asLong() + before.get("noShows").asLong()
                + before.get("cancellations").asLong() + before.get("entries").asLong()).isZero();
    }

    // D5
    @Test
    void cancellationsIgnoreTheOldVisitOfAReschedule() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ApiClient admin = admin();
        Booking direct = book(me, day, 0);
        cancel(me, direct);
        Booking discarded = book(me, day, 0);
        discard(admin, discarded.leadId());
        Booking rescheduled = book(me, day, 0);
        reschedule(me, rescheduled, day.plusDays(2));
        clock.set(at(day, 12, 0));

        JsonNode mine = summary(admin, "prospectorId=" + me.prospector().getId());
        assertThat(mine.get("cancellations").asLong()).isEqualTo(2);
        assertThat(mine.get("scheduledVisits").asLong()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT cancel_reason FROM visits WHERE id = :v").param("v", rescheduled.visitId())
                .query(String.class).single()).isEqualTo("RESCHEDULED");
    }

    // D6
    @Test
    void visitCreditStaysWithTheResponsibleAfterReassignmentAndLeadsFollowTheOwner() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession p1 = loggedInProspector();
        ProspectorSession p2 = loggedInProspector();
        ApiClient admin = admin();
        Booking booking = book(p1, day, 1);
        enter(gate(), booking, at(day, 9, 0));
        assign(admin, booking.leadId(), p2.prospector().getId());
        clock.set(at(day, 12, 0));

        JsonNode p1Own = summary(p1.client());
        JsonNode p2Own = summary(p2.client());
        assertThat(p1Own.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(p1Own.get("myLeads").asLong()).isZero();
        assertThat(p2Own.get("completedVisits").asLong()).isZero();
        assertThat(p2Own.get("myLeads").asLong()).isEqualTo(1);

        JsonNode p1Filter = summary(admin, "prospectorId=" + p1.prospector().getId());
        JsonNode p2Filter = summary(admin, "prospectorId=" + p2.prospector().getId());
        assertThat(p1Filter.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(p1Filter.get("entries").asLong()).isEqualTo(2);
        assertThat(p1Filter.get("totalLeads").asLong()).isZero();
        assertThat(p2Filter.get("completedVisits").asLong()).isZero();
        assertThat(p2Filter.get("entries").asLong()).isZero();
        assertThat(p2Filter.get("totalLeads").asLong()).isEqualTo(1);
        assertThat(day(visitsByDay(admin, "prospectorId=" + p1.prospector().getId()), day).get("completed").asLong()).isEqualTo(1);
        assertThat(day(visitsByDay(admin, "prospectorId=" + p2.prospector().getId()), day).get("completed").asLong()).isZero();
    }

    // D7
    @Test
    void periodDefaultsLimitsAndValidation() throws Exception {
        LocalDate day = uniqueDay();
        clock.set(at(day, 12, 0));
        ApiClient admin = admin();

        JsonNode byDefault = summary(admin);
        assertThat(byDefault.get("from").asString()).isEqualTo(day.minusDays(29).toString());
        assertThat(byDefault.get("to").asString()).isEqualTo(day.toString());

        admin.get("/api/dashboard/summary?from=" + day).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.get("/api/dashboard/summary?to=" + day).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.get("/api/dashboard/summary?" + period(day, day.minusDays(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.get("/api/dashboard/summary?" + period(day.minusDays(365), day)).andExpect(status().isOk());
        admin.get("/api/dashboard/summary?" + period(day.minusDays(366), day)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_TOO_LONG"));
        admin.get("/api/dashboard/visits-by-day?" + period(day.minusDays(366), day)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_TOO_LONG"));
        admin.get("/api/dashboard/summary?from=25/09/2026&to=" + day).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        // Datas futuras valem: há visitas agendadas até 12 meses à frente (D-074).
        admin.get("/api/dashboard/summary?" + period(day, day.plusDays(60))).andExpect(status().isOk());
    }

    // D11
    @Test
    void upcomingVisitsFollowReadRuleOrderAndLimit() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ProspectorSession other = loggedInProspector();
        ApiClient admin = admin();
        // Excluídas: cancelada, realizada e de ontem.
        cancel(me, book(me, day.plusDays(1), 0));
        Booking done = book(me, day, 0);
        enter(gate(), done, at(day, 9, 0));
        book(me, day.minusDays(1), 0);
        // Recebida por reatribuição: o outro é o responsável, eu sou o dono atual.
        Booking received = book(other, day.plusDays(1), 2);
        assign(admin, received.leadId(), me.prospector().getId());
        // Duas no mesmo dia, ordenadas pelo nome do Lead.
        Booking zeta = book(me, day.plusDays(2), 0);
        Booking alfa = book(me, day.plusDays(2), 1);
        jdbc.sql("UPDATE leads SET name = 'Zeta Lead Fictício' WHERE id = :id").param("id", zeta.leadId()).update();
        jdbc.sql("UPDATE leads SET name = 'Alfa Lead Fictício' WHERE id = :id").param("id", alfa.leadId()).update();
        for (int i = 3; i <= 11; i++) {
            book(me, day.plusDays(i), 0);
        }
        clock.set(at(day, 12, 0));

        JsonNode mine = summary(me.client()).get("upcomingVisits");
        assertThat(mine).hasSize(10);
        assertThat(mine.get(0).propertyNames()).containsExactlyInAnyOrder("visitId", "scheduledDate", "leadName",
                "companionsCount", "canEdit");
        assertThat(mine.get(0).get("visitId").asString()).isEqualTo(received.visitId().toString());
        assertThat(mine.get(0).get("scheduledDate").asString()).isEqualTo(day.plusDays(1).toString());
        assertThat(mine.get(0).get("companionsCount").asInt()).isEqualTo(2);
        assertThat(mine.get(0).get("canEdit").asBoolean()).isTrue();
        assertThat(mine.get(1).get("leadName").asString()).isEqualTo("Alfa Lead Fictício");
        assertThat(mine.get(2).get("leadName").asString()).isEqualTo("Zeta Lead Fictício");
        List<String> dates = mine.valueStream().map(v -> v.get("scheduledDate").asString()).toList();
        assertThat(dates).isSorted();
        assertThat(dates.getLast()).isEqualTo(day.plusDays(9).toString());
        assertThat(mine.valueStream().map(v -> v.get("visitId").asString()))
                .doesNotContain(done.visitId().toString());

        // O responsável antigo ainda lê a visita (D-041), mas não a edita.
        JsonNode theirs = summary(other.client()).get("upcomingVisits");
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).get("visitId").asString()).isEqualTo(received.visitId().toString());
        assertThat(theirs.get(0).get("canEdit").asBoolean()).isFalse();
        // O cartão conta por crédito (D-013): a visita recebida não entra no meu, e entra no dele.
        assertThat(summary(me.client()).get("scheduledVisits").asLong()).isEqualTo(11);
        assertThat(summary(other.client()).get("scheduledVisits").asLong()).isEqualTo(1);
    }

    // D2 complemento: o PROSPECTOR escolhe o período, sempre com os dados dele.
    @Test
    void prospectorMayChooseThePeriod() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        Booking old = book(me, day.minusDays(40), 0);
        enter(gate(), old, at(day.minusDays(40), 9, 0));
        clock.set(at(day, 12, 0));

        assertThat(summary(me.client()).get("completedVisits").asLong()).isZero();
        JsonNode chosen = summary(me.client(), period(day.minusDays(45), day.minusDays(35)));
        assertThat(chosen.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(chosen.get("from").asString()).isEqualTo(day.minusDays(45).toString());
        UUID other = loggedInProspector().prospector().getId();
        me.client().get("/api/dashboard/summary?" + period(day.minusDays(45), day) + "&prospectorId=" + other)
                .andExpect(status().isForbidden());
    }
}
