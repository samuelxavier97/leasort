package com.resort.platform.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/** Cartões e próximas visitas do PROSPECTOR (§16.7, D-098), sempre do Prospector da sessão. */
public record ProspectorSummary(
        LocalDate from,
        LocalDate to,
        LocalDate today,
        long myLeads,
        long scheduledVisits,
        long visitsToday,
        long activeInvitations,
        long completedVisits,
        List<UpcomingVisit> upcomingVisits) implements SummaryResponse {}
