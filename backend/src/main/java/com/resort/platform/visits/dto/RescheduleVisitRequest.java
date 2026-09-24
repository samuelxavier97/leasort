package com.resort.platform.visits.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RescheduleVisitRequest(@NotNull LocalDate scheduledDate) {}
