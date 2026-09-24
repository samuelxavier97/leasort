package com.resort.platform.visits.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateVisitRequest(
        @NotNull UUID leadId,
        @NotNull LocalDate scheduledDate,
        @Size(max = 2000) String notes,
        @Size(max = 2000) String hostNotes,
        List<@Valid @NotNull CompanionRequest> companions) {}
