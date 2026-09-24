package com.resort.platform.visits;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros da lista. {@code prospectorId} só vale para o ADMIN; {@code descending} inverte a ordem por data;
 * {@code history} exclui as visitas {@code SCHEDULED} de hoje em diante, que formam a Agenda (D-079).
 */
public record VisitFilter(
        VisitStatus status,
        LocalDate from,
        LocalDate to,
        UUID leadId,
        UUID prospectorId,
        boolean descending,
        boolean history) {}
