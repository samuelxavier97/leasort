package com.resort.platform.invitations.dto;

import com.resort.platform.invitations.Invitation;
import com.resort.platform.invitations.InvitationCodeGenerator;
import com.resort.platform.invitations.InvitationStatus;
import com.resort.platform.visits.Visit;
import com.resort.platform.visits.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Convite como a API o entrega (§16.4). Sem CPF nem outros dados pessoais além do nome do Lead, que
 * quem lê a visita já vê. {@code canReissue}, {@code visit.canEdit} e {@code lead.accessible} só
 * orientam a interface (D-078).
 */
public record InvitationResponse(
        UUID id,
        String code,
        String formattedCode,
        InvitationStatus status,
        Instant expiresAt,
        Instant usedAt,
        Instant cancelledAt,
        Instant createdAt,
        VisitRef visit,
        LeadRef lead,
        Ref prospector,
        boolean canReissue) {

    public record VisitRef(UUID id, LocalDate scheduledDate, VisitStatus status, int companionsCount, boolean canEdit) {}

    public record LeadRef(UUID id, String name, boolean accessible) {}

    public record Ref(UUID id, String name) {}

    public static InvitationResponse of(Invitation invitation, boolean canWrite) {
        Visit visit = invitation.getVisit();
        boolean scheduled = visit.getStatus() == VisitStatus.SCHEDULED;
        return new InvitationResponse(
                invitation.getId(),
                invitation.getCode(),
                InvitationCodeGenerator.format(invitation.getCode()),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getUsedAt(),
                invitation.getCancelledAt(),
                invitation.getCreatedAt(),
                new VisitRef(visit.getId(), visit.getScheduledDate(), visit.getStatus(), visit.getCompanions().size(),
                        canWrite && scheduled),
                new LeadRef(visit.getLead().getId(), visit.getLead().getName(), canWrite),
                new Ref(visit.getProspector().getId(), visit.getProspector().getUser().getName()),
                canWrite && scheduled && invitation.getStatus() == InvitationStatus.ACTIVE);
    }
}
