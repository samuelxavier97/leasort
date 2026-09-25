package com.resort.platform.exports;

import java.util.ArrayList;
import java.util.List;

/** Leitor RFC 4180 mínimo e independente do escritor, só para os testes: separador ;, aspas e CRLF. */
final class Rfc4180 {

    private Rfc4180() {}

    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == ';') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                i += 2;
                continue;
            } else {
                cell.append(c);
            }
            i++;
        }
        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
