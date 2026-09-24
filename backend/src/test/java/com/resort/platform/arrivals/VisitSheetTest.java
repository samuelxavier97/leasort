package com.resort.platform.arrivals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** GET /api/visits/{id}/sheet (§15, §16.6, D-022, D-096). */
class VisitSheetTest extends ArrivalTestSupport {

    private static final String HOST_NOTES = "Prefere conhecer a área das piscinas primeiro.";

    /** Visita com Lead completo (CPF, e-mail, nascimento, notas) e três acompanhantes; um não entra. */
    private record Full(Booking booking, LocalDate day, String cpf, String email, String leadNotes, String visitNotes,
            List<String> companionCpfs) {}

    private Full fullVisit(ProspectorSession me, LocalDate day) throws Exception {
        Booking booking = book(me, day, List.of(
                new Companion("Acompanhante Cônjuge", "SPOUSE", day.minusYears(40)),
                new Companion("Acompanhante Filho", "CHILD", day.minusYears(7).plusDays(1)),
                new Companion("Acompanhante Amigo", "FRIEND", day.minusYears(30))), HOST_NOTES);
        jdbc.sql("UPDATE leads SET birth_date = :b, notes = :n WHERE id = :id")
                .param("b", day.minusYears(35)).param("n", "Nota interna do Lead " + booking.leadId())
                .param("id", booking.leadId()).update();
        Map<String, Object> lead = jdbc.sql("SELECT cpf, email, notes FROM leads WHERE id = :id")
                .param("id", booking.leadId()).query().singleRow();
        List<String> companionCpfs = jdbc.sql("SELECT cpf FROM visit_companions WHERE visit_id = :v")
                .param("v", booking.visitId()).query(String.class).list();
        String visitNotes = jdbc.sql("SELECT notes FROM visits WHERE id = :v").param("v", booking.visitId())
                .query(String.class).single();
        return new Full(booking, day, (String) lead.get("cpf"), (String) lead.get("email"), (String) lead.get("notes"),
                visitNotes, companionCpfs);
    }

    private ApiClient enteredWithoutTheFriend(Full full) throws Exception {
        ApiClient gate = gate();
        enter(gate, full.booking(), full.booking().companionIds().subList(0, 2), at(full.day(), 10, 30));
        clock.set(at(full.day(), 11, 0));
        return gate;
    }

    private JsonNode sheet(ApiClient client, UUID visitId) throws Exception {
        return json(client.get("/api/visits/" + visitId + "/sheet").andExpect(status().isOk()));
    }

    // H12
    @Test
    void adminSheetHasEveryFieldOfTheSpec() throws Exception {
        ProspectorSession me = loggedInProspector();
        Full full = fullVisit(me, uniqueDay());
        enteredWithoutTheFriend(full);

        JsonNode sheet = sheet(admin(), full.booking().visitId());

        assertThat(sheet.propertyNames()).containsExactlyInAnyOrder("visitId", "scheduledDate", "entryAt", "lead",
                "prospectorName", "presentCompanions", "absentCompanions", "hostNotes");
        assertThat(sheet.get("visitId").asString()).isEqualTo(full.booking().visitId().toString());
        assertThat(sheet.get("scheduledDate").asString()).isEqualTo(full.day().toString());
        assertThat(sheet.get("entryAt").asString()).isEqualTo(at(full.day(), 10, 30).toString());
        assertThat(sheet.get("lead").propertyNames()).containsExactlyInAnyOrder("name", "age", "phone");
        assertThat(sheet.get("lead").get("name").asString()).startsWith("Lead Fictício ");
        assertThat(sheet.get("lead").get("age").asInt()).isEqualTo(35);
        assertThat(sheet.get("lead").get("phone").asString()).isEqualTo("11 90000-0000");
        assertThat(sheet.get("prospectorName").asString()).isEqualTo(me.user().getName());
        assertThat(sheet.get("presentCompanions").toString()).isEqualTo(
                "[{\"name\":\"Acompanhante Cônjuge\",\"relationship\":\"SPOUSE\",\"age\":40},"
                        + "{\"name\":\"Acompanhante Filho\",\"relationship\":\"CHILD\",\"age\":6}]");
        assertThat(sheet.get("absentCompanions").toString())
                .isEqualTo("[{\"name\":\"Acompanhante Amigo\",\"relationship\":\"FRIEND\"}]");
        assertThat(sheet.get("hostNotes").asString()).isEqualTo(HOST_NOTES);
    }

    // H13
    @Test
    void sheetAndListNeverCarryCpfEmailBirthDateOrInternalNotes() throws Exception {
        ProspectorSession me = loggedInProspector();
        Full full = fullVisit(me, uniqueDay());
        enteredWithoutTheFriend(full);
        UUID visitId = full.booking().visitId();

        List<String> bodies = new ArrayList<>();
        for (ApiClient client : List.of(admin(), me.client(), host())) {
            bodies.add(body(client.get("/api/visits/" + visitId + "/sheet").andExpect(status().isOk())));
            bodies.add(body(client.get("/api/arrivals").andExpect(status().isOk())));
        }
        List<String> secrets = new ArrayList<>(List.of(full.cpf(), full.email(), full.leadNotes(), full.visitNotes(),
                full.day().minusYears(35).toString()));
        secrets.addAll(full.companionCpfs());
        for (String body : bodies) {
            assertThat(keysOf(jsonMapper.readTree(body))).doesNotContain("cpf", "email", "birthDate", "notes");
            for (String secret : secrets) {
                assertThat(body).doesNotContain(secret);
            }
            // CPF também não aparece formatado nem mascarado.
            assertThat(body).doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}").doesNotContain("***");
        }
    }

    // H14
    @Test
    void agesAreComputedOnTheVisitDate() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking booking = book(me, day, List.of(
                new Companion("Aniversário no dia", "CHILD", day.minusYears(10)),
                new Companion("Aniversário amanhã", "CHILD", day.minusYears(10).plusDays(1))), null);
        jdbc.sql("UPDATE leads SET birth_date = :b WHERE id = :id")
                .param("b", day.minusYears(50).plusDays(1)).param("id", booking.leadId()).update();
        enter(gate(), booking, at(day, 10, 0));

        clock.set(at(day, 12, 0));
        JsonNode sameDay = sheet(admin(), booking.visitId());
        // Um ano depois, a ficha antiga continua com as idades da data da visita.
        clock.set(at(day.plusYears(1).plusDays(2), 12, 0));
        JsonNode later = sheet(admin(), booking.visitId());

        for (JsonNode sheet : List.of(sameDay, later)) {
            assertThat(sheet.get("lead").get("age").asInt()).isEqualTo(49);
            assertThat(sheet.get("presentCompanions").get(0).get("age").asInt()).isEqualTo(10);
            assertThat(sheet.get("presentCompanions").get(1).get("age").asInt()).isEqualTo(9);
        }
        assertThat(ArrivalService.ageOn(LocalDate.of(2000, 2, 29), LocalDate.of(2001, 2, 28))).isZero();
        assertThat(ArrivalService.ageOn(LocalDate.of(2000, 2, 29), LocalDate.of(2001, 3, 1))).isEqualTo(1);
    }

    // H14 e H15
    @Test
    void missingOptionalDataComesAsNullOrEmpty() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking booking = book(me, day, 0);
        jdbc.sql("UPDATE leads SET birth_date = NULL, phone = NULL WHERE id = :id").param("id", booking.leadId()).update();
        enter(gate(), booking, at(day, 10, 0));

        clock.set(at(day, 12, 0));
        JsonNode sheet = sheet(admin(), booking.visitId());

        assertThat(sheet.get("lead").get("age").isNull()).isTrue();
        assertThat(sheet.get("lead").get("phone").isNull()).isTrue();
        assertThat(sheet.get("hostNotes").isNull()).isTrue();
        assertThat(sheet.get("presentCompanions").isEmpty()).isTrue();
        assertThat(sheet.get("absentCompanions").isEmpty()).isTrue();
    }

    // H16
    @Test
    void visitWithoutEntryIsConflictForReadersAndNotFoundForHost() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking scheduled = book(me, day, 0);
        Booking cancelled = book(me, day, 0);
        me.client().patch("/api/visits/" + cancelled.visitId() + "/cancel", Map.of()).andExpect(status().isOk());
        Booking noShow = book(me, day, 0);
        jdbc.sql("UPDATE visits SET status = 'NO_SHOW' WHERE id = :v").param("v", noShow.visitId()).update();
        Booking legacy = book(me, day, 0);
        jdbc.sql("DELETE FROM invitations WHERE visit_id = :v").param("v", legacy.visitId()).update();
        clock.set(at(day, 12, 0));
        ApiClient admin = admin();
        ApiClient host = host();
        String nonexistent = withoutInstance(body(host.get("/api/visits/" + UUID.randomUUID() + "/sheet")
                .andExpect(status().isNotFound())));

        for (Booking visit : List.of(scheduled, cancelled, noShow, legacy)) {
            for (ApiClient reader : List.of(admin, me.client())) {
                reader.get("/api/visits/" + visit.visitId() + "/sheet")
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("VISIT_NOT_ARRIVED"));
            }
            assertThat(withoutInstance(body(host.get("/api/visits/" + visit.visitId() + "/sheet")
                    .andExpect(status().isNotFound())))).isEqualTo(nonexistent);
        }
    }

    // H17
    @Test
    void unknownVisitIsNotFoundForEveryReader() throws Exception {
        ProspectorSession me = loggedInProspector();
        for (ApiClient client : List.of(admin(), me.client(), host())) {
            client.get("/api/visits/" + UUID.randomUUID() + "/sheet")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("VISIT_NOT_FOUND"));
        }
    }

    // H18
    @Test
    void prospectorOpensSheetAsResponsibleOrCurrentOwnerOnly() throws Exception {
        ProspectorSession responsible = loggedInProspector();
        ProspectorSession newOwner = loggedInProspector();
        ProspectorSession stranger = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking booking = book(responsible, day, 0);
        enter(gate(), booking, at(day, 10, 0));
        admin().patch("/api/leads/assign", Map.of("leadIds", List.of(booking.leadId()), "prospectorId", newOwner.prospector().getId()))
                .andExpect(status().isOk());
        // Dias depois: o PROSPECTOR abre fichas de qualquer data.
        clock.set(at(day.plusDays(3), 9, 0));

        sheet(responsible.client(), booking.visitId());
        sheet(newOwner.client(), booking.visitId());
        String nonexistent = withoutInstance(body(stranger.client().get("/api/visits/" + UUID.randomUUID() + "/sheet")));
        assertThat(withoutInstance(body(stranger.client().get("/api/visits/" + booking.visitId() + "/sheet")
                .andExpect(status().isNotFound())))).isEqualTo(nonexistent);
    }

    // H19
    @Test
    void hostOpensOnlyTodaysArrivalsAcrossMidnight() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking late = book(me, day, 0);
        Booking yesterday = book(me, day, 0);
        enter(gate(), yesterday, at(day, 9, 0));
        enter(gate(), late, at(day, 23, 59));
        ApiClient host = host();

        clock.set(at(day, 23, 59).plusSeconds(30));
        sheet(host, late.visitId());
        sheet(host, yesterday.visitId());

        clock.set(at(day.plusDays(1), 0, 1));
        String nonexistent = withoutInstance(body(host.get("/api/visits/" + UUID.randomUUID() + "/sheet")));
        for (Booking old : List.of(late, yesterday)) {
            assertThat(withoutInstance(body(host.get("/api/visits/" + old.visitId() + "/sheet")
                    .andExpect(status().isNotFound())))).isEqualTo(nonexistent);
        }
        // O ADMIN continua abrindo.
        sheet(admin(), late.visitId());
    }

    // H22
    @Test
    void reissuedVisitHasASingleArrivalFromTheUsedInvitation() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking booking = book(me, day, 1);
        JsonNode reissued = json(me.client().post("/api/invitations/" + booking.invitationId() + "/reissue", Map.of())
                .andExpect(status().isOk()));
        Booking current = new Booking(booking.leadId(), booking.visitId(),
                UUID.fromString(reissued.get("id").asString()), booking.companionIds());
        enter(gate(), current, List.of(), at(day, 10, 0));

        clock.set(at(day, 11, 0));
        assertThat(visitIds(arrivals(admin(), ""))).containsExactly(booking.visitId());
        JsonNode sheet = sheet(admin(), booking.visitId());
        assertThat(sheet.get("entryAt").asString()).isEqualTo(at(day, 10, 0).toString());
        assertThat(sheet.get("absentCompanions").size()).isEqualTo(1);
    }

    // H23
    @Test
    void arrivalStaysAfterTheLeadIsReassignedOrDiscarded() throws Exception {
        ProspectorSession me = loggedInProspector();
        ProspectorSession other = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking reassigned = book(me, day, 0);
        Booking discarded = book(me, day, 0);
        enter(gate(), reassigned, at(day, 9, 0));
        enter(gate(), discarded, at(day, 9, 30));
        ApiClient admin = admin();
        clock.set(at(day, 10, 0));
        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(reassigned.leadId()), "prospectorId", other.prospector().getId()))
                .andExpect(status().isOk());
        admin.patch("/api/leads/" + discarded.leadId() + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());

        ApiClient host = host();
        for (ApiClient reader : List.of(admin, host)) {
            assertThat(visitIds(arrivals(reader, ""))).containsExactly(discarded.visitId(), reassigned.visitId());
            sheet(reader, reassigned.visitId());
            sheet(reader, discarded.visitId());
        }
    }

    private static List<String> keysOf(JsonNode node) {
        List<String> keys = new ArrayList<>();
        if (node.isObject()) {
            for (Iterator<String> it = node.propertyNames().iterator(); it.hasNext(); ) {
                String key = it.next();
                keys.add(key);
                keys.addAll(keysOf(node.get(key)));
            }
        } else if (node.isArray()) {
            node.valueStream().forEach(child -> keys.addAll(keysOf(child)));
        }
        return keys;
    }
}
