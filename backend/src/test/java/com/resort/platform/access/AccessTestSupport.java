package com.resort.platform.access;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Utilitários dos testes da Portaria, com dados fictícios e códigos gerados na hora. */
abstract class AccessTestSupport extends IntegrationTestSupport {

    record Booking(UUID leadId, UUID visitId, UUID invitationId, String code, List<UUID> companionIds) {}

    record GateSession(ApiClient client, User user) {}

    record AccessRow(UUID id, UUID invitationId, String attemptedCode, UUID validatedBy, String gate, String result,
            String denialReason, boolean entered) {}

    GateSession gate() throws Exception {
        User user = testData.user(Role.GATE);
        return new GateSession(client().login(user.getEmail(), TestData.PASSWORD), user);
    }

    /** Agenda pela API do Prospector e devolve a visita com o convite ACTIVE criado junto. */
    Booking book(ProspectorSession me, LocalDate date, int companions) throws Exception {
        UUID leadId = testData.lead(me.prospector()).getId();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= companions; i++) {
            list.add(Map.of("name", "Acompanhante " + i, "birthDate", "2010-01-01", "relationship", "FRIEND"));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId);
        body.put("scheduledDate", date.toString());
        body.put("companions", list);
        JsonNode visit = json(me.client().post("/api/visits", body).andExpect(status().isCreated()));
        UUID visitId = UUID.fromString(visit.get("id").asString());
        List<UUID> companionIds = visit.get("companions").valueStream().map(c -> UUID.fromString(c.get("id").asString())).toList();
        Map<String, Object> invitation = jdbc.sql("SELECT id, code FROM invitations WHERE visit_id = :v AND status = 'ACTIVE'")
                .param("v", visitId).query().singleRow();
        return new Booking(leadId, visitId, (UUID) invitation.get("id"), invitation.get("code").toString().trim(), companionIds);
    }

    ResultActions validate(ApiClient gate, String code) throws Exception {
        return gate.post("/api/access/validate", Map.of("code", code));
    }

    ResultActions register(ApiClient gate, UUID invitationId, List<UUID> companions) throws Exception {
        return gate.post("/api/access/register", Map.of("invitationId", invitationId, "presentCompanionIds", companions));
    }

    JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    List<AccessRow> accessRowsBy(UUID userId) {
        return jdbc.sql("""
                        SELECT id, invitation_id, attempted_code, validated_by_user_id, gate, result, denial_reason, entry_at
                        FROM access_records WHERE validated_by_user_id = :user ORDER BY created_at, id
                        """)
                .param("user", userId)
                .query((rs, n) -> new AccessRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("invitation_id", UUID.class),
                        rs.getString("attempted_code"),
                        rs.getObject("validated_by_user_id", UUID.class),
                        rs.getString("gate"),
                        rs.getString("result"),
                        rs.getString("denial_reason"),
                        rs.getTimestamp("entry_at") != null))
                .list();
    }

    long authorizedCount(UUID invitationId) {
        return jdbc.sql("SELECT count(*) FROM access_records WHERE invitation_id = :i AND result = 'AUTHORIZED'")
                .param("i", invitationId).query(Long.class).single();
    }

    String stateOf(String table, UUID id) {
        return jdbc.sql("SELECT status FROM " + table + " WHERE id = :id").param("id", id).query(String.class).single();
    }

    /** Metadata da auditoria de um registro de acesso, como texto JSON. */
    List<String> accessAudits(String action, UUID recordId) {
        return jdbc.sql("SELECT metadata::text FROM audit_logs WHERE action = :action AND entity_id = :id")
                .param("action", action).param("id", recordId).query(String.class).list();
    }

    long auditTotal() {
        return jdbc.sql("SELECT count(*) FROM audit_logs").query(Long.class).single();
    }
}
