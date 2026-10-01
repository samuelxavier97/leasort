package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Desempenho por Prospector no período (D-128), só do ADMIN: visitas realizadas, sem comparecimento e a
 * taxa de comparecimento (realizadas ÷ realizadas + sem comparecimento). Só entra quem teve ao menos uma das
 * duas no período; ordem por realizadas, depois taxa e nome.
 */
public record ProspectorPerformanceResponse(LocalDate from, LocalDate to, List<Row> prospectors) {

    public record Row(UUID prospectorId, String name, long completedVisits, long noShows, double attendanceRate) {}
}
