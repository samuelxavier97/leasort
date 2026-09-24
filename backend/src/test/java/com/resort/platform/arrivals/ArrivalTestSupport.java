package com.resort.platform.arrivals;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
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
 * Utilitários dos testes de chegadas, com dados fictícios. Cada teste usa um dia só seu, longe de hoje:
 * o banco é compartilhado pela suíte e a lista de ADMIN e HOST traz todas as chegadas do dia.
 */
abstract class ArrivalTestSupport extends IntegrationTestSupport {

    private static final AtomicLong DAYS = new AtomicLong();

    record Booking(UUID leadId, UUID visitId, UUID invitationId, List<UUID> companionIds) {}

    /** Companheiro fictício com parentesco e data de nascimento; CPF gerado para conferir que não vaza. */
    record Companion(String name, String relationship, LocalDate birthDate) {}

    /** Dia exclusivo do teste, espaçado para caber D−1 e D+1 sem colidir com outro teste. */
    static LocalDate uniqueDay() {
        return LocalDate.of(2031, 1, 1).plusDays(DAYS.getAndIncrement() * 4 + 1);
    }

    Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(calendar.zone()).toInstant();
    }

    ApiClient gate() throws Exception {
        return loggedIn(Role.GATE);
    }

    ApiClient host() throws Exception {
        return loggedIn(Role.HOST);
    }

    ApiClient admin() throws Exception {
        return loggedIn(Role.ADMIN);
    }

    /** Agenda para {@code day} com o relógio nesse dia, pela API do Prospector. */
    Booking book(ProspectorSession me, LocalDate day, List<Companion> companions, String hostNotes) throws Exception {
        clock.set(at(day, 8, 0));
        UUID leadId = testData.lead(me.prospector()).getId();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Companion c : companions) {
            list.add(Map.of("name", c.name(), "cpf", FakeCpf.generate(), "birthDate", c.birthDate().toString(),
                    "relationship", c.relationship()));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId);
        body.put("scheduledDate", day.toString());
        body.put("companions", list);
        body.put("notes", "Nota interna da visita " + leadId);
        if (hostNotes != null) {
            body.put("hostNotes", hostNotes);
        }
        JsonNode visit = json(me.client().post("/api/visits", body).andExpect(status().isCreated()));
        UUID visitId = UUID.fromString(visit.get("id").asString());
        List<UUID> companionIds =
                visit.get("companions").valueStream().map(c -> UUID.fromString(c.get("id").asString())).toList();
        UUID invitationId = jdbc.sql("SELECT id FROM invitations WHERE visit_id = :v AND status = 'ACTIVE'")
                .param("v", visitId).query(UUID.class).single();
        return new Booking(leadId, visitId, invitationId, companionIds);
    }

    Booking book(ProspectorSession me, LocalDate day, int companions) throws Exception {
        List<Companion> list = new ArrayList<>();
        for (int i = 1; i <= companions; i++) {
            list.add(new Companion("Acompanhante " + i, "FRIEND", LocalDate.of(2010, 1, 1)));
        }
        return book(me, day, list, null);
    }

    /** Registra a entrada pela Portaria no instante {@code when}. */
    void enter(ApiClient gate, Booking booking, List<UUID> present, Instant when) throws Exception {
        clock.set(when);
        gate.post("/api/access/register", Map.of("invitationId", booking.invitationId(), "presentCompanionIds", present))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("AUTHORIZED"));
    }

    void enter(ApiClient gate, Booking booking, Instant when) throws Exception {
        enter(gate, booking, booking.companionIds(), when);
    }

    JsonNode arrivals(ApiClient client, String query) throws Exception {
        return json(client.get("/api/arrivals" + query).andExpect(status().isOk()));
    }

    /** Ids das visitas da lista, na ordem da resposta. */
    List<UUID> visitIds(JsonNode response) {
        return response.get("arrivals").valueStream().map(a -> UUID.fromString(a.get("visitId").asString())).toList();
    }

    JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }

    long auditTotal() {
        return jdbc.sql("SELECT count(*) FROM audit_logs").query(Long.class).single();
    }
}
