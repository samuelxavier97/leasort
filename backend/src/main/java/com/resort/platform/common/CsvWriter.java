package com.resort.platform.common;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * CSV para o Excel em português (D-019): BOM UTF-8, separador {@code ;} e linhas em CRLF. Cada célula passa
 * primeiro pela proteção contra fórmula (D-068) e depois pelas aspas do RFC 4180, nessa ordem: um valor que
 * começa com tabulação ou retorno de carro recebe o apóstrofo e sai entre aspas com o apóstrofo dentro.
 * Escreve direto na saída, linha a linha, sem acumular o arquivo em memória.
 */
public final class CsvWriter {

    private static final char SEPARATOR = ';';

    private final Writer out;
    private boolean started;

    public CsvWriter(OutputStream output) {
        this.out = new OutputStreamWriter(output, StandardCharsets.UTF_8);
    }

    public void row(String... cells) throws IOException {
        if (!started) {
            out.write('﻿');
            started = true;
        }
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.write(SEPARATOR);
            }
            out.write(cell(cells[i]));
        }
        out.write("\r\n");
    }

    public void flush() throws IOException {
        out.flush();
    }

    /** D-068 e RFC 4180, nessa ordem. {@code null} vira célula vazia. */
    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = neutralize(value);
        boolean quote = safe.indexOf(SEPARATOR) >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0 || safe.indexOf('\t') >= 0;
        return quote ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
    }

    /** Célula que começa com {@code = + - @ \t \r} ganha o prefixo {@code '}: o Excel não a trata como fórmula. */
    static String neutralize(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value
                : value;
    }
}
