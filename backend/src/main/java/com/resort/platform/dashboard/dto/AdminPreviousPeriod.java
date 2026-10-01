package com.resort.platform.dashboard.dto;

import java.time.LocalDate;

/** Os indicadores de período do ADMIN no período anterior de mesmo tamanho (D-128). */
public record AdminPreviousPeriod(
        LocalDate from, LocalDate to, long completedVisits, long noShows, long cancellations, long entries) {}
