package com.resort.platform.visits;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Utilitários dos testes de visita, com dados fictícios gerados. */
abstract class VisitTestSupport extends IntegrationTestSupport {

    static Map<String, Object> companion(String name, String cpf, String birthDate, String relationship) {
        Map<String, Object> companion = new HashMap<>();
        companion.put("name", name);
        companion.put("cpf", cpf);
        companion.put("birthDate", birthDate);
        companion.put("relationship", relationship);
        return companion;
    }

    static Map<String, Object> companion(String name) {
        return companion(name, null, "2012-05-20", "CHILD");
    }

    static List<Map<String, Object>> companions(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(companion("Acompanhante " + i, FakeCpf.generate(), "2010-01-0" + ((i % 9) + 1), "FRIEND"));
        }
        return list;
    }

    Map<String, Object> visitBody(UUID leadId, LocalDate date, List<Map<String, Object>> companions) {
        Map<String, Object> body = new HashMap<>();
        body.put("leadId", leadId);
        body.put("scheduledDate", date.toString());
        body.put("notes", "Nota operacional fictícia");
        body.put("hostNotes", "Prefere conhecer a área de lazer");
        body.put("companions", companions);
        return body;
    }

    ResultActions schedule(ApiClient client, UUID leadId, LocalDate date, List<Map<String, Object>> companions) throws Exception {
        return client.post("/api/visits", visitBody(leadId, date, companions));
    }

    JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    UUID scheduledId(ApiClient client, UUID leadId, LocalDate date, List<Map<String, Object>> companions) throws Exception {
        return UUID.fromString(json(schedule(client, leadId, date, companions)).get("id").asString());
    }

    String visitStatus(UUID visitId) {
        return jdbc.sql("SELECT status FROM visits WHERE id = :id").param("id", visitId).query(String.class).single();
    }

    String leadStatus(UUID leadId) {
        return jdbc.sql("SELECT status FROM leads WHERE id = :id").param("id", leadId).query(String.class).single();
    }

    long scheduledCount(UUID leadId) {
        return jdbc.sql("SELECT count(*) FROM visits WHERE lead_id = :id AND status = 'SCHEDULED'")
                .param("id", leadId).query(Long.class).single();
    }

    long auditCount(String action, UUID entityId) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = :action AND entity_id = :id")
                .param("action", action).param("id", entityId).query(Long.class).single();
    }

    /** Muda o status direto no banco para testar estados que só as Fases 6 e seguintes produzem. */
    void forceVisitStatus(UUID visitId, String status) {
        jdbc.sql("UPDATE visits SET status = :status WHERE id = :id").param("status", status).param("id", visitId).update();
    }
}
