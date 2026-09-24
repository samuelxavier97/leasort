package com.resort.platform.imports;

/** Um erro do relatório de importação. Nunca contém o conteúdo da célula (D-065). */
public record ImportError(int line, String column, String code, String message) {}
