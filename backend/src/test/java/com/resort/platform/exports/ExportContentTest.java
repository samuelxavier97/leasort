package com.resort.platform.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.leads.LeadStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Conteúdo e filtros dos quatro arquivos (§18, D-101). */
class ExportContentTest extends ExportTestSupport {

    // X5
    @Test
    void leadsFileHasEveryColumnInPortugueseAndFiltersByCreationOwnerAndStatus() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession ana = loggedInProspector();
        ProspectorSession bia = loggedInProspector();
        ApiClient admin = admin();
        String cpf = FakeCpf.generate();
        UUID full = testData.lead(ana.prospector(), cpf, LeadStatus.CONTACTED).getId();
        jdbc.sql("UPDATE leads SET name = 'Ação; \"Teste\"', phone = '+55 11 90000-0000', email = 'lead@ficticio.test', "
                        + "birth_date = DATE '1985-04-12', created_at = :at WHERE id = :id")
                .param("at", at(day, 23, 59).atOffset(java.time.ZoneOffset.UTC)).param("id", full).update();
        UUID nextDay = testData.lead(ana.prospector(), null, LeadStatus.CANCELLED).getId();
        jdbc.sql("UPDATE leads SET created_at = :at WHERE id = :id")
                .param("at", at(day.plusDays(1), 0, 1).atOffset(java.time.ZoneOffset.UTC)).param("id", nextDay).update();
        UUID reassigned = testData.lead(ana.prospector()).getId();
        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(reassigned), "prospectorId", bia.prospector().getId()))
                .andExpect(status().isOk());
        String p = "prospectorId=" + ana.prospector().getId();

        Download all = download(admin, "/api/exports/leads" + query(p));
        assertThat(all.header()).containsExactly("ID", "Nome", "CPF", "Telefone", "E-mail", "Nascimento", "Status",
                "Prospector", "Criado em");
        assertThat(all.data()).extracting(row -> row.getFirst()).containsExactlyInAnyOrder(full.toString(), nextDay.toString());
        List<String> row = all.data().stream().filter(r -> r.getFirst().equals(full.toString())).findFirst().orElseThrow();
        assertThat(row).containsExactly(full.toString(), "Ação; \"Teste\"", FakeCpf.formatted(cpf), "'+55 11 90000-0000",
                "lead@ficticio.test", "12/04/1985", "Contatado", ana.user().getName(), "%s 23:59".formatted(br(day)));
        assertThat(all.text()).contains("\"Ação; \"\"Teste\"\"\"");

        // Período pela criação no fuso da operação: 23:59 e 00:01 caem em dias diferentes.
        assertThat(download(admin, "/api/exports/leads" + query(p, "from=" + day, "to=" + day)).data())
                .extracting(r -> r.getFirst()).containsExactly(full.toString());
        assertThat(download(admin, "/api/exports/leads" + query(p, "from=" + day.plusDays(1), "to=" + day.plusDays(1))).data())
                .extracting(r -> r.getFirst()).containsExactly(nextDay.toString());
        // Status, incluindo Descartado; Prospector pelo dono atual.
        assertThat(download(admin, "/api/exports/leads" + query(p, "status=CANCELLED")).data())
                .extracting(r -> r.get(6)).containsExactly("Descartado");
        assertThat(download(admin, "/api/exports/leads" + query("prospectorId=" + bia.prospector().getId())).data())
                .extracting(r -> r.getFirst()).containsExactly(reassigned.toString());
        // Lead sem dono: Prospector vazio.
        UUID orphan = testData.lead(null).getId();
        jdbc.sql("UPDATE leads SET created_at = :at WHERE id = :id")
                .param("at", at(day.plusDays(2), 12, 0).atOffset(java.time.ZoneOffset.UTC)).param("id", orphan).update();
        List<String> orphanRow = download(admin, "/api/exports/leads" + query("from=" + day.plusDays(2), "to=" + day.plusDays(2)))
                .data().stream().filter(r -> r.getFirst().equals(orphan.toString())).findFirst().orElseThrow();
        assertThat(orphanRow.get(7)).isEmpty();
    }

    // X6
    @Test
    void visitsFileDistinguishesCancellationRescheduleAndDiscardAndCreditsTheResponsible() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession ana = loggedInProspector();
        ProspectorSession bia = loggedInProspector();
        ApiClient admin = admin();
        String cpf = FakeCpf.generate();
        UUID leadId = testData.lead(ana.prospector(), cpf, LeadStatus.NEW).getId();
        Booking done = book(ana, leadId, day, List.of(companion("Acompanhante Um", "SPOUSE"), companion("Acompanhante Dois", "CHILD")));
        enter(gate(), done, done.companionIds().subList(0, 1), at(day, 9, 30));
        Booking cancelled = book(ana, day);
        ana.client().patch("/api/visits/" + cancelled.visitId() + "/cancel", Map.of()).andExpect(status().isOk());
        Booking rescheduled = book(ana, day);
        ana.client().post("/api/visits/" + rescheduled.visitId() + "/reschedule", Map.of("scheduledDate", day.plusDays(1).toString()))
                .andExpect(status().isOk());
        Booking discarded = book(ana, day);
        admin.patch("/api/leads/" + discarded.leadId() + "/status", Map.of("status", "CANCELLED")).andExpect(status().isOk());
        // Crédito da visita fica com quem agendou, mesmo depois da reatribuição (D-013).
        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(leadId), "prospectorId", bia.prospector().getId()))
                .andExpect(status().isOk());
        String range = "from=" + day + "&to=" + day;

        Download file = download(admin, "/api/exports/visits" + query("prospectorId=" + ana.prospector().getId(), range));
        assertThat(file.header()).containsExactly("ID", "Lead", "CPF do Lead", "Prospector", "Data", "Status",
                "Motivo do cancelamento", "Acompanhantes", "Entrada em");
        Map<String, List<String>> byId = byFirstColumn(file);
        assertThat(byId.keySet()).containsExactlyInAnyOrder(done.visitId().toString(), cancelled.visitId().toString(),
                rescheduled.visitId().toString(), discarded.visitId().toString());
        assertThat(byId.get(done.visitId().toString())).containsExactly(done.visitId().toString(), leadName(leadId),
                FakeCpf.formatted(cpf), ana.user().getName(), br(day), "Realizada", "", "2", br(day) + " 09:30");
        assertThat(byId.get(cancelled.visitId().toString()).subList(5, 9)).containsExactly("Cancelada", "Cancelada pelo usuário", "0", "");
        assertThat(byId.get(rescheduled.visitId().toString()).subList(5, 7)).containsExactly("Cancelada", "Remarcação");
        assertThat(byId.get(discarded.visitId().toString()).subList(5, 7)).containsExactly("Cancelada", "Lead descartado");

        assertThat(download(admin, "/api/exports/visits" + query("prospectorId=" + bia.prospector().getId(), range)).data()).isEmpty();
        assertThat(download(admin, "/api/exports/visits" + query("prospectorId=" + ana.prospector().getId(), range, "status=COMPLETED"))
                .data()).extracting(r -> r.getFirst()).containsExactly(done.visitId().toString());
        // A visita nova da remarcação é do dia seguinte.
        assertThat(download(admin, "/api/exports/visits" + query("prospectorId=" + ana.prospector().getId(),
                "from=" + day.plusDays(1), "to=" + day.plusDays(1))).data()).extracting(r -> r.get(5)).containsExactly("Agendada");
    }

    // X7
    @Test
    void companionsFileShowsPresenceOnlyForCompletedVisits() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession ana = loggedInProspector();
        ApiClient admin = admin();
        Companion present = companion("Acompanhante Presente", "SPOUSE");
        Companion absent = companion("Acompanhante Ausente", "GRANDPARENT");
        Booking done = book(ana, testData.lead(ana.prospector()).getId(), day, List.of(present, absent));
        enter(gate(), done, done.companionIds().subList(0, 1), at(day, 10, 0));
        Booking pending = book(ana, testData.lead(ana.prospector()).getId(), day.plusDays(1),
                List.of(companion("Acompanhante Futuro", "FRIEND")));
        String p = "prospectorId=" + ana.prospector().getId();

        Download file = download(admin, "/api/exports/companions" + query(p));
        assertThat(file.header()).containsExactly("ID da visita", "Data da visita", "Lead", "Nome", "CPF", "Nascimento",
                "Parentesco", "Presente");
        assertThat(file.data()).containsExactly(
                List.of(done.visitId().toString(), br(day), leadName(done.leadId()), "Acompanhante Presente",
                        FakeCpf.formatted(present.cpf()), "04/03/2012", "Cônjuge", "Sim"),
                List.of(done.visitId().toString(), br(day), leadName(done.leadId()), "Acompanhante Ausente",
                        FakeCpf.formatted(absent.cpf()), "04/03/2012", "Avô/Avó", "Não"),
                List.of(pending.visitId().toString(), br(day.plusDays(1)), leadName(pending.leadId()), "Acompanhante Futuro",
                        FakeCpf.formatted(pending.companionIds().isEmpty() ? "" : cpfOf(pending.companionIds().getFirst())),
                        "04/03/2012", "Amigo(a)", ""));
        assertThat(download(admin, "/api/exports/companions" + query(p, "status=SCHEDULED")).data())
                .extracting(r -> r.getFirst()).containsExactly(pending.visitId().toString());
    }

    // X8
    @Test
    void accessFileNeverCarriesTheAttemptedCodeAndShowsInvalidCodeWithoutLead() throws Exception {
        LocalDate day = uniqueDay();
        ProspectorSession ana = loggedInProspector();
        ApiClient admin = admin();
        ApiClient gate = gate();
        Booking entered = book(ana, testData.lead(ana.prospector()).getId(), day, List.of(companion("Um", "FRIEND"), companion("Dois", "FRIEND")));
        enter(gate, entered, entered.companionIds(), at(day, 23, 59));
        Booking tomorrow = book(ana, day.plusDays(1));
        String tomorrowCode = codeOf(tomorrow.invitationId());
        clock.set(at(day, 23, 59).plusSeconds(20));
        gate.post("/api/access/validate", Map.of("code", tomorrowCode)).andExpect(jsonPath("$.denialReason").value("WRONG_DATE"));
        String invalid = "Q9Q9Q9Q9Q9";
        gate.post("/api/access/validate", Map.of("code", invalid)).andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        clock.set(at(day.plusDays(1), 0, 1));
        gate.post("/api/access/validate", Map.of("code", invalid)).andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        String range = "from=" + day + "&to=" + day;

        Download file = download(admin, "/api/exports/access" + query(range));
        assertThat(file.header()).containsExactly("Data e hora", "Resultado", "Motivo", "Lead", "Porteiro", "Portaria",
                "Acompanhantes presentes");
        String gateName = jdbc.sql("SELECT u.name FROM access_records r JOIN users u ON u.id = r.validated_by_user_id "
                + "WHERE r.invitation_id = :i").param("i", entered.invitationId()).query(String.class).single();
        assertThat(file.data()).contains(
                List.of(br(day) + " 23:59", "Liberado", "", leadName(entered.leadId()), gateName, "PRINCIPAL", "2"),
                List.of(br(day) + " 23:59", "Negado", "Fora da data", leadName(tomorrow.leadId()), gateName, "PRINCIPAL", ""),
                List.of(br(day) + " 23:59", "Negado", "Código inválido", "", gateName, "PRINCIPAL", ""));
        assertThat(file.text()).doesNotContain(invalid).doesNotContain(tomorrowCode).doesNotContain(codeOf(entered.invitationId()));
        // A tentativa das 00:01 é do dia seguinte.
        assertThat(file.data()).filteredOn(r -> r.getFirst().startsWith(br(day.plusDays(1)))).isEmpty();
        assertThat(download(admin, "/api/exports/access" + query("from=" + day.plusDays(1), "to=" + day.plusDays(1))).data())
                .anyMatch(r -> r.get(2).equals("Código inválido"));

        // Filtro de resultado; com o Prospector, a negativa sem convite sai.
        assertThat(download(admin, "/api/exports/access" + query(range, "status=AUTHORIZED")).data())
                .allMatch(r -> r.get(1).equals("Liberado"));
        assertThat(download(admin, "/api/exports/access" + query(range, "prospectorId=" + ana.prospector().getId())).data())
                .extracting(r -> r.get(2)).containsExactlyInAnyOrder("", "Fora da data");
    }

    private Map<String, List<String>> byFirstColumn(Download file) {
        Map<String, List<String>> map = new java.util.HashMap<>();
        file.data().forEach(r -> map.put(r.getFirst(), r));
        return map;
    }

    private String leadName(UUID leadId) {
        return jdbc.sql("SELECT name FROM leads WHERE id = :id").param("id", leadId).query(String.class).single();
    }

    private String cpfOf(UUID companionId) {
        return jdbc.sql("SELECT cpf FROM visit_companions WHERE id = :id").param("id", companionId).query(String.class).single();
    }

    private String codeOf(UUID invitationId) {
        return jdbc.sql("SELECT code FROM invitations WHERE id = :id").param("id", invitationId).query(String.class).single().trim();
    }

    static String br(LocalDate date) {
        return "%02d/%02d/%d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }
}
