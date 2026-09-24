package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.common.CsvReader.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReaderTest {

    @Test
    void splitsBySemicolonAndKeepsLineNumbers() {
        List<Row> rows = CsvReader.parse("a;b;c\n1;2;3\n", ';');

        assertThat(rows).containsExactly(new Row(1, List.of("a", "b", "c")), new Row(2, List.of("1", "2", "3")));
    }

    @Test
    void quotedFieldMayContainSeparatorAndEscapedQuotes() {
        List<Row> rows = CsvReader.parse("\"Silva; Ana\";\"diz \"\"oi\"\"\";x", ';');

        assertThat(rows.getFirst().fields()).containsExactly("Silva; Ana", "diz \"oi\"", "x");
    }

    @Test
    void handlesCrlfAndLf() {
        assertThat(CsvReader.parse("a;b\r\n1;2\r\n3;4", ';'))
                .extracting(Row::line)
                .containsExactly(1, 2, 3);
    }

    @Test
    void removesBomAndSkipsBlankLinesWithoutLosingPhysicalNumbering() {
        List<Row> rows = CsvReader.parse("﻿nome;cpf\n\n;\nAna;1\n", ';');

        assertThat(rows).containsExactly(new Row(1, List.of("nome", "cpf")), new Row(4, List.of("Ana", "1")));
    }

    @Test
    void multilineQuotedFieldStartsAtItsFirstLine() {
        List<Row> rows = CsvReader.parse("h1;h2\n\"linha 1\nlinha 2\";x\nfim;y\n", ';');

        assertThat(rows).containsExactly(
                new Row(1, List.of("h1", "h2")),
                new Row(2, List.of("linha 1\nlinha 2", "x")),
                new Row(4, List.of("fim", "y")));
    }

    @Test
    void keepsEmptyTrailingFields() {
        assertThat(CsvReader.parse("Ana;;", ';').getFirst().fields()).containsExactly("Ana", "", "");
    }
}
