package com.resort.platform.visits.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Edição da visita: a lista de acompanhantes substitui a atual, casando por id (D-075). */
public record UpdateVisitRequest(
        @Size(max = 2000) String notes,
        @Size(max = 2000) String hostNotes,
        @NotNull List<@Valid @NotNull CompanionRequest> companions) {}
