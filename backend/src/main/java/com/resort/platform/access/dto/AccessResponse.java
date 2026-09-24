package com.resort.platform.access.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resort.platform.access.AccessResult;
import com.resort.platform.access.DenialReason;
import com.resort.platform.visits.Relationship;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Resultado da validação ou do registro (D-088): 200 nos dois casos, com {@code result}. Liberado traz só o
 * que a §15 permite ao GATE; negado traz o motivo e, em WRONG_DATE e EXPIRED, a data da visita. Nunca o código.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessResponse(
        AccessResult result,
        DenialReason denialReason,
        UUID invitationId,
        String leadName,
        LocalDate scheduledDate,
        String prospectorName,
        List<CompanionRef> companions,
        UUID accessRecordId,
        Instant entryAt) {

    public record CompanionRef(UUID id, String name, Relationship relationship) {}

    public static AccessResponse denied(DenialReason reason, LocalDate scheduledDate) {
        boolean withDate = reason == DenialReason.WRONG_DATE || reason == DenialReason.EXPIRED;
        return new AccessResponse(AccessResult.DENIED, reason, null, null, withDate ? scheduledDate : null,
                null, null, null, null);
    }

    public static AccessResponse authorized(
            UUID invitationId, String leadName, LocalDate scheduledDate, String prospectorName, List<CompanionRef> companions) {
        return new AccessResponse(AccessResult.AUTHORIZED, null, invitationId, leadName, scheduledDate, prospectorName,
                companions, null, null);
    }

    public static AccessResponse registered(UUID invitationId, String leadName, UUID accessRecordId, Instant entryAt) {
        return new AccessResponse(AccessResult.AUTHORIZED, null, invitationId, leadName, null, null, null,
                accessRecordId, entryAt);
    }
}
