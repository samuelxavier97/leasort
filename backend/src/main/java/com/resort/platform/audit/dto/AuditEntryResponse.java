package com.resort.platform.audit.dto;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Linha da auditoria (§19, D-102): "Sistema" quando {@code user_id} é nulo (job noturno, bootstrap). */
public record AuditEntryResponse(
        UUID id,
        Instant createdAt,
        UUID userId,
        String userName,
        String action,
        String entityType,
        UUID entityId,
        JsonNode metadata,
        String ipAddress) {}
