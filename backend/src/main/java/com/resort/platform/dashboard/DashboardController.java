package com.resort.platform.dashboard;

import com.resort.platform.auth.AuthenticatedUser;
import com.resort.platform.auth.ViewerResolver;
import com.resort.platform.dashboard.dto.DenialsResponse;
import com.resort.platform.dashboard.dto.ProspectorPerformanceResponse;
import com.resort.platform.dashboard.dto.SummaryResponse;
import com.resort.platform.dashboard.dto.VisitsByDayResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard (§11, §16.7): resumo e visitas por dia para ADMIN e PROSPECTOR; negativas por motivo e desempenho
 * por Prospector só ADMIN (D-128, D-129).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ViewerResolver viewers;

    public DashboardController(DashboardService dashboardService, ViewerResolver viewers) {
        this.dashboardService = dashboardService;
        this.viewers = viewers;
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID prospectorId) {
        return dashboardService.summary(from, to, prospectorId, viewers.resolve(user));
    }

    @GetMapping("/visits-by-day")
    public VisitsByDayResponse visitsByDay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID prospectorId) {
        return dashboardService.visitsByDay(from, to, prospectorId, viewers.resolve(user));
    }

    @GetMapping("/denials")
    public DenialsResponse denials(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID prospectorId) {
        return dashboardService.denials(from, to, prospectorId, viewers.resolve(user));
    }

    @GetMapping("/prospector-performance")
    public ProspectorPerformanceResponse prospectorPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.prospectorPerformance(from, to);
    }
}
