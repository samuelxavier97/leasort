package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.users.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** §12.3 e RN11: registro da entrada, com revalidação completa. */
class AccessRegisterTest extends AccessTestSupport {

    /** G10 */
    @Test
    void registersTheEntryWithThePresentCompanions() throws Exception {
        ProspectorSession me = loggedInProspector();
        Booking booking = book(me, calendar.today(), 3);
        GateSession gate = gate();
        List<UUID> present = booking.companionIds().subList(0, 2);

        JsonNode body = json(register(gate.client(), booking.invitationId(), present).andExpect(status().isOk()));

        assertThat(body.get("result").asString()).isEqualTo("AUTHORIZED");
        UUID recordId = UUID.fromString(body.get("accessRecordId").asString());
        assertThat(body.get("entryAt").asString()).isNotBlank();
        List<AccessRow> rows = accessRowsBy(gate.user().getId());
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(recordId);
            assertThat(row.result()).isEqualTo("AUTHORIZED");
            assertThat(row.entered()).isTrue();
            assertThat(row.denialReason()).isNull();
            assertThat(row.invitationId()).isEqualTo(booking.invitationId());
        });
        assertThat(jdbc.sql("SELECT companion_id FROM access_record_companions WHERE access_record_id = :id")
                .param("id", recordId).query(UUID.class).list()).containsExactlyInAnyOrderElementsOf(present);
        assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("USED");
        assertThat(jdbc.sql("SELECT used_at IS NOT NULL FROM invitations WHERE id = :id").param("id", booking.invitationId())
                .query(Boolean.class).single()).isTrue();
        assertThat(stateOf("visits", booking.visitId())).isEqualTo("COMPLETED");
        assertThat(stateOf("leads", booking.leadId())).isEqualTo("VISITED");
        assertThat(accessAudits("ACCESS_VALIDATED", recordId)).singleElement()
                .satisfies(metadata -> assertThat(jsonMapper.readTree(metadata)).isEqualTo(jsonMapper.readTree(
                        "{\"access_record_id\":\"%s\",\"visit_id\":\"%s\",\"companions_present\":2}"
                                .formatted(recordId, booking.visitId()))));
        assertThat(jdbc.sql("""
                        SELECT metadata ->> 'cause' FROM audit_logs WHERE action = 'LEAD_STATUS_CHANGED' AND entity_id = :id
                        ORDER BY created_at DESC LIMIT 1
                        """).param("id", booking.leadId()).query(String.class).single()).isEqualTo("ACCESS_REGISTERED");
    }

    /** G11 */
    @Test
    void presentCompanionsMustBelongToTheVisit() throws Exception {
        ProspectorSession me = loggedInProspector();
        Booking booking = book(me, calendar.today(), 2);
        Booking other = book(me, calendar.today(), 1);
        GateSession gate = gate();

        register(gate.client(), booking.invitationId(), List.of(other.companionIds().get(0)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMPANION_NOT_FOUND"));
        register(gate.client(), booking.invitationId(), List.of(UUID.randomUUID()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMPANION_NOT_FOUND"));
        UUID first = booking.companionIds().get(0);
        register(gate.client(), booking.invitationId(), List.of(first, first))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        gate.client().post("/api/access/register", Map.of("invitationId", booking.invitationId()))
                .andExpect(status().isBadRequest());

        assertThat(accessRowsBy(gate.user().getId())).isEmpty();
        assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("ACTIVE");
        assertThat(stateOf("visits", booking.visitId())).isEqualTo("SCHEDULED");

        // Lista vazia: só o Lead entrou.
        register(gate.client(), booking.invitationId(), List.of()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
    }

    /** G12: tudo é revalidado no registro, e a negativa fica gravada. */
    @Test
    void registrationRevalidatesEverything() throws Exception {
        ProspectorSession me = loggedInProspector();
        GateSession gate = gate();
        Booking cancelled = book(me, calendar.today(), 0);
        me.client().patch("/api/visits/" + cancelled.visitId() + "/cancel", null).andExpect(status().isOk());
        Booking reissued = book(me, calendar.today(), 0);
        me.client().post("/api/invitations/" + reissued.invitationId() + "/reissue", null).andExpect(status().isOk());
        Booking future = book(me, calendar.today().plusDays(2), 0);
        Booking today = book(me, calendar.today(), 0);

        register(gate.client(), cancelled.invitationId(), List.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DENIED")).andExpect(jsonPath("$.denialReason").value("CANCELLED"));
        register(gate.client(), reissued.invitationId(), List.of())
                .andExpect(jsonPath("$.denialReason").value("CANCELLED"));
        register(gate.client(), future.invitationId(), List.of())
                .andExpect(jsonPath("$.denialReason").value("WRONG_DATE"))
                .andExpect(jsonPath("$.scheduledDate").value(calendar.today().plusDays(2).toString()));
        clock.set(calendar.endOfDay(calendar.today()).plusSeconds(1));
        register(gate.client(), today.invitationId(), List.of()).andExpect(jsonPath("$.denialReason").value("EXPIRED"));
        clock.reset();
        register(gate.client(), UUID.randomUUID(), List.of()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));

        List<AccessRow> rows = accessRowsBy(gate.user().getId());
        assertThat(rows).extracting(AccessRow::denialReason).containsExactly("CANCELLED", "CANCELLED", "WRONG_DATE", "EXPIRED");
        assertThat(rows).allSatisfy(row -> assertThat(row.result()).isEqualTo("DENIED"));
        assertThat(stateOf("visits", today.visitId())).isEqualTo("SCHEDULED");
    }

    /** G13: clique duplo em sequência. */
    @Test
    void doubleClickRecordsASingleEntry() throws Exception {
        Booking booking = book(loggedInProspector(), calendar.today(), 0);
        GateSession gate = gate();

        register(gate.client(), booking.invitationId(), List.of()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        register(gate.client(), booking.invitationId(), List.of())
                .andExpect(jsonPath("$.result").value("DENIED"))
                .andExpect(jsonPath("$.denialReason").value("ALREADY_USED"));

        assertThat(authorizedCount(booking.invitationId())).isEqualTo(1);
        assertThat(accessRowsBy(gate.user().getId())).extracting(AccessRow::result).containsExactly("AUTHORIZED", "DENIED");
    }

    /** G18 */
    @Test
    void onlyGateValidatesAndRegisters() throws Exception {
        UUID id = UUID.randomUUID();
        for (Role role : List.of(Role.ADMIN, Role.PROSPECTOR, Role.HOST)) {
            ApiClient client = loggedIn(role);
            validate(client, "ABCDEFGHJK").andExpect(status().isForbidden());
            register(client, id, List.of()).andExpect(status().isForbidden());
        }
        loggedIn(Role.PROSPECTOR).get("/api/access/recent").andExpect(status().isForbidden());
        loggedIn(Role.HOST).get("/api/access/recent").andExpect(status().isForbidden());
        loggedIn(Role.ADMIN).get("/api/access/recent").andExpect(status().isOk());
        gate().client().get("/api/access/recent").andExpect(status().isOk());
        client().post("/api/access/validate", Map.of("code", "ABCDEFGHJK")).andExpect(status().isUnauthorized());
        client().get("/api/access/recent").andExpect(status().isUnauthorized());
    }

    /** G19: 30 validações por minuto por usuário GATE (§12.4, D-045). */
    @Test
    void validationIsRateLimitedPerGateUser() throws Exception {
        clock.set(java.time.Instant.parse("2026-03-10T15:00:00Z"));
        GateSession gate = gate();
        GateSession other = gate();
        Booking booking = book(loggedInProspector(), calendar.today(), 0);

        for (int i = 0; i < 30; i++) {
            validate(gate.client(), "ABCDEFGHJ").andExpect(status().isOk());
        }
        long recorded = accessRowsBy(gate.user().getId()).size();
        validate(gate.client(), booking.code()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VALIDATIONS"));
        assertThat(accessRowsBy(gate.user().getId())).hasSize((int) recorded);

        validate(other.client(), booking.code()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        register(gate.client(), booking.invitationId(), List.of()).andExpect(jsonPath("$.result").value("AUTHORIZED"));

        clock.set(java.time.Instant.parse("2026-03-10T15:01:01Z"));
        validate(gate.client(), booking.code()).andExpect(status().isOk());
    }

    /** G20: acessos de hoje, do mais recente para o mais antigo, sem código nem CPF (D-092). */
    @Test
    void recentAccessesShowTodayWithoutCodes() throws Exception {
        ProspectorSession me = loggedInProspector();
        GateSession gate = gate();
        Booking entered = book(me, calendar.today(), 0);
        Booking future = book(me, calendar.today().plusDays(1), 0);
        String unknown = com.resort.platform.TestCodes.unique();
        // Ontem, em APP_TIMEZONE: não aparece.
        clock.set(calendar.startOfDay(calendar.today()).minusSeconds(60));
        validate(gate.client(), unknown).andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        clock.reset();
        validate(gate.client(), unknown).andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        validate(gate.client(), future.code()).andExpect(jsonPath("$.denialReason").value("WRONG_DATE"));
        register(gate.client(), entered.invitationId(), List.of()).andExpect(jsonPath("$.result").value("AUTHORIZED"));

        String response = loggedIn(Role.ADMIN).get("/api/access/recent").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> mine = new ArrayList<>();
        jsonMapper.readTree(response).valueStream()
                .filter(item -> gate.user().getName().equals(item.get("validatedBy").asString()))
                .forEach(mine::add);
        // Todos os GATE de teste se chamam igual; filtra pelos registros deste teste.
        List<UUID> ids = accessRowsBy(gate.user().getId()).stream().map(AccessRow::id).toList();
        List<JsonNode> ours = mine.stream().filter(item -> ids.contains(UUID.fromString(item.get("id").asString()))).toList();

        assertThat(ours).extracting(item -> item.get("result").asString() + "/" + item.get("denialReason").asString())
                .containsExactly("AUTHORIZED/", "DENIED/WRONG_DATE", "DENIED/INVALID_CODE");
        assertThat(ours.get(0).get("leadName").asString()).isNotBlank();
        assertThat(ours.get(2).get("leadName").isNull()).isTrue();
        assertThat(ours.get(0).get("gate").asString()).isEqualTo("PRINCIPAL");
        assertThat(response).doesNotContain(unknown).doesNotContain(future.code()).doesNotContain(entered.code());
        assertThat(jsonMapper.readTree(response).size()).isLessThanOrEqualTo(AccessService.RECENT_LIMIT);
    }
}
