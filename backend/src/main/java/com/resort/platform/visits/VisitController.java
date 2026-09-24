package com.resort.platform.visits;

import com.resort.platform.auth.AuthenticatedUser;
import com.resort.platform.auth.ViewerResolver;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.PageResponse;
import com.resort.platform.visits.dto.CreateVisitRequest;
import com.resort.platform.visits.dto.RescheduleVisitRequest;
import com.resort.platform.visits.dto.UpdateVisitRequest;
import com.resort.platform.visits.dto.VisitResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;
    private final ViewerResolver viewers;

    public VisitController(VisitService visitService, ViewerResolver viewers) {
        this.visitService = visitService;
        this.viewers = viewers;
    }

    @GetMapping
    public PageResponse<VisitResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) VisitStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID prospectorId,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(required = false) String scope,
            Pageable pageable) {
        if (scope != null && !"history".equals(scope)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Escopo de lista inválido.");
        }
        VisitFilter filter = new VisitFilter(
                status, from, to, leadId, prospectorId, "desc".equalsIgnoreCase(order), scope != null);
        return visitService.list(filter, pageable, viewers.resolve(user));
    }

    @GetMapping("/{id}")
    public VisitResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return visitService.get(id, viewers.resolve(user));
    }

    @PostMapping
    public ResponseEntity<VisitResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateVisitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(visitService.create(request, viewers.resolve(user)));
    }

    @PutMapping("/{id}")
    public VisitResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVisitRequest request) {
        return visitService.update(id, request, viewers.resolve(user));
    }

    @PostMapping("/{id}/reschedule")
    public VisitResponse reschedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleVisitRequest request) {
        return visitService.reschedule(id, request.scheduledDate(), viewers.resolve(user));
    }

    @PatchMapping("/{id}/cancel")
    public VisitResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return visitService.cancel(id, viewers.resolve(user));
    }
}
