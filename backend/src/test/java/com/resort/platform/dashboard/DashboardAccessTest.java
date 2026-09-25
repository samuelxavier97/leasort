package com.resort.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.StatementCounter;
import com.resort.platform.users.Role;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Perfis, escopo e efeitos das rotas do dashboard (§11, D-098). */
class DashboardAccessTest extends DashboardTestSupport {

    private static final List<String> ROUTES = List.of(
            "/api/dashboard/summary", "/api/dashboard/visits-by-day", "/api/dashboard/access-by-day");

    @Autowired
    EntityManagerFactory entityManagerFactory;

    // D1
    @Test
    void gateAndHostAreForbiddenAnonymousIsUnauthorizedAndAccessByDayIsAdminOnly() throws Exception {
        for (Role role : List.of(Role.GATE, Role.HOST)) {
            ApiClient client = loggedIn(role);
            for (String route : ROUTES) {
                client.get(route).andExpect(status().isForbidden());
            }
        }
        for (String route : ROUTES) {
            client().get(route).andExpect(status().isUnauthorized());
        }
        loggedInProspector().client().get("/api/dashboard/access-by-day").andExpect(status().isForbidden());
    }

    // D2
    @Test
    void prospectorNeverChoosesTheScopeAndSeesOnlyOwnNumbers() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession ana = loggedInProspector();
        ProspectorSession bia = loggedInProspector();
        Booking anaVisit = book(ana, day, 1);
        book(ana, day.plusDays(1), 0);
        Booking biaVisit = book(bia, day, 0);
        ApiClient gate = gate();
        enter(gate, anaVisit, at(day, 9, 0));
        enter(gate, biaVisit, at(day, 10, 0));
        clock.set(at(day, 12, 0));

        for (UUID id : List.of(ana.prospector().getId(), bia.prospector().getId())) {
            for (String route : List.of("/api/dashboard/summary", "/api/dashboard/visits-by-day")) {
                ana.client().get(route + "?prospectorId=" + id)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }

        JsonNode anaSummary = summary(ana.client());
        JsonNode biaSummary = summary(bia.client());
        assertThat(anaSummary.get("myLeads").asLong()).isEqualTo(2);
        assertThat(anaSummary.get("scheduledVisits").asLong()).isEqualTo(1);
        assertThat(anaSummary.get("visitsToday").asLong()).isEqualTo(1);
        assertThat(anaSummary.get("activeInvitations").asLong()).isEqualTo(1);
        assertThat(anaSummary.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(anaSummary.get("upcomingVisits")).hasSize(1);
        assertThat(biaSummary.get("myLeads").asLong()).isEqualTo(1);
        assertThat(biaSummary.get("scheduledVisits").asLong()).isZero();
        assertThat(biaSummary.get("completedVisits").asLong()).isEqualTo(1);
        assertThat(biaSummary.get("upcomingVisits")).isEmpty();
        assertThat(anaSummary.propertyNames()).doesNotContain("totalLeads", "assignedLeads", "noShows", "cancellations", "entries");

        assertThat(day(visitsByDay(ana.client()), day).get("completed").asLong()).isEqualTo(1);
        assertThat(day(visitsByDay(ana.client(), period(day, day.plusDays(1))), day.plusDays(1)).get("scheduled").asLong())
                .isEqualTo(1);
        assertThat(day(visitsByDay(bia.client(), period(day, day.plusDays(1))), day.plusDays(1)).get("scheduled").asLong())
                .isZero();
    }

    // D12
    @Test
    void readingChangesNothing() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        Booking booking = book(me, day, 1);
        enter(gate(), booking, at(day, 9, 0));
        clock.set(at(day, 12, 0));
        ApiClient admin = admin();
        long audits = auditTotal();
        Map<String, Object> before = state(booking);

        summary(admin);
        summary(admin, "prospectorId=" + me.prospector().getId());
        visitsByDay(admin);
        accessByDay(admin);
        summary(me.client());
        visitsByDay(me.client());

        assertThat(auditTotal()).isEqualTo(audits);
        assertThat(state(booking)).isEqualTo(before);
    }

    // D13
    @Test
    void dashboardLoadsNoEntitiesAndRunsAFixedNumberOfStatements() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        ApiClient admin = admin();
        ApiClient gate = gate();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        enter(gate, book(me, day, 1), at(day, 9, 0));
        clock.set(at(day, 12, 0));

        long[] withOne = measure(statistics, admin, me);
        for (int i = 0; i < 20; i++) {
            enter(gate, book(me, day, 2), at(day, 9, i + 1));
        }
        book(me, day.plusDays(1), 3);
        clock.set(at(day, 12, 0));
        long[] withMany = measure(statistics, admin, me);

        assertThat(withMany).containsExactly(withOne);
        assertThat(summary(admin, "prospectorId=" + me.prospector().getId()).get("completedVisits").asLong()).isEqualTo(21);
    }

    /** Statements por requisição, com nenhuma entidade ou coleção carregada pelo Hibernate. */
    private long[] measure(Statistics statistics, ApiClient admin, ProspectorSession me) throws Exception {
        List<Runnable> requests = List.of(
                () -> run(() -> summary(admin)),
                () -> run(() -> summary(admin, "prospectorId=" + me.prospector().getId())),
                () -> run(() -> visitsByDay(admin)),
                () -> run(() -> accessByDay(admin)),
                () -> run(() -> summary(me.client())),
                () -> run(() -> visitsByDay(me.client())));
        long[] counts = new long[requests.size()];
        for (int i = 0; i < requests.size(); i++) {
            statistics.clear();
            long before = StatementCounter.count();
            requests.get(i).run();
            counts[i] = StatementCounter.count() - before;
            assertThat(statistics.getEntityLoadCount() + statistics.getEntityFetchCount()
                    + statistics.getCollectionLoadCount() + statistics.getCollectionFetchCount())
                    .as("entidades carregadas na requisição " + i).isZero();
            assertThat(counts[i]).as("statements na requisição " + i).isPositive();
        }
        return counts;
    }

    private interface Request {
        void call() throws Exception;
    }

    private static void run(Request request) {
        try {
            request.call();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // D14
    @Test
    void unknownProspectorFilterIsNotFound() throws Exception {
        ApiClient admin = admin();
        for (String route : ROUTES) {
            admin.get(route + "?prospectorId=" + UUID.randomUUID())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROSPECTOR_NOT_FOUND"));
        }
    }

    private Map<String, Object> state(Booking booking) {
        return jdbc.sql("""
                        SELECT v.status AS visit, v.updated_at AS visit_updated, l.status AS lead, l.updated_at AS lead_updated,
                               i.status AS invitation,
                               (SELECT count(*) FROM access_records r WHERE r.invitation_id = i.id) AS accesses
                        FROM visits v JOIN leads l ON l.id = v.lead_id JOIN invitations i ON i.visit_id = v.id
                        WHERE v.id = :v
                        """)
                .param("v", booking.visitId()).query().singleRow();
    }
}
