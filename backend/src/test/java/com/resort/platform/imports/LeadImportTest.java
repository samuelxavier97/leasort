package com.resort.platform.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.FakeCpf;
import com.resort.platform.HttpBrowser;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * Importação CSV (SPEC §17, D-018, D-065). Todos os arquivos são montados em memória com dados
 * fictícios gerados; nenhum CSV de fixture fica no repositório.
 */
class LeadImportTest extends IntegrationTestSupport {

    static final String HEADER = "nome;cpf;telefone;email;data_nascimento;codigo_prospector;observacoes";

    @LocalServerPort
    int port;

    User adminUser;
    ApiClient admin;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        adminUser = testData.user(Role.ADMIN);
        admin = client().login(adminUser.getEmail(), TestData.PASSWORD);
        token = "Imp" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void importsValidFileWithBom() throws Exception {
        Prospector prospector = testData.prospectorOf(testData.user(Role.PROSPECTOR));
        String cpf = FakeCpf.generate();
        String csv = "﻿" + HEADER + "\n"
                + row(name(1), FakeCpf.formatted(cpf), "11 90000-0001", "Lead.Um@Test.Local", "05/03/1980", prospector.getEmployeeCode(), "Prefere manhã") + "\n"
                + row(name(2), "", "", "", "", "", "") + "\n"
                + row(name(3), FakeCpf.generate(), "", "", "29/02/2000", "", "") + "\n";

        upload(csv).andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(3))
                .andExpect(jsonPath("$.totalRows").value(3));

        var first = jdbc.sql("SELECT cpf, email, birth_date, status, prospector_id, notes FROM leads WHERE name = :name")
                .param("name", name(1))
                .query((rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6)))
                .single();
        assertThat(first).containsExactly(cpf, "lead.um@test.local", LocalDate.of(1980, 3, 5), LeadStatus.NEW.name(),
                prospector.getId(), "Prefere manhã");
        assertThat(importedCount()).isEqualTo(3);
        assertThat(jdbc.sql("""
                        SELECT metadata::text FROM audit_logs WHERE action = 'LEAD_IMPORTED' AND user_id = :user
                        """).param("user", adminUser.getId()).query(String.class).single())
                .contains("\"count\": 3").contains("\"assignedCount\": 1");
    }

    @Test
    void acceptsNoBomCrlfQuotedSeparatorAndReorderedColumns() throws Exception {
        String csv = "observacoes;codigo_prospector;data_nascimento;email;telefone;cpf;nome\r\n"
                + "\"Nota; com ponto e vírgula\";;;;;;" + name(1) + "\r\n"
                + ";;;;;;\"" + name(2) + "\"\r\n";

        upload(csv).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(2));

        assertThat(jdbc.sql("SELECT notes FROM leads WHERE name = :name").param("name", name(1)).query(String.class).single())
                .isEqualTo("Nota; com ponto e vírgula");
    }

    @Test
    void oneBadRowRejectsTheWholeFile() throws Exception {
        String csv = HEADER + "\n"
                + row(name(1), "", "", "", "", "", "") + "\n"
                + row(name(2), FakeCpf.invalid(), "", "", "", "", "") + "\n"
                + row(name(3), "", "", "", "", "", "") + "\n";

        upload(csv).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMPORT_REJECTED"))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.errors[0].line").value(3))
                .andExpect(jsonPath("$.errors[0].code").value("CPF_INVALID"));

        assertThat(importedCount()).isZero();
    }

    @Test
    void reportsEveryErrorKindOnThePhysicalLine() throws Exception {
        String existing = FakeCpf.generate();
        testData.lead(null, existing, LeadStatus.NEW);
        String repeated = FakeCpf.generate();
        User inactiveUser = testData.user(Role.PROSPECTOR, TestData.PASSWORD, false, false);
        String inactiveCode = testData.prospectorOf(inactiveUser).getEmployeeCode();
        String csv = HEADER + "\n"                                                          // 1
                + row(name(1), "", "", "", "", "", "") + "\n"                               // 2 válida
                + "\n"                                                                      // 3 vazia
                + row(name(4), FakeCpf.invalid(), "", "", "", "", "") + "\n"                // 4
                + row(name(5), existing, "", "", "", "", "") + "\n"                         // 5
                + row(name(6), repeated, "", "", "", "", "") + "\n"                         // 6
                + row(name(7), FakeCpf.formatted(repeated), "", "", "", "", "") + "\n"      // 7
                + row(name(8), repeated, "", "", "", "", "") + "\n"                         // 8
                + row(name(9), "", "", "", "31/02/1990", "", "") + "\n"                     // 9
                + row(name(10), "", "", "sem-arroba", "", "", "") + "\n"                    // 10
                + row(name(11), "", "telefone?", "", "", "", "") + "\n"                     // 11
                + row(name(12), "", "", "", "", "NAO-EXISTE-" + token, "") + "\n"           // 12
                + row(name(13), "", "", "", "", inactiveCode, "") + "\n"                    // 13
                + row("", "", "11 90000-0000", "", "", "", "") + "\n"                        // 14
                + name(15) + ";;;;;\n";                                                     // 15 (6 colunas)

        JsonNode errors = json(upload(csv).andExpect(status().isUnprocessableContent())).get("errors");

        List<String> found = new ArrayList<>();
        errors.forEach(e -> found.add(e.get("line").asInt() + ":" + text(e.get("column")) + ":" + e.get("code").asString()));
        assertThat(found).containsExactly(
                "4:cpf:CPF_INVALID",
                "5:cpf:CPF_ALREADY_EXISTS",
                "7:cpf:CPF_DUPLICATED_IN_FILE",
                "8:cpf:CPF_DUPLICATED_IN_FILE",
                "9:data_nascimento:DATE_INVALID",
                "10:email:EMAIL_INVALID",
                "11:telefone:PHONE_INVALID",
                "12:codigo_prospector:PROSPECTOR_NOT_FOUND",
                "13:codigo_prospector:PROSPECTOR_INACTIVE",
                "14:nome:REQUIRED",
                "15:null:COLUMN_COUNT_MISMATCH");
        assertThat(errors.get(2).get("message").asString()).contains("linha 6");
        assertThat(errors.get(3).get("message").asString()).contains("linha 6");
        assertThat(importedCount()).isZero();
    }

    @Test
    void reportsAllErrorsOfTheSameLine() throws Exception {
        String csv = HEADER + "\n" + row("", FakeCpf.invalid(), "", "invalido", "1990-01-01", "", "") + "\n";

        JsonNode errors = json(upload(csv).andExpect(status().isUnprocessableContent())).get("errors");

        assertThat(errors.valueStream().map(e -> e.get("column").asString()).toList())
                .containsExactly("nome", "cpf", "email", "data_nascimento");
    }

    @Test
    void invalidHeaders() throws Exception {
        for (String header : List.of(
                row(name(1), "", "", "", "", "", ""),
                "nome;cpf;telefone;email;data_nascimento;codigo_prospector",
                HEADER + ";extra",
                "nome;nome;telefone;email;data_nascimento;codigo_prospector;observacoes")) {
            upload(header + "\n" + row(name(2), "", "", "", "", "", "") + "\n")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_HEADER"));
        }
    }

    @Test
    void importsExactlyTheRowLimit() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= LeadImportService.MAX_ROWS; i++) {
            csv.append(row(name(i), FakeCpf.generate(), "11 90000-0000", "", "", "", "")).append('\n');
        }

        upload(csv.toString()).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(5000));

        assertThat(importedCount()).isEqualTo(5000);
    }

    @Test
    void rejectsMoreThanTheRowLimitWithoutValidatingRows() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int i = 1; i <= LeadImportService.MAX_ROWS + 1; i++) {
            csv.append(row(name(i), "", "", "", "", "", "")).append('\n');
        }

        upload(csv.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ROWS"));
        assertThat(importedCount()).isZero();
    }

    @Test
    void emptyFiles() throws Exception {
        for (String csv : List.of("", HEADER, HEADER + "\n\n;;;;;;\n", "﻿")) {
            upload(csv).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        }
    }

    @Test
    void rejectsNonUtf8() throws Exception {
        byte[] latin1 = (HEADER + "\n" + row("José da Conceição", "", "", "", "", "", "") + "\n")
                .getBytes(StandardCharsets.ISO_8859_1);

        admin.upload("/api/leads/import", "leads.csv", latin1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENCODING"));
    }

    @Test
    void reportNeverEchoesCellValues() throws Exception {
        String badCpf = FakeCpf.invalid();
        String badEmail = "email-invalido-" + token;
        String csv = HEADER + "\n" + row(name(1), badCpf, "", badEmail, "99/99/9999", "COD-" + token, "") + "\n";

        String body = upload(csv).andExpect(status().isUnprocessableContent()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(badCpf, FakeCpf.formatted(badCpf), badEmail, name(1), "99/99/9999", "COD-" + token);
    }

    @Test
    void templateHasBomAndExactHeader() throws Exception {
        byte[] bytes = admin.get("/api/leads/import/template")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"modelo-importacao-leads.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("﻿" + HEADER + "\r\n");
    }

    @Test
    void filledTemplateImports() throws Exception {
        String template = new String(admin.get("/api/leads/import/template").andReturn().getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);

        upload(template + row(name(1), FakeCpf.generate(), "", "", "01/01/1990", "", "") + "\r\n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
    }

    /** O limite de 5 MB é aplicado pelo servidor real no parsing do multipart; o MockMvc não o reproduz. */
    @Test
    void fileAboveFiveMegabytesIsRejected() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login", jsonMapper.writeValueAsString(Map.of("email", adminUser.getEmail(), "password", TestData.PASSWORD)));
        byte[] big = new byte[5 * 1024 * 1024 + 1024];
        java.util.Arrays.fill(big, (byte) 'a');

        var response = browser.upload("/api/leads/import", "grande.csv", big);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo("FILE_TOO_LARGE");
    }

    private ResultActions upload(String csv) throws Exception {
        return admin.upload("/api/leads/import", "leads.csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private long importedCount() {
        return jdbc.sql("SELECT count(*) FROM leads WHERE name LIKE :prefix").param("prefix", "Lead " + token + "%")
                .query(Long.class).single();
    }

    private String name(int i) {
        return "Lead " + token + " " + i;
    }

    private static String row(String... values) {
        return String.join(";", values);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "null" : node.asString();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
