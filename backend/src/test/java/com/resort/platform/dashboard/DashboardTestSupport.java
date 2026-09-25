package com.resort.platform.dashboard;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.users.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * Utilitários dos testes do dashboard, com dados fictícios. Cada teste vive num dia só seu, longe dos
 * outros e do período padrão (30 dias) dos vizinhos; os números exatos usam o filtro de Prospector, porque
 * o banco é compartilhado pela suíte.
 */
abstract class DashboardTestSupport extends IntegrationTestSupport {

    private static final AtomicLong DAYS = new AtomicLong();

    record Booking(UUID leadId, UUID visitId, UUID invitationId, List<UUID> companionIds) {}

    /** Dia exclusivo do teste, com 45 dias de folga para o período padrão não alcançar o teste vizinho. */
    static LocalDate uniqueDay() {
        return LocalDate.of(2034, 3, 1).plusDays(DAYS.getAndIncrement() * 45 + 10);
    }

    Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(calendar.zone()).toInstant();
    }

    ApiClient gate() throws Exception {
        return loggedIn(Role.GATE);
    }

    ApiClient admin() throws Exception {
        return loggedIn(Role.ADMIN);
    }

    /** Agenda para {@code day} com o relógio às 8h desse dia, pela API do Prospector. */
    Booking book(ProspectorSession me, LocalDate day, int companions) throws Exception {
        clock.set(at(day, 8, 0));
        UUID leadId = testData.lead(me.prospector()).getId();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= companions; i++) {
            list.add(Map.of("name", "Acompanhante " + i, "birthDate", "2010-01-01", "relationship", "FRIEND"));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId);
        body.put("scheduledDate", day.toString());
        body.put("companions", list);
        JsonNode visit = json(me.client().post("/api/visits", body).andExpect(status().isCreated()));
        UUID visitId = UUID.fromString(visit.get("id").asString());
        List<UUID> companionIds =
                visit.get("companions").valueStream().map(c -> UUID.fromString(c.get("id").asString())).toList();
        UUID invitationId = jdbc.sql("SELECT id FROM invitations WHERE visit_id = :v AND status = 'ACTIVE'")
                .param("v", visitId).query(UUID.class).single();
        return new Booking(leadId, visitId, invitationId, companionIds);
    }

    /** Registra a entrada pela Portaria em {@code when}, com os acompanhantes informados. */
    void enter(ApiClient gate, Booking booking, List<UUID> present, Instant when) throws Exception {
        clock.set(when);
        gate.post("/api/access/register", Map.of("invitationId", booking.invitationId(), "presentCompanionIds", present))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("AUTHORIZED"));
    }

    void enter(ApiClient gate, Booking booking, Instant when) throws Exception {
        enter(gate, booking, booking.companionIds(), when);
    }

    /** Visita que não compareceu: o efeito do job noturno (D-091), sem rodá-lo sobre o banco inteiro. */
    void markNoShow(Booking booking) {
        jdbc.sql("UPDATE invitations SET status = 'EXPIRED' WHERE id = :i").param("i", booking.invitationId()).update();
        jdbc.sql("UPDATE visits SET status = 'NO_SHOW' WHERE id = :v").param("v", booking.visitId()).update();
    }

    void cancel(ProspectorSession me, Booking booking) throws Exception {
        me.client().patch("/api/visits/" + booking.visitId() + "/cancel", Map.of()).andExpect(status().isOk());
    }

    /** Remarca e devolve o id da visita nova. */
    UUID reschedule(ProspectorSession me, Booking booking, LocalDate newDate) throws Exception {
        JsonNode created = json(me.client().post("/api/visits/" + booking.visitId() + "/reschedule",
                Map.of("scheduledDate", newDate.toString())).andExpect(status().isOk()));
        return UUID.fromString(created.get("id").asString());
    }

    void discard(ApiClient admin, UUID leadId) throws Exception {
        admin.patch("/api/leads/" + leadId + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
    }

    void assign(ApiClient admin, UUID leadId, UUID prospectorId) throws Exception {
        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(leadId), "prospectorId", prospectorId))
                .andExpect(status().isOk());
    }

    static String period(LocalDate from, LocalDate to) {
        return "from=" + from + "&to=" + to;
    }

    static String query(String... parts) {
        return parts.length == 0 ? "" : "?" + String.join("&", parts);
    }

    JsonNode summary(ApiClient client, String... parts) throws Exception {
        return json(client.get("/api/dashboard/summary" + query(parts)).andExpect(status().isOk()));
    }

    JsonNode visitsByDay(ApiClient client, String... parts) throws Exception {
        return json(client.get("/api/dashboard/visits-by-day" + query(parts)).andExpect(status().isOk()));
    }

    JsonNode accessByDay(ApiClient client, String... parts) throws Exception {
        return json(client.get("/api/dashboard/access-by-day" + query(parts)).andExpect(status().isOk()));
    }

    /** Linha de uma série pela data. */
    static JsonNode day(JsonNode series, LocalDate date) {
        return series.get("days").valueStream()
                .filter(d -> d.get("date").asString().equals(date.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("dia ausente na série: " + date));
    }

    JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    long auditTotal() {
        return jdbc.sql("SELECT count(*) FROM audit_logs").query(Long.class).single();
    }
}
