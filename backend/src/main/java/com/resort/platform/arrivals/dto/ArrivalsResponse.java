package com.resort.platform.arrivals.dto;

import java.time.LocalDate;
import java.util.List;

/** Chegadas de um dia em {@code APP_TIMEZONE}, com a data usada pelo backend (D-095). */
public record ArrivalsResponse(LocalDate date, List<ArrivalResponse> arrivals) {}
