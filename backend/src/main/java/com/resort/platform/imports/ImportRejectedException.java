package com.resort.platform.imports;

import java.util.List;

/** Importação recusada por erros de linha; nada foi gravado (tudo ou nada, D-018). */
class ImportRejectedException extends RuntimeException {

    private final int totalRows;
    private final List<ImportError> errors;

    ImportRejectedException(int totalRows, List<ImportError> errors) {
        super("O arquivo tem " + errors.size() + (errors.size() == 1 ? " erro" : " erros") + ". Nenhum Lead foi importado.");
        this.totalRows = totalRows;
        this.errors = errors;
    }

    int getTotalRows() {
        return totalRows;
    }

    List<ImportError> getErrors() {
        return errors;
    }
}
