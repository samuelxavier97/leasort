package com.resort.platform.arrivals;

import com.resort.platform.arrivals.dto.ArrivalsResponse;
import com.resort.platform.arrivals.dto.VisitSheetResponse;
import com.resort.platform.auth.AuthenticatedUser;
import com.resort.platform.auth.ViewerResolver;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chegadas e ficha (§11, §16.6): ADMIN, PROSPECTOR (D-041) e HOST (só hoje). */
@RestController
public class ArrivalController {

    private final ArrivalService arrivalService;
    private final ViewerResolver viewers;

    public ArrivalController(ArrivalService arrivalService, ViewerResolver viewers) {
        this.arrivalService = arrivalService;
        this.viewers = viewers;
    }

    @GetMapping("/api/arrivals")
    public ArrivalsResponse list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return arrivalService.list(date, viewers.resolve(user));
    }

    @GetMapping("/api/visits/{id}/sheet")
    public VisitSheetResponse sheet(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return arrivalService.sheet(id, viewers.resolve(user));
    }
}
