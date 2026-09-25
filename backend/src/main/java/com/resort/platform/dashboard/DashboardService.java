package com.resort.platform.dashboard;

import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.dashboard.dto.AccessByDayResponse;
import com.resort.platform.dashboard.dto.AdminSummary;
import com.resort.platform.dashboard.dto.DashboardPeriod;
import com.resort.platform.dashboard.dto.ProspectorSummary;
import com.resort.platform.dashboard.dto.SummaryResponse;
import com.resort.platform.dashboard.dto.VisitsByDayResponse;
import com.resort.platform.prospectors.ProspectorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard (§16.7, D-098): período padrão de 30 dias até hoje, máximo de 366, em {@code APP_TIMEZONE}. O
 * PROSPECTOR vê só os próprios números; o filtro de Prospector é do ADMIN. Só leitura, sem auditoria.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final int UPCOMING_LIMIT = 10;

    private final DashboardQueries queries;
    private final ProspectorRepository prospectors;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public DashboardService(DashboardQueries queries, ProspectorRepository prospectors, BusinessCalendar calendar, Clock clock) {
        this.queries = queries;
        this.prospectors = prospectors;
        this.calendar = calendar;
        this.clock = clock;
    }

    public SummaryResponse summary(LocalDate from, LocalDate to, UUID prospectorId, Viewer viewer) {
        LocalDate today = calendar.today();
        DashboardPeriod period = period(from, to, today);
        UUID scope = scope(prospectorId, viewer);
        DashboardQueries.VisitCounts visits = queries.visits(today, period, scope);
        long activeInvitations = queries.activeInvitations(clock.instant(), scope);
        if (viewer.isProspector()) {
            return new ProspectorSummary(period.from(), period.to(), today, queries.leads(scope).total(),
                    visits.scheduled(), visits.visitsToday(), activeInvitations, visits.completed(),
                    queries.upcomingVisits(today, scope, UPCOMING_LIMIT));
        }
        DashboardQueries.LeadCounts leads = queries.leads(scope);
        long entries = queries.entries(calendar.startOfDay(period.from()), calendar.endOfDay(period.to()), scope);
        return new AdminSummary(period.from(), period.to(), today, leads.total(), leads.assigned(), visits.scheduled(),
                visits.visitsToday(), activeInvitations, visits.completed(), visits.noShows(), visits.cancellations(), entries);
    }

    public VisitsByDayResponse visitsByDay(LocalDate from, LocalDate to, UUID prospectorId, Viewer viewer) {
        DashboardPeriod period = period(from, to, calendar.today());
        return new VisitsByDayResponse(period.from(), period.to(), queries.visitsByDay(period, scope(prospectorId, viewer)));
    }

    /** Só ADMIN (SecurityConfig); o filtro de Prospector segue a mesma regra do resumo. */
    public AccessByDayResponse accessByDay(LocalDate from, LocalDate to, UUID prospectorId, Viewer viewer) {
        DashboardPeriod period = period(from, to, calendar.today());
        return new AccessByDayResponse(period.from(), period.to(), queries.accessByDay(period,
                calendar.startOfDay(period.from()), calendar.endOfDay(period.to()), calendar.zone().getId(),
                scope(prospectorId, viewer)));
    }

    /**
     * O PROSPECTOR nunca escolhe o escopo: qualquer {@code prospectorId} é 403, mesmo o próprio, para o
     * parâmetro não existir para ele. O ADMIN pode filtrar por um Prospector existente.
     */
    private UUID scope(UUID prospectorId, Viewer viewer) {
        if (viewer.isProspector()) {
            if (prospectorId != null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Você não tem permissão para esta ação.");
            }
            return viewer.prospectorId();
        }
        if (prospectorId != null && !prospectors.existsById(prospectorId)) {
            throw ApiException.notFound("PROSPECTOR_NOT_FOUND", "Prospector não encontrado.");
        }
        return prospectorId;
    }

    static DashboardPeriod period(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null && to == null) {
            return new DashboardPeriod(today.minusDays(DEFAULT_DAYS - 1L), today);
        }
        if (from == null || to == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Informe as duas datas do período.");
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A data inicial não pode ser posterior à final.");
        }
        DashboardPeriod period = new DashboardPeriod(from, to);
        if (period.days() > MAX_DAYS) {
            throw ApiException.badRequest("PERIOD_TOO_LONG", "O período pode ter no máximo " + MAX_DAYS + " dias.");
        }
        return period;
    }
}
