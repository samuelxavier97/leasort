package com.resort.platform.visits;

import java.time.LocalDate;
import java.util.UUID;

/** Filtros da lista. {@code prospectorId} só vale para o ADMIN; {@code descending} inverte a ordem por data. */
public record VisitFilter(
        VisitStatus status, LocalDate from, LocalDate to, UUID leadId, UUID prospectorId, boolean descending) {}
