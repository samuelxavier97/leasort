package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/** Acessos por dia em {@code APP_TIMEZONE}: liberados por {@code entry_at}, negados por {@code created_at} (D-098). */
public record AccessByDayResponse(LocalDate from, LocalDate to, List<Day> days) {

    public record Day(LocalDate date, long authorized, long denied) {}
}
