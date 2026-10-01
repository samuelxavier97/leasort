package com.resort.platform.dashboard;

import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.common.DatePeriods;
import com.resort.platform.access.DenialReason;
import com.resort.platform.dashboard.dto.AdminPreviousPeriod;
import com.resort.platform.dashboard.dto.AdminSummary;
import com.resort.platform.dashboard.dto.DashboardPeriod;
import com.resort.platform.dashboard.dto.DenialsResponse;
import com.resort.platform.dashboard.dto.Greeting;
import com.resort.platform.dashboard.dto.ProspectorPerformanceResponse;
import com.resort.platform.dashboard.dto.ProspectorPreviousPeriod;
import com.resort.platform.dashboard.dto.ProspectorSummary;
import com.resort.platform.dashboard.dto.SummaryResponse;
import com.resort.platform.dashboard.dto.VisitsByDayResponse;
import com.resort.platform.prospectors.ProspectorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard (§16.7, D-098, D-128, D-129): período padrão de 30 dias até hoje, máximo de 366, em
 * {@code APP_TIMEZONE}. O PROSPECTOR vê só os próprios números; o filtro de Prospector é do ADMIN. Só leitura,
 * sem auditoria.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final int UPCOMING_LIMIT = 10;
    static final int TODAY_LIMIT = 50;

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
        DashboardPeriod previous = previous(period);
        UUID scope = scope(prospectorId, viewer);
        Greeting greeting = Greeting.at(clock.instant().atZone(calendar.zone()).getHour());
        DashboardQueries.VisitCounts visits = queries.visits(today, period, previous, scope);
        long activeInvitations = queries.activeInvitations(clock.instant(), scope);
        var todayVisits = queries.todayVisits(today, scope, TODAY_LIMIT);
        if (viewer.isProspector()) {
            return new ProspectorSummary(period.from(), period.to(), today, greeting, queries.leads(scope).total(),
                    visits.scheduled(), visits.visitsToday(), visits.arrivedToday(), activeInvitations, visits.completed(),
                    visits.noShows(),
                    new ProspectorPreviousPeriod(previous.from(), previous.to(), visits.previousCompleted(),
                            visits.previousNoShows()),
                    queries.upcomingVisits(today, scope, UPCOMING_LIMIT), todayVisits);
        }
        DashboardQueries.LeadCounts leads = queries.leads(scope);
        long entries = queries.entries(calendar.startOfDay(period.from()), calendar.endOfDay(period.to()), scope);
        long previousEntries = queries.entries(calendar.startOfDay(previous.from()), calendar.endOfDay(previous.to()), scope);
        return new AdminSummary(period.from(), period.to(), today, greeting, leads.total(), leads.assigned(),
                visits.scheduled(), visits.visitsToday(), visits.arrivedToday(), activeInvitations, visits.completed(),
                visits.noShows(), visits.cancellations(), entries,
                new AdminPreviousPeriod(previous.from(), previous.to(), visits.previousCompleted(), visits.previousNoShows(),
                        visits.previousCancellations(), previousEntries),
                todayVisits);
    }

    public VisitsByDayResponse visitsByDay(LocalDate from, LocalDate to, UUID prospectorId, Viewer viewer) {
        DashboardPeriod period = period(from, to, calendar.today());
        return new VisitsByDayResponse(period.from(), period.to(), queries.visitsByDay(period, scope(prospectorId, viewer)));
    }

    /** Só ADMIN (SecurityConfig); o filtro de Prospector segue a mesma regra do resumo (D-129). */
    public DenialsResponse denials(LocalDate from, LocalDate to, UUID prospectorId, Viewer viewer) {
        DashboardPeriod period = period(from, to, calendar.today());
        Map<DenialReason, Long> counts = queries.denials(calendar.startOfDay(period.from()), calendar.endOfDay(period.to()),
                scope(prospectorId, viewer));
        List<DenialsResponse.Reason> reasons = counts.entrySet().stream()
                .map(entry -> new DenialsResponse.Reason(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(DenialsResponse.Reason::count).reversed()
                        .thenComparing(DenialsResponse.Reason::reason))
                .toList();
        long total = reasons.stream().mapToLong(DenialsResponse.Reason::count).sum();
        return new DenialsResponse(period.from(), period.to(), total, reasons);
    }

    /** Só ADMIN (SecurityConfig): compara todos os Prospectores, sem o filtro de um deles (D-128). */
    public ProspectorPerformanceResponse prospectorPerformance(LocalDate from, LocalDate to) {
        DashboardPeriod period = period(from, to, calendar.today());
        return new ProspectorPerformanceResponse(period.from(), period.to(), queries.prospectorPerformance(period));
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

    /** Período anterior de mesmo tamanho, encostado no início do atual (D-128). */
    static DashboardPeriod previous(DashboardPeriod period) {
        return new DashboardPeriod(period.from().minusDays(period.days()), period.from().minusDays(1));
    }

    static DashboardPeriod period(LocalDate from, LocalDate to, LocalDate today) {
        if (!DatePeriods.validate(from, to)) {
            return new DashboardPeriod(today.minusDays(DEFAULT_DAYS - 1L), today);
        }
        DatePeriods.requireAtMost(from, to, MAX_DAYS);
        return new DashboardPeriod(from, to);
    }
}
