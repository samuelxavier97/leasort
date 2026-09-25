package com.resort.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.resort.platform.ApiClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Séries por dia e virada do dia (§16.7, D-098). */
class DashboardSeriesTest extends DashboardTestSupport {

    // D8
    @Test
    void visitsByDayBringsEveryDayOfThePeriodWithCountsByStatus() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ProspectorSession other = loggedInProspector();
        ApiClient admin = admin();
        Booking completed = book(me, day.minusDays(2), 0);
        enter(gate(), completed, at(day.minusDays(2), 9, 0));
        markNoShow(book(me, day.minusDays(1), 0));
        cancel(me, book(me, day, 0));
        reschedule(me, book(me, day, 0), day.plusDays(1));
        book(me, day.plusDays(1), 0);
        clock.set(at(day, 12, 0));
        String scope = "prospectorId=" + me.prospector().getId();
        String range = period(day.minusDays(3), day.plusDays(1));

        JsonNode series = visitsByDay(admin, scope, range);
        assertThat(series.get("from").asString()).isEqualTo(day.minusDays(3).toString());
        assertThat(series.get("to").asString()).isEqualTo(day.plusDays(1).toString());
        assertThat(series.get("days").valueStream().map(d -> d.get("date").asString()).toList()).containsExactly(
                day.minusDays(3).toString(), day.minusDays(2).toString(), day.minusDays(1).toString(),
                day.toString(), day.plusDays(1).toString());
        assertThat(rows(series)).containsExactly(
                List.of(0L, 0L, 0L, 0L),
                List.of(0L, 1L, 0L, 0L),
                List.of(0L, 0L, 1L, 0L),
                List.of(0L, 0L, 0L, 1L),
                List.of(2L, 0L, 0L, 0L));
        assertThat(rows(visitsByDay(me.client(), range))).isEqualTo(rows(series));
        assertThat(rows(visitsByDay(other.client(), range))).allMatch(row -> row.equals(List.of(0L, 0L, 0L, 0L)));
    }

    // D9
    @Test
    void accessByDayCountsAuthorizedByEntryAndDeniedByAttempt() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ApiClient admin = admin();
        ApiClient gate = gate();
        Booking entered = book(me, day.minusDays(1), 0);
        enter(gate, entered, at(day.minusDays(1), 18, 0));
        Booking cancelled = book(me, day, 0);
        String cancelledCode = jdbc.sql("SELECT code FROM invitations WHERE id = :i").param("i", cancelled.invitationId())
                .query(String.class).single().trim();
        cancel(me, cancelled);
        clock.set(at(day, 10, 0));
        gate.post("/api/access/validate", Map.of("code", cancelledCode)).andExpect(jsonPath("$.denialReason").value("CANCELLED"));
        gate.post("/api/access/validate", Map.of("code", "ZZZZZZZZZZ")).andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        String range = period(day.minusDays(2), day);

        JsonNode all = accessByDay(admin, range);
        assertThat(all.get("days").valueStream().map(d -> d.get("date").asString()).toList())
                .containsExactly(day.minusDays(2).toString(), day.minusDays(1).toString(), day.toString());
        assertThat(day(all, day.minusDays(2)).get("authorized").asLong() + day(all, day.minusDays(2)).get("denied").asLong()).isZero();
        assertThat(day(all, day.minusDays(1)).get("authorized").asLong()).isEqualTo(1);
        assertThat(day(all, day).get("denied").asLong()).isEqualTo(2);

        // Com o filtro, só o que está ligado a um convite do Prospector: a negativa por código inválido sai.
        JsonNode mine = accessByDay(admin, range, "prospectorId=" + me.prospector().getId());
        assertThat(day(mine, day.minusDays(1)).get("authorized").asLong()).isEqualTo(1);
        assertThat(day(mine, day).get("denied").asLong()).isEqualTo(1);
        assertThat(day(mine, day).get("authorized").asLong()).isZero();
    }

    // D10
    @Test
    void dayBoundaryFollowsTheOperationTimezone() throws Exception {
        LocalDate day = uniqueDay();
        LocalDate next = day.plusDays(1);
        ProspectorSession me = loggedInProspector();
        ApiClient admin = admin();
        ApiClient gate = gate();
        Booking late = book(me, day, 0);
        Booking pending = book(me, day, 0);
        Booking early = book(me, next, 0);
        enter(gate, late, at(day, 23, 59));
        enter(gate, early, at(next, 0, 1));
        assertThat(at(day, 23, 59).toString()).startsWith(next.toString());
        String scope = "prospectorId=" + me.prospector().getId();
        String range = period(day, next);

        JsonNode access = accessByDay(admin, range, scope);
        assertThat(day(access, day).get("authorized").asLong()).isEqualTo(1);
        assertThat(day(access, next).get("authorized").asLong()).isEqualTo(1);
        JsonNode visits = visitsByDay(admin, range, scope);
        assertThat(day(visits, day).get("completed").asLong()).isEqualTo(1);
        assertThat(day(visits, day).get("scheduled").asLong()).isEqualTo(1);
        assertThat(day(visits, next).get("completed").asLong()).isEqualTo(1);

        clock.set(at(day, 23, 59).plusSeconds(30));
        JsonNode beforeMidnight = summary(me.client());
        assertThat(beforeMidnight.get("today").asString()).isEqualTo(day.toString());
        // Hoje: a que entrou às 23:59 e a pendente; agendada e convite ativo: só a pendente (a de amanhã já entrou).
        assertThat(beforeMidnight.get("visitsToday").asLong()).isEqualTo(2);
        assertThat(beforeMidnight.get("scheduledVisits").asLong()).isEqualTo(1);
        assertThat(beforeMidnight.get("activeInvitations").asLong()).isEqualTo(1);

        clock.set(at(next, 0, 1));
        JsonNode afterMidnight = summary(me.client());
        assertThat(afterMidnight.get("today").asString()).isEqualTo(next.toString());
        assertThat(afterMidnight.get("visitsToday").asLong()).isEqualTo(1);
        assertThat(afterMidnight.get("scheduledVisits").asLong()).isZero();
        // O convite de ontem deixou de valer à meia-noite, antes de o job rodar (D-091).
        assertThat(afterMidnight.get("activeInvitations").asLong()).isZero();
        assertThat(jdbc.sql("SELECT status FROM invitations WHERE id = :i").param("i", pending.invitationId())
                .query(String.class).single()).isEqualTo("ACTIVE");
    }

    private static List<List<Long>> rows(JsonNode series) {
        return series.get("days").valueStream()
                .map(d -> List.of(d.get("scheduled").asLong(), d.get("completed").asLong(), d.get("noShow").asLong(),
                        d.get("cancelled").asLong()))
                .toList();
    }
}
