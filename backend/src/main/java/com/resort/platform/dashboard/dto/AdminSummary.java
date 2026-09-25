package com.resort.platform.dashboard.dto;

import java.time.LocalDate;

/** Cartões do ADMIN (§16.7, D-098). Fotografias e contagens no período, com o filtro de Prospector aplicado. */
public record AdminSummary(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        long totalLeads,
        long assignedLeads,
        long scheduledVisits,
        long visitsToday,
        long activeInvitations,
        long completedVisits,
        long noShows,
        long cancellations,
        long entries) implements SummaryResponse {}
