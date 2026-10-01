package com.resort.platform.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.ApiClient;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** GET /api/dashboard/prospector-performance (D-128): ordem, empate e período. O 403 está no DashboardAccessTest. */
class DashboardPerformanceTest extends DashboardTestSupport {

    // D18
    @Test
    void ordersByCompletedThenRateThenNameWithinThePeriod() throws Exception {
        LocalDate day = uniqueDay();
        ApiClient admin = admin();
        ApiClient gate = gate();
        ProspectorSession top = loggedInProspector();
        ProspectorSession tieHighRate = loggedInProspector();
        ProspectorSession tieLowRate = loggedInProspector();
        ProspectorSession onlyNoShow = loggedInProspector();
        ProspectorSession outside = loggedInProspector();
        ProspectorSession cancelledOnly = loggedInProspector();
        // top: 3 realizadas e 1 sem comparecimento.
        for (int i = 0; i < 3; i++) {
            enter(gate, book(top, day.minusDays(1), 0), at(day.minusDays(1), 9, i));
        }
        markNoShow(book(top, day.minusDays(2), 0));
        // Empate em 2 realizadas: a taxa decide (2/2 antes de 2/3).
        for (int i = 0; i < 2; i++) {
            enter(gate, book(tieHighRate, day.minusDays(1), 0), at(day.minusDays(1), 10, i));
            enter(gate, book(tieLowRate, day.minusDays(1), 0), at(day.minusDays(1), 11, i));
        }
        markNoShow(book(tieLowRate, day.minusDays(3), 0));
        // Só sem comparecimento: entra, com taxa 0.
        markNoShow(book(onlyNoShow, day.minusDays(2), 0));
        // Fora do período e só cancelada: não entram.
        enter(gate, book(outside, day.minusDays(10), 0), at(day.minusDays(10), 9, 0));
        cancel(cancelledOnly, book(cancelledOnly, day.minusDays(1), 0));
        clock.set(at(day, 12, 0));

        JsonNode response = performance(admin, period(day.minusDays(5), day));
        assertThat(response.get("from").asString()).isEqualTo(day.minusDays(5).toString());
        List<JsonNode> rows = response.get("prospectors").valueStream()
                .filter(row -> mine(row, top, tieHighRate, tieLowRate, onlyNoShow, outside, cancelledOnly))
                .toList();
        assertThat(rows.stream().map(row -> row.get("prospectorId").asString()).toList()).containsExactly(
                id(top), id(tieHighRate), id(tieLowRate), id(onlyNoShow));
        JsonNode first = rows.getFirst();
        assertThat(first.propertyNames()).containsExactlyInAnyOrder("prospectorId", "name", "completedVisits", "noShows",
                "attendanceRate");
        assertThat(first.get("name").asString()).isEqualTo(top.user().getName());
        assertThat(first.get("completedVisits").asLong()).isEqualTo(3);
        assertThat(first.get("noShows").asLong()).isEqualTo(1);
        assertThat(first.get("attendanceRate").asDouble()).isEqualTo(0.75);
        assertThat(rows.get(1).get("attendanceRate").asDouble()).isEqualTo(1.0);
        assertThat(rows.get(3).get("attendanceRate").asDouble()).isZero();

        // Mesmo empate com a mesma taxa: o nome decide.
        jdbc.sql("UPDATE users SET name = 'Zélia Fictícia' WHERE id = :id").param("id", tieHighRate.user().getId()).update();
        jdbc.sql("UPDATE users SET name = 'Abigail Fictícia' WHERE id = :id").param("id", tieLowRate.user().getId()).update();
        jdbc.sql("UPDATE visits SET status = 'COMPLETED' WHERE prospector_id = :p AND status = 'NO_SHOW'")
                .param("p", tieLowRate.prospector().getId()).update();
        JsonNode tied = performance(admin, period(day.minusDays(5), day));
        List<String> order = tied.get("prospectors").valueStream()
                .filter(row -> mine(row, tieHighRate, tieLowRate))
                .map(row -> row.get("name").asString())
                .toList();
        // Agora tieLowRate tem 3 realizadas (1,0) e passa à frente; o nome só desempata iguais.
        assertThat(order).containsExactly("Abigail Fictícia", "Zélia Fictícia");

        // O período decide: só o de fora aparece de D-12 a D-8.
        List<String> old = performance(admin, period(day.minusDays(12), day.minusDays(8))).get("prospectors").valueStream()
                .filter(row -> mine(row, top, tieHighRate, tieLowRate, onlyNoShow, outside, cancelledOnly))
                .map(row -> row.get("prospectorId").asString())
                .toList();
        assertThat(old).containsExactly(id(outside));
    }

    // D19: empate exato (realizadas e taxa) fica pelo nome.
    @Test
    void exactTieIsBrokenByName() throws Exception {
        LocalDate day = uniqueDay();
        ApiClient admin = admin();
        ApiClient gate = gate();
        ProspectorSession first = loggedInProspector();
        ProspectorSession second = loggedInProspector();
        jdbc.sql("UPDATE users SET name = 'Beatriz Fictícia' WHERE id = :id").param("id", first.user().getId()).update();
        jdbc.sql("UPDATE users SET name = 'Amanda Fictícia' WHERE id = :id").param("id", second.user().getId()).update();
        enter(gate, book(first, day.minusDays(1), 0), at(day.minusDays(1), 9, 0));
        enter(gate, book(second, day.minusDays(1), 0), at(day.minusDays(1), 9, 1));
        clock.set(at(day, 12, 0));

        List<String> names = performance(admin, period(day.minusDays(1), day)).get("prospectors").valueStream()
                .filter(row -> mine(row, first, second))
                .map(row -> row.get("name").asString())
                .toList();
        assertThat(names).containsExactly("Amanda Fictícia", "Beatriz Fictícia");
    }

    private static String id(ProspectorSession session) {
        return session.prospector().getId().toString();
    }

    private static boolean mine(JsonNode row, ProspectorSession... sessions) {
        UUID id = UUID.fromString(row.get("prospectorId").asString());
        for (ProspectorSession session : sessions) {
            if (session.prospector().getId().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
