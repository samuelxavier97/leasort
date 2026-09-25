package com.resort.platform.dashboard.dto;

import java.time.LocalDate;

/** Período fechado nos dois extremos, em dias de {@code APP_TIMEZONE} (D-098). */
public record DashboardPeriod(LocalDate from, LocalDate to) {

    public long days() {
        return to.toEpochDay() - from.toEpochDay() + 1;
    }
}
