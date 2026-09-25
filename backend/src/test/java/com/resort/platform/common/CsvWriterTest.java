package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** D-019, D-068 e RFC 4180 no escritor de CSV. */
class CsvWriterTest {

    private static String write(String[]... rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvWriter csv = new CsvWriter(out);
        for (String[] row : rows) {
            csv.row(row);
        }
        csv.flush();
        return out.toString(StandardCharsets.UTF_8);
    }

    // X2: BOM, separador ; e CRLF.
    @Test
    void writesBomSemicolonAndCrlf() throws Exception {
        String text = write(new String[] {"ID", "Nome"}, new String[] {"1", "Ação"});

        assertThat(text).isEqualTo("﻿ID;Nome\r\n1;Ação\r\n");
        assertThat(text.getBytes(StandardCharsets.UTF_8)).startsWith(0xEF, 0xBB, 0xBF);
    }

    @Test
    void writesTheBomOnlyOnceAndNullAsEmpty() throws Exception {
        assertThat(write(new String[] {"a", null, ""}, new String[] {"b"})).isEqualTo("﻿a;;\r\nb\r\n");
    }

    // X3: RFC 4180.
    @Test
    void quotesSeparatorQuotesAndLineBreaks() {
        assertThat(CsvWriter.cell("Silva; Souza")).isEqualTo("\"Silva; Souza\"");
        assertThat(CsvWriter.cell("Dona \"Bia\"")).isEqualTo("\"Dona \"\"Bia\"\"\"");
        assertThat(CsvWriter.cell("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
        assertThat(CsvWriter.cell("linha 1\r\nlinha 2")).isEqualTo("\"linha 1\r\nlinha 2\"");
        assertThat(CsvWriter.cell("sem nada especial")).isEqualTo("sem nada especial");
    }

    // X4: D-068, um caso por caractere.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"=", "+", "-", "@"})
    void neutralizesFormulaPrefixes(String prefix) {
        assertThat(CsvWriter.cell(prefix + "SOMA(A1:A2)")).isEqualTo("'" + prefix + "SOMA(A1:A2)");
    }

    // X4: com \t e \r no início, primeiro o apóstrofo, depois as aspas: o apóstrofo fica dentro delas.
    @Test
    void tabAtTheStartGetsTheApostropheInsideQuotes() {
        assertThat(CsvWriter.cell("\t=1+1")).isEqualTo("\"'\t=1+1\"");
    }

    @Test
    void carriageReturnAtTheStartGetsTheApostropheInsideQuotes() {
        assertThat(CsvWriter.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
    }

    @Test
    void neutralizedValuesWithSeparatorOrQuotesStillFollowRfc4180() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"http://x\";\"clique\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://x\"\";\"\"clique\"\")\"");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"a=b", "1+1", "Maria-José", "e@mail", "a\tb"})
    void onlyTheFirstCharacterMatters(String value) {
        assertThat(CsvWriter.cell(value)).doesNotStartWith("'").doesNotStartWith("\"'");
    }

    @Test
    void phoneWithCountryCodeIsNeutralizedToo() {
        assertThat(CsvWriter.cell("+55 11 90000-0000")).isEqualTo("'+55 11 90000-0000");
    }
}
