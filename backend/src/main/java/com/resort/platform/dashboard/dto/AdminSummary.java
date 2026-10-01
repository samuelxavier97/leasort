package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Cartões do ADMIN (§16.7, D-098). Fotografias e contagens no período, com o filtro de Prospector aplicado;
 * mais a saudação, as chegadas de hoje, o período anterior e as visitas de hoje (D-128).
 */
public record AdminSummary(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        Greeting greeting,
        long totalLeads,
        long assignedLeads,
        long scheduledVisits,
        long visitsToday,
        long arrivedToday,
        long activeInvitations,
        long completedVisits,
        long noShows,
        long cancellations,
        long entries,
        AdminPreviousPeriod previous,
        List<TodayVisit> todayVisits) implements SummaryResponse {}
