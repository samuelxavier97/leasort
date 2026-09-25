package com.resort.platform.audit;

import com.resort.platform.audit.dto.AuditEntryResponse;
import com.resort.platform.common.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tela de auditoria (§11, §19): só ADMIN. */
@RestController
public class AuditQueryController {

    private final AuditQueryService auditQueryService;

    public AuditQueryController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/api/audit")
    public PageResponse<AuditEntryResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            Pageable pageable) {
        return auditQueryService.search(from, to, userId, action, entityType, entityId, pageable);
    }
}
