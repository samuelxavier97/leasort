package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/** Visitas por dia da data agendada, com todos os dias do período, inclusive os de valor zero (D-098). */
public record VisitsByDayResponse(LocalDate from, LocalDate to, List<Day> days) {

    public record Day(LocalDate date, long scheduled, long completed, long noShow, long cancelled) {}
}
