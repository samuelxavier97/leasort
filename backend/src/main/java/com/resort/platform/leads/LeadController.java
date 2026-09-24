package com.resort.platform.leads;

import com.resort.platform.auth.AuthenticatedUser;
import com.resort.platform.auth.ViewerResolver;
import com.resort.platform.common.PageResponse;
import com.resort.platform.leads.dto.AssignLeadsRequest;
import com.resort.platform.leads.dto.ChangeLeadStatusRequest;
import com.resort.platform.leads.dto.LeadRequest;
import com.resort.platform.leads.dto.LeadResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;
    private final ViewerResolver viewers;

    public LeadController(LeadService leadService, ViewerResolver viewers) {
        this.leadService = leadService;
        this.viewers = viewers;
    }

    @GetMapping
    public PageResponse<LeadResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID prospectorId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) String cpf,
            Pageable pageable) {
        return leadService.list(new LeadFilter(status, q, prospectorId, unassigned, cpf), pageable, viewers.resolve(user));
    }

    @GetMapping("/{id}")
    public LeadResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return leadService.get(id, viewers.resolve(user));
    }

    @PostMapping
    public ResponseEntity<LeadResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody LeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leadService.create(request, viewers.resolve(user)));
    }

    @PutMapping("/{id}")
    public LeadResponse update(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody LeadRequest request) {
        return leadService.update(id, request, viewers.resolve(user));
    }

    @PatchMapping("/{id}/status")
    public LeadResponse changeStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeLeadStatusRequest request) {
        return leadService.changeStatus(id, request.status(), viewers.resolve(user));
    }

    @PatchMapping("/assign")
    public Map<String, Integer> assign(@Valid @RequestBody AssignLeadsRequest request) {
        return Map.of("assigned", leadService.assign(request));
    }
}
