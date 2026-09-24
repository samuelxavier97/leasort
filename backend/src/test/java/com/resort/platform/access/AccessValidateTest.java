package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.TestCodes;
import com.resort.platform.invitations.InvitationExpiryJob;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** §12.1 e §12.2: validação só lê o convite e grava apenas as negativas. */
class AccessValidateTest extends AccessTestSupport {

    @Autowired
    InvitationExpiryJob expiryJob;

    /** G4 */
    @Test
    void validInvitationTodayIsAuthorizedWithoutWritingAnything() throws Exception {
        ProspectorSession me = loggedInProspector();
        Booking booking = book(me, calendar.today(), 2);
        GateSession gate = gate();
        long audits = auditTotal();

        JsonNode body = json(validate(gate.client(), booking.code()).andExpect(status().isOk()));

        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "result", "invitationId", "leadName", "scheduledDate", "prospectorName", "companions");
        assertThat(body.get("result").asString()).isEqualTo("AUTHORIZED");
        assertThat(body.get("invitationId").asString()).isEqualTo(booking.invitationId().toString());
        assertThat(body.get("scheduledDate").asString()).isEqualTo(calendar.today().toString());
        assertThat(body.get("prospectorName").asString()).isEqualTo(me.user().getName());
        assertThat(body.get("companions")).hasSize(2);
        for (JsonNode companion : body.get("companions")) {
            // §15: GATE vê nome e parentesco; nada de CPF nem nascimento.
            assertThat(fieldNames(companion)).containsExactlyInAnyOrder("id", "name", "relationship");
        }
        assertThat(accessRowsBy(gate.user().getId())).isEmpty();
        assertThat(auditTotal()).isEqualTo(audits);
        assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("ACTIVE");
    }

    /** G5: um teste por motivo de negativa, com o registro e a auditoria conferidos. */
    @Test
    void unknownCodeIsInvalid() throws Exception {
        GateSession gate = gate();
        String unknown = TestCodes.unique();

        validate(gate.client(), unknown).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DENIED"))
                .andExpect(jsonPath("$.denialReason").value("INVALID_CODE"))
                .andExpect(jsonPath("$.scheduledDate").doesNotExist());

        assertDenied(gate, null, unknown, "INVALID_CODE");
    }

    @Test
    void malformedCodeIsInvalidAndStoredNormalized() throws Exception {
        GateSession gate = gate();

        validate(gate.client(), "rsv:abc-12 u").andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));

        assertDenied(gate, null, "ABC12U", "INVALID_CODE");
    }

    @Test
    void cancelledInvitationIsDenied() throws Exception {
        ProspectorSession me = loggedInProspector();
        Booking booking = book(me, calendar.today(), 0);
        me.client().patch("/api/visits/" + booking.visitId() + "/cancel", null).andExpect(status().isOk());
        GateSession gate = gate();

        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("CANCELLED"))
                .andExpect(jsonPath("$.scheduledDate").doesNotExist());

        assertDenied(gate, booking.invitationId(), booking.code(), "CANCELLED");
    }

    @Test
    void usedInvitationIsDenied() throws Exception {
        Booking booking = book(loggedInProspector(), calendar.today(), 0);
        register(gate().client(), booking.invitationId(), List.of()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        GateSession gate = gate();

        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("ALREADY_USED"));

        assertDenied(gate, booking.invitationId(), booking.code(), "ALREADY_USED");
    }

    @Test
    void expiredStatusIsDenied() throws Exception {
        Booking booking = book(loggedInProspector(), calendar.today(), 0);
        jdbc.sql("UPDATE invitations SET status = 'EXPIRED' WHERE id = :id").param("id", booking.invitationId()).update();
        GateSession gate = gate();

        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("EXPIRED"))
                .andExpect(jsonPath("$.scheduledDate").value(calendar.today().toString()));

        assertDenied(gate, booking.invitationId(), booking.code(), "EXPIRED");
    }

    /** D-006: vencido pela data, ainda ACTIVE porque o job não rodou. */
    @Test
    void pastDateIsExpiredEvenWhileStillActive() throws Exception {
        LocalDate date = calendar.today();
        Booking booking = book(loggedInProspector(), date, 0);
        GateSession gate = gate();
        clock.set(calendar.endOfDay(date).plusSeconds(60));

        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("EXPIRED"))
                .andExpect(jsonPath("$.scheduledDate").value(date.toString()));

        assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("ACTIVE");
        assertDenied(gate, booking.invitationId(), booking.code(), "EXPIRED");
    }

    @Test
    void futureDateIsWrongDate() throws Exception {
        LocalDate date = calendar.today().plusDays(2);
        Booking booking = book(loggedInProspector(), date, 0);
        GateSession gate = gate();

        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("WRONG_DATE"))
                .andExpect(jsonPath("$.scheduledDate").value(date.toString()));

        assertDenied(gate, booking.invitationId(), booking.code(), "WRONG_DATE");
    }

    /** G6: a ordem das verificações decide quando mais de um motivo se aplica. */
    @Test
    void checksFollowTheSpecOrder() throws Exception {
        ProspectorSession me = loggedInProspector();
        GateSession gate = gate();
        LocalDate today = calendar.today();
        Booking cancelledPast = book(me, today, 0);
        me.client().patch("/api/visits/" + cancelledPast.visitId() + "/cancel", null).andExpect(status().isOk());
        Booking cancelledFuture = book(me, today.plusDays(3), 0);
        me.client().patch("/api/visits/" + cancelledFuture.visitId() + "/cancel", null).andExpect(status().isOk());
        Booking usedPast = book(me, today, 0);
        register(gate.client(), usedPast.invitationId(), List.of()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        Booking expiredByJob = book(me, today, 0);

        // Dia seguinte: o job expira o convite ainda ativo.
        clock.set(calendar.endOfDay(today).plusSeconds(60));
        validate(gate.client(), cancelledPast.code()).andExpect(jsonPath("$.denialReason").value("CANCELLED"));
        validate(gate.client(), usedPast.code()).andExpect(jsonPath("$.denialReason").value("ALREADY_USED"));
        expiryJob.run();
        assertThat(stateOf("invitations", expiredByJob.invitationId())).isEqualTo("EXPIRED");

        // De volta ao dia da visita: o status EXPIRED continua negando.
        clock.reset();
        validate(gate.client(), expiredByJob.code()).andExpect(jsonPath("$.denialReason").value("EXPIRED"));
        validate(gate.client(), cancelledFuture.code()).andExpect(jsonPath("$.denialReason").value("CANCELLED"));
    }

    /** G7: "hoje" em APP_TIMEZONE, com o relógio controlado. */
    @Test
    void validityFollowsTheOperationDayAcrossMidnight() throws Exception {
        clock.set(Instant.parse("2026-03-10T15:00:00Z"));
        Booking booking = book(loggedInProspector(), LocalDate.parse("2026-03-11"), 0);
        GateSession gate = gate();

        clock.set(Instant.parse("2026-03-11T02:59:59Z")); // 23:59:59 de 10/03 em São Paulo
        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("WRONG_DATE"));
        clock.set(Instant.parse("2026-03-11T03:00:00Z")); // 00:00 de 11/03
        validate(gate.client(), booking.code()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        clock.set(Instant.parse("2026-03-12T02:59:59Z")); // 23:59:59 de 11/03; já é 12/03 em UTC
        validate(gate.client(), booking.code()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        clock.set(Instant.parse("2026-03-12T03:00:00Z")); // 00:00 de 12/03
        validate(gate.client(), booking.code()).andExpect(jsonPath("$.denialReason").value("EXPIRED"));
    }

    /** G8: o código digitado ou lido é normalizado antes da busca. */
    @Test
    void typedVariantsAreNormalized() throws Exception {
        String code = "01" + TestCodes.unique().substring(2);
        random.enqueueCodes(code);
        Booking booking = book(loggedInProspector(), calendar.today(), 0);
        assertThat(booking.code()).isEqualTo(code);
        GateSession gate = gate();
        String typed = ("rsv: " + code.substring(0, 5) + "-" + code.substring(5)).toLowerCase().replace('0', 'o').replace('1', 'l');

        validate(gate.client(), typed).andExpect(jsonPath("$.result").value("AUTHORIZED"))
                .andExpect(jsonPath("$.invitationId").value(booking.invitationId().toString()));
        validate(gate.client(), "RSV:" + code.replace('1', 'I')).andExpect(jsonPath("$.result").value("AUTHORIZED"));
    }

    /** G9: código em branco ou longo demais não é tentativa de acesso. */
    @Test
    void blankOrOversizedCodeIsABadRequestAndNothingIsRecorded() throws Exception {
        GateSession gate = gate();

        for (String code : List.of("", "   ", "A".repeat(65))) {
            validate(gate.client(), code).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        gate.client().post("/api/access/validate", Map.of()).andExpect(status().isBadRequest());
        assertThat(accessRowsBy(gate.user().getId())).isEmpty();
    }

    private void assertDenied(GateSession gate, UUID invitationId, String attemptedCode, String reason) {
        List<AccessRow> rows = accessRowsBy(gate.user().getId());
        assertThat(rows).hasSize(1);
        AccessRow row = rows.get(0);
        assertThat(row.result()).isEqualTo("DENIED");
        assertThat(row.denialReason()).isEqualTo(reason);
        assertThat(row.invitationId()).isEqualTo(invitationId);
        assertThat(row.attemptedCode()).isEqualTo(attemptedCode);
        assertThat(row.gate()).isEqualTo("PRINCIPAL");
        assertThat(row.entered()).isFalse();
        // D-044: metadata só com o id do registro.
        assertThat(accessAudits("ACCESS_DENIED", row.id())).singleElement()
                .satisfies(metadata -> assertThat(jsonMapper.readTree(metadata))
                        .isEqualTo(jsonMapper.readTree("{\"access_record_id\":\"" + row.id() + "\"}")));
    }

    private static List<String> fieldNames(JsonNode node) {
        return StreamSupport.stream(node.propertyNames().spliterator(), false).toList();
    }
}
