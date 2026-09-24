package com.resort.platform.access;

import com.resort.platform.access.dto.AccessResponse;
import com.resort.platform.access.dto.RecentAccessResponse;
import com.resort.platform.access.dto.RegisterRequest;
import com.resort.platform.access.dto.ValidateRequest;
import com.resort.platform.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portaria (§11, §12). Validar e registrar: só GATE; acessos recentes: GATE e ADMIN. */
@RestController
@RequestMapping("/api/access")
public class AccessController {

    private final AccessService accessService;

    public AccessController(AccessService accessService) {
        this.accessService = accessService;
    }

    @PostMapping("/validate")
    public AccessResponse validate(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ValidateRequest request) {
        return accessService.validate(request.code(), user.id());
    }

    @PostMapping("/register")
    public AccessResponse register(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody RegisterRequest request) {
        return accessService.register(request.invitationId(), request.presentCompanionIds(), user.id());
    }

    @GetMapping("/recent")
    public List<RecentAccessResponse> recent() {
        return accessService.recent();
    }
}
