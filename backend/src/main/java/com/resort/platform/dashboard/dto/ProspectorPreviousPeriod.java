package com.resort.platform.dashboard.dto;

import java.time.LocalDate;

/** Os indicadores de período do PROSPECTOR no período anterior de mesmo tamanho (D-128). */
public record ProspectorPreviousPeriod(LocalDate from, LocalDate to, long completedVisits, long noShows) {}
