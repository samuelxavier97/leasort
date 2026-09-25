package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Próxima visita do PROSPECTOR: leitura da D-041; {@code canEdit} só para o dono atual do Lead (D-078). */
public record UpcomingVisit(UUID visitId, LocalDate scheduledDate, String leadName, int companionsCount, boolean canEdit) {}
