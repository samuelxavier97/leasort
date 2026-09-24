package com.resort.platform.invitations;

import com.resort.platform.auth.AuthenticatedUser;
import com.resort.platform.auth.ViewerResolver;
import com.resort.platform.common.PageResponse;
import com.resort.platform.invitations.dto.InvitationResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Convites (§11). Não há criação nem cancelamento isolado: o convite segue a visita (D-010). */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;
    private final ViewerResolver viewers;

    public InvitationController(InvitationService invitationService, ViewerResolver viewers) {
        this.invitationService = invitationService;
        this.viewers = viewers;
    }

    @GetMapping
    public PageResponse<InvitationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) InvitationStatus status,
            @RequestParam(required = false) UUID visitId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) UUID prospectorId,
            @RequestParam(defaultValue = "asc") String order,
            Pageable pageable) {
        InvitationFilter filter = new InvitationFilter(status, visitId, leadId, prospectorId, "desc".equalsIgnoreCase(order));
        return invitationService.list(filter, pageable, viewers.resolve(user));
    }

    @GetMapping("/{id}")
    public InvitationResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return invitationService.get(id, viewers.resolve(user));
    }

    @GetMapping("/{id}/qr-code")
    public ResponseEntity<byte[]> qrCode(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(invitationService.qrCode(id, viewers.resolve(user)));
    }

    @PostMapping("/{id}/reissue")
    public InvitationResponse reissue(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return invitationService.reissue(id, viewers.resolve(user));
    }
}
