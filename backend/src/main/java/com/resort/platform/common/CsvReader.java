package com.resort.platform.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Leitor mínimo de CSV com separador configurável e aspas no padrão RFC 4180: campos entre aspas
 * podem conter o separador, quebras de linha e aspas escapadas ({@code ""}). Cada registro guarda o
 * número da linha física em que começa (a primeira linha do texto é a 1). Registros totalmente
 * vazios são descartados.
 */
public final class CsvReader {

    public record Row(int line, List<String> fields) {}

    private CsvReader() {}

    public static List<Row> parse(String text, char separator) {
        String content = text.startsWith("﻿") ? text.substring(1) : text;
        List<Row> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldWasQuoted = false;
        int line = 1;
        int rowStart = 1;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
                fieldWasQuoted = true;
            } else if (c == separator) {
                fields.add(field.toString());
                field.setLength(0);
                fieldWasQuoted = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                addRow(rows, rowStart, fields, fieldWasQuoted);
                fields = new ArrayList<>();
                field.setLength(0);
                fieldWasQuoted = false;
                line++;
                rowStart = line;
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !fields.isEmpty() || fieldWasQuoted) {
            fields.add(field.toString());
            addRow(rows, rowStart, fields, fieldWasQuoted);
        }
        return rows;
    }

    private static void addRow(List<Row> rows, int line, List<String> fields, boolean lastFieldQuoted) {
        boolean blank = !lastFieldQuoted && fields.stream().allMatch(value -> value.isBlank());
        if (!blank) {
            rows.add(new Row(line, List.copyOf(fields)));
        }
    }
}
