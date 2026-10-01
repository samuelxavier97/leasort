package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Cartões e próximas visitas do PROSPECTOR (§16.7, D-098), sempre do Prospector da sessão; mais a
 * saudação, as chegadas de hoje, o sem comparecimento (para a taxa), o período anterior e as visitas de hoje
 * (D-128).
 */
public record ProspectorSummary(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        Greeting greeting,
        long myLeads,
        long scheduledVisits,
        long visitsToday,
        long arrivedToday,
        long activeInvitations,
        long completedVisits,
        long noShows,
        ProspectorPreviousPeriod previous,
        List<UpcomingVisit> upcomingVisits,
        List<TodayVisit> todayVisits) implements SummaryResponse {}
