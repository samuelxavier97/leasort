package com.resort.platform.invitations;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Utilitários dos testes de convite, com dados fictícios e códigos gerados na hora. */
abstract class InvitationTestSupport extends IntegrationTestSupport {

    record InvitationRow(UUID id, UUID visitId, String code, String status, Instant expiresAt, Instant cancelledAt) {}

    JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    ResultActions schedule(ApiClient client, UUID leadId, LocalDate date) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId);
        body.put("scheduledDate", date.toString());
        body.put("companions", List.of(Map.of("name", "Acompanhante Fictício", "birthDate", "2012-05-20", "relationship", "CHILD")));
        return client.post("/api/visits", body);
    }

    /** Agenda e devolve o id da visita. */
    UUID scheduledVisit(ApiClient client, UUID leadId, LocalDate date) throws Exception {
        return UUID.fromString(json(schedule(client, leadId, date).andExpect(status().isCreated())).get("id").asString());
    }

    List<InvitationRow> invitationsOf(UUID visitId) {
        return jdbc.sql("""
                        SELECT id, visit_id, code, status, expires_at, cancelled_at FROM invitations
                        WHERE visit_id = :visit ORDER BY created_at, id
                        """)
                .param("visit", visitId)
                .query((rs, n) -> new InvitationRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("visit_id", UUID.class),
                        rs.getString("code"),
                        rs.getString("status"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant()))
                .list();
    }

    InvitationRow activeOf(UUID visitId) {
        List<InvitationRow> active = invitationsOf(visitId).stream().filter(i -> i.status().equals("ACTIVE")).toList();
        if (active.size() != 1) {
            throw new AssertionError("Esperado exatamente um convite ACTIVE; encontrados " + active.size());
        }
        return active.get(0);
    }

    long activeCount(UUID visitId) {
        return invitationsOf(visitId).stream().filter(i -> i.status().equals("ACTIVE")).count();
    }

    String visitStatus(UUID visitId) {
        return jdbc.sql("SELECT status FROM visits WHERE id = :id").param("id", visitId).query(String.class).single();
    }

    String leadStatus(UUID leadId) {
        return jdbc.sql("SELECT status FROM leads WHERE id = :id").param("id", leadId).query(String.class).single();
    }

    UUID scheduledVisitOfLead(UUID leadId) {
        return jdbc.sql("SELECT id FROM visits WHERE lead_id = :id AND status = 'SCHEDULED'")
                .param("id", leadId).query(UUID.class).single();
    }

    List<Map<String, Object>> audits(String action, UUID entityId) {
        return jdbc.sql("SELECT metadata::text AS metadata FROM audit_logs WHERE action = :action AND entity_id = :id")
                .param("action", action).param("id", entityId).query().listOfRows();
    }

    /** Invariante RN06: toda visita SCHEDULED com exatamente um ACTIVE; nenhuma outra com ACTIVE. */
    void assertInvariant(UUID leadId) {
        List<Map<String, Object>> rows = jdbc.sql("""
                        SELECT v.id, v.status, count(i.id) FILTER (WHERE i.status = 'ACTIVE') AS active
                        FROM visits v LEFT JOIN invitations i ON i.visit_id = v.id
                        WHERE v.lead_id = :lead GROUP BY v.id, v.status
                        """)
                .param("lead", leadId).query().listOfRows();
        for (Map<String, Object> row : rows) {
            long active = ((Number) row.get("active")).longValue();
            long expected = "SCHEDULED".equals(row.get("status")) ? 1 : 0;
            if (active != expected) {
                throw new AssertionError("Visita " + row.get("id") + " " + row.get("status") + " com " + active + " convite(s) ACTIVE");
            }
        }
    }
}
