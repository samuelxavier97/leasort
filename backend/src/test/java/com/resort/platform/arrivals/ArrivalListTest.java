package com.resort.platform.arrivals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** GET /api/arrivals (§16.6, D-095). */
class ArrivalListTest extends ArrivalTestSupport {

    // H2
    @Test
    void adminSeesEveryArrivalOfTheDayMostRecentFirstWithTheListFields() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession ana = loggedInProspector();
        ProspectorSession bia = loggedInProspector();
        Booking first = book(ana, day, 0);
        Booking second = book(bia, day, 2);
        ApiClient gate = gate();
        enter(gate, first, at(day, 9, 10));
        enter(gate, second, at(day, 11, 45));

        clock.set(at(day, 12, 0));
        JsonNode response = arrivals(admin(), "");

        assertThat(response.get("date").asString()).isEqualTo(day.toString());
        assertThat(visitIds(response)).containsExactly(second.visitId(), first.visitId());
        JsonNode latest = response.get("arrivals").get(0);
        assertThat(latest.propertyNames()).containsExactlyInAnyOrder(
                "visitId", "entryAt", "leadName", "companionsPresent", "prospectorName");
        assertThat(latest.get("entryAt").asString()).isEqualTo(at(day, 11, 45).toString());
        assertThat(latest.get("leadName").asString()).isEqualTo(leadName(second.leadId()));
        assertThat(latest.get("companionsPresent").asInt()).isEqualTo(2);
        assertThat(latest.get("prospectorName").asString()).isEqualTo(bia.user().getName());
    }

    // H3
    @Test
    void onlyAuthorizedEntriesAreArrivals() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        Booking entered = book(me, day, 0);
        Booking scheduledOnly = book(me, day, 0);
        Booking cancelled = book(me, day, 0);
        me.client().patch("/api/visits/" + cancelled.visitId() + "/cancel", Map.of()).andExpect(status().isOk());
        ApiClient gate = gate();
        clock.set(at(day, 10, 0));
        String cancelledCode = codeOf(cancelled.invitationId());
        gate.post("/api/access/validate", Map.of("code", cancelledCode)).andExpect(jsonPath("$.result").value("DENIED"));
        gate.post("/api/access/validate", Map.of("code", "ZZZZZZZZZZ")).andExpect(jsonPath("$.result").value("DENIED"));
        enter(gate, entered, at(day, 10, 5));
        // Visita que não compareceu: o job de expiração do dia seguinte a marca NO_SHOW.
        Booking noShow = book(me, day, 0);
        jdbc.sql("UPDATE visits SET status = 'NO_SHOW' WHERE id = :v").param("v", noShow.visitId()).update();

        clock.set(at(day, 18, 0));
        JsonNode response = arrivals(admin(), "");

        assertThat(visitIds(response)).containsExactly(entered.visitId());
        assertThat(visitIds(response)).doesNotContain(scheduledOnly.visitId(), cancelled.visitId(), noShow.visitId());
    }

    // H4
    @Test
    void withoutDateTheListIsTodayAndAdminCanAskForAnotherDay() throws Exception {
        LocalDate day = uniqueDay();
        LocalDate next = day.plusDays(1);
        ProspectorSession me = loggedInProspector();
        Booking yesterday = book(me, day, 0);
        Booking today = book(me, next, 0);
        ApiClient gate = gate();
        enter(gate, yesterday, at(day, 15, 0));
        enter(gate, today, at(next, 9, 0));

        clock.set(at(next, 10, 0));
        ApiClient admin = admin();
        JsonNode byDefault = arrivals(admin, "");
        JsonNode previous = arrivals(admin, "?date=" + day);

        assertThat(byDefault.get("date").asString()).isEqualTo(next.toString());
        assertThat(visitIds(byDefault)).containsExactly(today.visitId());
        assertThat(previous.get("date").asString()).isEqualTo(day.toString());
        assertThat(visitIds(previous)).containsExactly(yesterday.visitId());
    }

    // H5
    @Test
    void theDayOfAnEntryIsTheOperationDayNotTheUtcDay() throws Exception {
        LocalDate day = uniqueDay();
        LocalDate next = day.plusDays(1);
        ProspectorSession me = loggedInProspector();
        Booking late = book(me, day, 0);
        Booking early = book(me, next, 0);
        ApiClient gate = gate();
        enter(gate, late, at(day, 23, 59));
        enter(gate, early, at(next, 0, 1));
        // 23:59 no fuso da operação já é o dia seguinte em UTC.
        assertThat(at(day, 23, 59).toString()).startsWith(next.toString());

        ApiClient admin = admin();
        assertThat(visitIds(arrivals(admin, "?date=" + day))).containsExactly(late.visitId());
        assertThat(visitIds(arrivals(admin, "?date=" + next))).containsExactly(early.visitId());

        ApiClient host = host();
        clock.set(at(day, 23, 59).plusSeconds(30));
        assertThat(visitIds(arrivals(host, ""))).containsExactly(late.visitId());
        clock.set(at(next, 0, 1).plusSeconds(30));
        assertThat(visitIds(arrivals(host, ""))).containsExactly(early.visitId());
    }

    // H6
    @Test
    void companionsPresentCountsOnlyThoseWhoEntered() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        Booking three = book(me, day, 3);
        Booking none = book(me, day, 0);
        ApiClient gate = gate();
        enter(gate, three, three.companionIds().subList(0, 2), at(day, 10, 0));
        enter(gate, none, at(day, 11, 0));

        clock.set(at(day, 12, 0));
        JsonNode list = arrivals(admin(), "").get("arrivals");

        assertThat(list.get(0).get("visitId").asString()).isEqualTo(none.visitId().toString());
        assertThat(list.get(0).get("companionsPresent").asInt()).isZero();
        assertThat(list.get(1).get("companionsPresent").asInt()).isEqualTo(2);
    }

    // H7
    @Test
    void prospectorSeesArrivalsAsResponsibleOrCurrentOwner() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession responsible = loggedInProspector();
        ProspectorSession newOwner = loggedInProspector();
        ProspectorSession stranger = loggedInProspector();
        Booking booking = book(responsible, day, 0);
        Booking own = book(stranger, day, 0);
        enter(gate(), booking, at(day, 10, 0));
        enter(gate(), own, at(day, 10, 30));
        admin().patch("/api/leads/assign", Map.of("leadIds", List.of(booking.leadId()), "prospectorId", newOwner.prospector().getId()))
                .andExpect(status().isOk());

        clock.set(at(day, 12, 0));
        assertThat(visitIds(arrivals(responsible.client(), ""))).containsExactly(booking.visitId());
        assertThat(visitIds(arrivals(newOwner.client(), ""))).containsExactly(booking.visitId());
        assertThat(visitIds(arrivals(stranger.client(), ""))).containsExactly(own.visitId());
        // Qualquer data na API para o PROSPECTOR (D-095); nada de outro dia aparece.
        assertThat(visitIds(arrivals(responsible.client(), "?date=" + day.minusDays(1)))).isEmpty();
    }

    // H8
    @Test
    void hostOnlyAsksForToday() throws Exception {
        LocalDate day = uniqueDay();
        clock.set(at(day, 12, 0));
        ApiClient host = host();

        assertThat(arrivals(host, "").get("date").asString()).isEqualTo(day.toString());
        assertThat(arrivals(host, "?date=" + day).get("date").asString()).isEqualTo(day.toString());
        for (LocalDate other : List.of(day.minusDays(1), day.plusDays(1))) {
            host.get("/api/arrivals?date=" + other)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ARRIVALS_DATE_NOT_ALLOWED"));
        }
    }

    // H9
    @Test
    void hostTodayIsTheOperationDay() throws Exception {
        LocalDate day = uniqueDay();
        ApiClient host = host();

        clock.set(at(day, 23, 30));
        assertThat(clock.instant().toString()).startsWith(day.plusDays(1).toString());
        host.get("/api/arrivals?date=" + day).andExpect(status().isOk());
        host.get("/api/arrivals?date=" + day.plusDays(1)).andExpect(status().isForbidden());

        clock.set(at(day.plusDays(1), 0, 30));
        host.get("/api/arrivals?date=" + day.plusDays(1)).andExpect(status().isOk());
        host.get("/api/arrivals?date=" + day).andExpect(status().isForbidden());
    }

    // H11: parâmetro malformado segue a convenção dos demais (400 BAD_REQUEST, ProblemDetailsCodeTest).
    @Test
    void invalidDateIsABadRequest() throws Exception {
        for (String bad : List.of("24/09/2026", "2026-13-01", "hoje")) {
            admin().get("/api/arrivals?date=" + bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
    }

    private String leadName(UUID leadId) {
        return jdbc.sql("SELECT name FROM leads WHERE id = :id").param("id", leadId).query(String.class).single();
    }

    private String codeOf(UUID invitationId) {
        return jdbc.sql("SELECT code FROM invitations WHERE id = :id").param("id", invitationId).query(String.class).single().trim();
    }
}
