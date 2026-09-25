package com.resort.platform.exports;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Filtros já validados (D-101): período opcional, status já convertido para o nome do enum, Prospector. */
record ExportFilters(LocalDate from, LocalDate to, String status, UUID prospectorId) {

    boolean hasPeriod() {
        return from != null;
    }

    /** Para o metadata da auditoria: só ids e valores de filtro, nenhum dado pessoal. */
    Map<String, Object> asMetadata() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", from == null ? null : from.toString());
        filters.put("to", to == null ? null : to.toString());
        filters.put("status", status);
        filters.put("prospectorId", prospectorId == null ? null : prospectorId.toString());
        return filters;
    }
}
