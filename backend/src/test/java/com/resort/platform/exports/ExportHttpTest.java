package com.resort.platform.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.TestData;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Acesso, cabeçalhos HTTP, validação e auditoria das exportações (§18, D-101). */
@ExtendWith(OutputCaptureExtension.class)
class ExportHttpTest extends ExportTestSupport {

    private static final List<String> FILES = List.of("leads", "visits", "companions", "access");

    @Autowired
    DataSource dataSource;

    @Autowired
    WebMvcProperties webMvcProperties;

    // X1
    @Test
    void onlyAdminExports() throws Exception {
        for (Role role : List.of(Role.PROSPECTOR, Role.GATE, Role.HOST)) {
            ApiClient client = loggedIn(role);
            for (String file : FILES) {
                client.get("/api/exports/" + file).andExpect(status().isForbidden());
            }
        }
        for (String file : FILES) {
            client().get("/api/exports/" + file).andExpect(status().isUnauthorized());
        }
        loggedIn(Role.ADMIN).get("/api/exports/outro").andExpect(status().isNotFound());
    }

    // X2
    @Test
    void responseHeadersNameTheFileWithTodayAndForbidCaching() throws Exception {
        LocalDate day = uniqueDay();
        clock.set(at(day, 23, 30));
        ApiClient admin = admin();
        String prospector = "prospectorId=" + loggedInProspector().prospector().getId();
        Map<String, String> names = Map.of("leads", "leads", "visits", "visitas", "companions", "acompanhantes", "access", "acessos");

        for (String file : FILES) {
            Download download = download(admin, "/api/exports/" + file + query(prospector));
            assertThat(download.response().getContentType()).isEqualTo("text/csv;charset=UTF-8");
            // Hoje no fuso da operação: às 23:30 locais já é o dia seguinte em UTC.
            assertThat(download.response().getHeader("Content-Disposition"))
                    .isEqualTo("attachment; filename=\"" + names.get(file) + "-" + day + ".csv\"");
            assertThat(download.response().getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(download.text()).endsWith("\r\n");
            assertThat(download.data()).isEmpty();
        }
    }

    // X9
    @Test
    void filtersAreValidated() throws Exception {
        ApiClient admin = admin();
        LocalDate day = uniqueDay();
        for (String file : FILES) {
            admin.get("/api/exports/" + file + "?from=" + day).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            admin.get("/api/exports/" + file + "?from=" + day + "&to=" + day.minusDays(1)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            admin.get("/api/exports/" + file + "?from=25/09/2026&to=" + day).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            admin.get("/api/exports/" + file + "?status=QUALQUER").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
            admin.get("/api/exports/" + file + "?prospectorId=" + UUID.randomUUID()).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PROSPECTOR_NOT_FOUND"));
        }
        // O status de um arquivo não vale para outro.
        admin.get("/api/exports/leads?status=COMPLETED").andExpect(status().isBadRequest());
        admin.get("/api/exports/visits?status=AUTHORIZED").andExpect(status().isBadRequest());
        admin.get("/api/exports/access?status=CONTACTED").andExpect(status().isBadRequest());
    }

    // X9: sem período, todo o histórico.
    @Test
    void withoutPeriodTheFileBringsTheWholeHistory() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID old = testData.lead(me.prospector()).getId();
        jdbc.sql("UPDATE leads SET created_at = TIMESTAMPTZ '2001-02-03 10:00:00+00' WHERE id = :id").param("id", old).update();
        UUID recent = testData.lead(me.prospector()).getId();

        assertThat(download(admin(), "/api/exports/leads?prospectorId=" + me.prospector().getId()).data())
                .extracting(r -> r.getFirst()).containsExactly(old.toString(), recent.toString());
    }

    // X11 e X12
    @Test
    void everyExportIsAuditedWithFiltersAndRowsAndNoCpfReachesLogsOrAudit(CapturedOutput output) throws Exception {
        User adminUser = testData.user(Role.ADMIN);
        ApiClient admin = client().login(adminUser.getEmail(), TestData.PASSWORD);
        LocalDate day = uniqueDay();
        ProspectorSession me = loggedInProspector();
        String leadCpf = FakeCpf.generate();
        UUID leadId = testData.lead(me.prospector(), leadCpf, LeadStatus.NEW).getId();
        Companion companion = companion("Acompanhante Fictício", "CHILD");
        Booking booking = book(me, leadId, day, List.of(companion));
        enter(gate(), booking, booking.companionIds(), at(day, 10, 0));
        String p = me.prospector().getId().toString();

        download(admin, "/api/exports/leads?prospectorId=" + p);
        download(admin, "/api/exports/visits?prospectorId=" + p + "&from=" + day + "&to=" + day + "&status=COMPLETED");
        Download companions = download(admin, "/api/exports/companions?prospectorId=" + p);
        download(admin, "/api/exports/access?prospectorId=" + p + "&status=AUTHORIZED");
        assertThat(companions.text()).contains(FakeCpf.formatted(companion.cpf()));

        // O relógio do teste está parado: as quatro auditorias têm o mesmo horário; o jsonb reordena as chaves.
        Map<String, JsonNode> audits = new java.util.HashMap<>();
        exportAudits(adminUser.getId()).stream().map(this::json).forEach(a -> audits.put(a.get("type").asString(), a));
        assertThat(audits).hasSize(4);
        assertThat(audits.get("LEADS")).isEqualTo(json(
                "{\"type\":\"LEADS\",\"filters\":{\"from\":null,\"to\":null,\"status\":null,\"prospectorId\":\"" + p + "\"},\"rows\":1}"));
        assertThat(audits.get("VISITS")).isEqualTo(json("{\"type\":\"VISITS\",\"filters\":{\"from\":\"" + day + "\",\"to\":\"" + day
                + "\",\"status\":\"COMPLETED\",\"prospectorId\":\"" + p + "\"},\"rows\":1}"));
        assertThat(audits.get("COMPANIONS").get("rows").asLong()).isEqualTo(1);
        assertThat(audits.get("ACCESS")).isEqualTo(json(
                "{\"type\":\"ACCESS\",\"filters\":{\"from\":null,\"to\":null,\"status\":\"AUTHORIZED\",\"prospectorId\":\"" + p + "\"},\"rows\":1}"));
        Map<String, Object> row = jdbc.sql("SELECT entity_type, entity_id, ip_address FROM audit_logs "
                        + "WHERE action = 'EXPORT_GENERATED' AND user_id = :u LIMIT 1")
                .param("u", adminUser.getId()).query().singleRow();
        assertThat(row.get("entity_type")).isEqualTo("EXPORT");
        assertThat(row.get("entity_id")).isNull();
        assertThat(row.get("ip_address")).isEqualTo("127.0.0.1");

        String leadName = jdbc.sql("SELECT name FROM leads WHERE id = :id").param("id", leadId).query(String.class).single();
        for (String secret : List.of(leadCpf, FakeCpf.formatted(leadCpf), companion.cpf(), FakeCpf.formatted(companion.cpf()))) {
            assertThat(output.getAll()).doesNotContain(secret);
            assertThat(auditsContaining(secret)).isZero();
        }
        assertThat(audits.values()).allSatisfy(a -> assertThat(a.toString()).doesNotContain(leadName, "Acompanhante Fictício"));
    }

    // Auditoria antes do primeiro byte: se ela falhar, a resposta é erro e nenhuma linha sai.
    @Test
    void whenTheAuditFailsNothingIsSentAndTheConnectionIsReleased() throws Exception {
        User adminUser = testData.user(Role.ADMIN);
        ApiClient admin = client().login(adminUser.getEmail(), TestData.PASSWORD);
        ProspectorSession me = loggedInProspector();
        testData.lead(me.prospector());
        String path = "/api/exports/leads?prospectorId=" + me.prospector().getId();
        int activeBefore = activeConnections();
        jdbc.sql("""
                CREATE FUNCTION test_fail_export_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'auditoria indisponível'; END $$;
                CREATE TRIGGER test_fail_export_audit BEFORE INSERT ON audit_logs
                    FOR EACH ROW WHEN (NEW.action = 'EXPORT_GENERATED') EXECUTE FUNCTION test_fail_export_audit();
                """).update();
        try {
            MvcResult result = admin.get(path).andExpect(status().isInternalServerError()).andReturn();
            assertThat(result.getRequest().isAsyncStarted()).isFalse();
            assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
            assertThat(result.getResponse().getHeader("Content-Disposition")).isNull();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("﻿", "ID;Nome");
            assertThat(exportAudits(adminUser.getId())).isEmpty();
        } finally {
            jdbc.sql("DROP TRIGGER test_fail_export_audit ON audit_logs; DROP FUNCTION test_fail_export_audit();").update();
        }
        waitForConnections(activeBefore);

        assertThat(download(admin, path).data()).hasSize(1);
        assertThat(exportAudits(adminUser.getId())).hasSize(1);
        waitForConnections(activeBefore);
    }

    // D-101: tempo-limite explícito da requisição assíncrona.
    @Test
    void asyncRequestTimeoutCoversAWholeExport() {
        assertThat(webMvcProperties.getAsync().getRequestTimeout()).isEqualTo(Duration.ofMinutes(30));
    }

    private long auditsContaining(String text) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE metadata::text LIKE :t").param("t", "%" + text + "%")
                .query(Long.class).single();
    }

    private int activeConnections() throws Exception {
        return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
    }

    /** A conexão presa pela exportação volta ao pool (a do MockMvc do teste já terminou). */
    private void waitForConnections(int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (activeConnections() > expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(activeConnections()).isLessThanOrEqualTo(expected);
    }
}
