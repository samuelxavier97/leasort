package com.resort.platform.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.users.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Utilitários dos testes de exportação, com dados fictícios. O banco é compartilhado pela suíte: cada teste
 * filtra pelo próprio Prospector ou por um dia só seu.
 */
abstract class ExportTestSupport extends IntegrationTestSupport {

    private static final AtomicLong DAYS = new AtomicLong();

    record Download(MockHttpServletResponse response, String text, List<List<String>> rows) {

        List<String> header() {
            return rows.getFirst();
        }

        List<List<String>> data() {
            return rows.subList(1, rows.size());
        }
    }

    record Booking(UUID leadId, UUID visitId, UUID invitationId, List<UUID> companionIds) {}

    record Companion(String name, String cpf, LocalDate birthDate, String relationship) {}

    static LocalDate uniqueDay() {
        return LocalDate.of(2037, 1, 1).plusDays(DAYS.getAndIncrement() * 4 + 1);
    }

    Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(calendar.zone()).toInstant();
    }

    ApiClient admin() throws Exception {
        return loggedIn(Role.ADMIN);
    }

    ApiClient gate() throws Exception {
        return loggedIn(Role.GATE);
    }

    /** Baixa o arquivo: a resposta é assíncrona (streaming) e termina num segundo despacho. */
    Download download(ApiClient client, String path) throws Exception {
        MvcResult started = client.get(path).andExpect(status().isOk()).andReturn();
        assertThat(started.getRequest().isAsyncStarted()).as("resposta em streaming").isTrue();
        MockHttpServletResponse response = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse();
        String text = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿");
        return new Download(response, text, Rfc4180.parse(text.substring(1)));
    }

    /** Agenda para {@code day} com o relógio às 8h desse dia. */
    Booking book(ProspectorSession me, UUID leadId, LocalDate day, List<Companion> companions) throws Exception {
        clock.set(at(day, 8, 0));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Companion c : companions) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", c.name());
            item.put("cpf", c.cpf());
            item.put("birthDate", c.birthDate().toString());
            item.put("relationship", c.relationship());
            list.add(item);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId);
        body.put("scheduledDate", day.toString());
        body.put("companions", list);
        JsonNode visit = json(me.client().post("/api/visits", body).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString());
        UUID visitId = UUID.fromString(visit.get("id").asString());
        List<UUID> ids = visit.get("companions").valueStream().map(c -> UUID.fromString(c.get("id").asString())).toList();
        UUID invitationId = jdbc.sql("SELECT id FROM invitations WHERE visit_id = :v AND status = 'ACTIVE'")
                .param("v", visitId).query(UUID.class).single();
        return new Booking(leadId, visitId, invitationId, ids);
    }

    Booking book(ProspectorSession me, LocalDate day) throws Exception {
        return book(me, testData.lead(me.prospector()).getId(), day, List.of());
    }

    void enter(ApiClient gate, Booking booking, List<UUID> present, Instant when) throws Exception {
        clock.set(when);
        gate.post("/api/access/register", Map.of("invitationId", booking.invitationId(), "presentCompanionIds", present))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("AUTHORIZED"));
    }

    static Companion companion(String name, String relationship) {
        return new Companion(name, FakeCpf.generate(), LocalDate.of(2012, 3, 4), relationship);
    }

    static String query(String... parts) {
        return parts.length == 0 ? "" : "?" + String.join("&", parts);
    }

    JsonNode json(String text) {
        return jsonMapper.readTree(text);
    }

    List<String> exportAudits(UUID adminId) {
        return jdbc.sql("SELECT metadata::text FROM audit_logs WHERE action = 'EXPORT_GENERATED' AND user_id = :u ORDER BY created_at, id")
                .param("u", adminId).query(String.class).list();
    }
}
