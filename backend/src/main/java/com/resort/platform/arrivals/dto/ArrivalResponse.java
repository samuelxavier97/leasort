package com.resort.platform.arrivals.dto;

import java.time.Instant;
import java.util.UUID;

/** Uma chegada (§16.6): entrada liberada, sem CPF nem outro dado além da lista. */
public record ArrivalResponse(UUID visitId, Instant entryAt, String leadName, int companionsPresent, String prospectorName) {}
