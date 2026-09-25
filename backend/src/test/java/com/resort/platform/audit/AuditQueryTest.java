package com.resort.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** GET /api/audit (§19, D-102). */
class AuditQueryTest extends IntegrationTestSupport {

    private static final AtomicLong DAYS = new AtomicLong();

    private static LocalDate uniqueDay() {
        return LocalDate.of(2039, 1, 1).plusDays(DAYS.getAndIncrement() * 3 + 1);
    }

    private Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(calendar.zone()).toInstant();
    }

    private record Admin(ApiClient client, User user) {}

    private Admin admin() throws Exception {
        User user = testData.user(Role.ADMIN);
        return new Admin(client().login(user.getEmail(), TestData.PASSWORD), user);
    }

    /** Uma ação do ADMIN no instante informado: criar um usuário GATE gera USER_CREATED. */
    private UUID createUser(Admin admin, Instant when) throws Exception {
        clock.set(when);
        String body = admin.client().post("/api/users",
                        Map.of("name", "Porteiro Fictício", "email", TestData.uniqueEmail("audit"), "role", "GATE"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(jsonMapper.readTree(body).get("user").get("id").asString());
    }

    private JsonNode search(Admin admin, String query) throws Exception {
        return jsonMapper.readTree(admin.client().get("/api/audit?" + query).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // X1 (auditoria)
    @Test
    void onlyAdminReadsTheAudit() throws Exception {
        for (Role role : List.of(Role.PROSPECTOR, Role.GATE, Role.HOST)) {
            loggedIn(role).get("/api/audit").andExpect(status().isForbidden());
        }
        client().get("/api/audit").andExpect(status().isUnauthorized());
    }

    // X13
    @Test
    void filtersByPeriodUserActionAndEntityMostRecentFirst() throws Exception {
        LocalDate day = uniqueDay();
        Admin admin = admin();
        UUID late = createUser(admin, at(day, 23, 59));
        UUID early = createUser(admin, at(day.plusDays(1), 0, 1));
        String mine = "userId=" + admin.user().getId();

        JsonNode firstDay = search(admin, mine + "&from=" + day + "&to=" + day);
        assertThat(firstDay.get("content").valueStream().map(e -> e.get("entityId").asString()).toList())
                .containsExactly(late.toString());
        JsonNode both = search(admin, mine + "&from=" + day + "&to=" + day.plusDays(1) + "&action=USER_CREATED&entityType=USER");
        assertThat(both.get("content").valueStream().map(e -> e.get("entityId").asString()).toList())
                .containsExactly(early.toString(), late.toString());
        JsonNode entry = both.get("content").get(0);
        assertThat(entry.propertyNames()).containsExactlyInAnyOrder("id", "createdAt", "userId", "userName", "action",
                "entityType", "entityId", "metadata", "ipAddress");
        assertThat(entry.get("userName").asString()).isEqualTo(admin.user().getName());
        assertThat(entry.get("action").asString()).isEqualTo("USER_CREATED");
        assertThat(entry.get("metadata").get("role").asString()).isEqualTo("GATE");
        assertThat(entry.get("ipAddress").asString()).isEqualTo("127.0.0.1");
        assertThat(entry.get("createdAt").asString()).isEqualTo(at(day.plusDays(1), 0, 1).toString());

        JsonNode byEntity = search(admin, "from=" + day + "&to=" + day.plusDays(1) + "&entityType=USER&entityId=" + late);
        assertThat(byEntity.get("totalElements").asLong()).isEqualTo(1);
        assertThat(search(admin, mine + "&from=" + day + "&to=" + day.plusDays(1) + "&action=LOGIN").get("totalElements").asLong())
                .isZero();
    }

    // X13: "Sistema" quando user_id é nulo (job noturno).
    @Test
    void systemActionsShowSistema() throws Exception {
        LocalDate day = uniqueDay();
        UUID entity = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id, metadata, created_at)
                        VALUES (:id, NULL, 'VISIT_NO_SHOW', 'VISIT', :entity, CAST('{"leadId":"x"}' AS jsonb), :at)
                        """)
                .param("id", UUID.randomUUID()).param("entity", entity)
                .param("at", at(day, 0, 15).atOffset(ZoneOffset.UTC)).update();

        JsonNode result = search(admin(), "from=" + day + "&to=" + day + "&entityId=" + entity + "&entityType=VISIT");
        JsonNode entry = result.get("content").get(0);
        assertThat(entry.get("userId").isNull()).isTrue();
        assertThat(entry.get("userName").asString()).isEqualTo("Sistema");
        assertThat(entry.get("ipAddress").isNull()).isTrue();
        assertThat(entry.get("metadata").get("leadId").asString()).isEqualTo("x");
    }

    // X13: paginação e tamanho máximo.
    @Test
    void paginatesMostRecentFirst() throws Exception {
        LocalDate day = uniqueDay();
        Admin admin = admin();
        List<UUID> created = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(createUser(admin, at(day, 10, i)));
        }
        String query = "userId=" + admin.user().getId() + "&action=USER_CREATED&from=" + day + "&to=" + day + "&size=2";

        JsonNode first = search(admin, query + "&page=0");
        JsonNode last = search(admin, query + "&page=2");
        assertThat(first.get("totalElements").asLong()).isEqualTo(5);
        assertThat(first.get("totalPages").asInt()).isEqualTo(3);
        assertThat(first.get("content").valueStream().map(e -> e.get("entityId").asString()).toList())
                .containsExactly(created.get(4).toString(), created.get(3).toString());
        assertThat(last.get("content").valueStream().map(e -> e.get("entityId").asString()).toList())
                .containsExactly(created.getFirst().toString());
        assertThat(search(admin, "size=500").get("size").asInt()).isEqualTo(100);
    }

    // X13: padrão de 30 dias até hoje.
    @Test
    void defaultPeriodIsTheLastThirtyDays() throws Exception {
        LocalDate day = uniqueDay();
        Admin admin = admin();
        UUID old = createUser(admin, at(day.minusDays(30), 12, 0));
        UUID recent = createUser(admin, at(day.minusDays(29), 12, 0));
        clock.set(at(day, 12, 0));

        JsonNode result = search(admin, "userId=" + admin.user().getId() + "&action=USER_CREATED");
        assertThat(result.get("content").valueStream().map(e -> e.get("entityId").asString()).toList())
                .containsExactly(recent.toString()).doesNotContain(old.toString());
    }

    // X14
    @Test
    void invalidFiltersAreRejected() throws Exception {
        ApiClient admin = admin().client();
        LocalDate day = uniqueDay();
        admin.get("/api/audit?action=FEZ_ALGO").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.get("/api/audit?entityType=ACCESS").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.get("/api/audit?from=" + day.minusDays(365) + "&to=" + day).andExpect(status().isOk());
        admin.get("/api/audit?from=" + day.minusDays(366) + "&to=" + day).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIOD_TOO_LONG"));
        admin.get("/api/audit?from=" + day).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.get("/api/audit?userId=nao-e-uuid").andExpect(status().isBadRequest());
        for (String type : AuditQueryService.ENTITY_TYPES) {
            admin.get("/api/audit?entityType=" + type).andExpect(status().isOk());
        }
    }

    // X15
    @Test
    void readingTheAuditIsNotAudited() throws Exception {
        Admin admin = admin();
        long before = jdbc.sql("SELECT count(*) FROM audit_logs").query(Long.class).single();

        search(admin, "");
        search(admin, "action=LOGIN&size=5");

        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs").query(Long.class).single()).isEqualTo(before);
    }
}
