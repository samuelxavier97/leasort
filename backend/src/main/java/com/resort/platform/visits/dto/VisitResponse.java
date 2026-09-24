package com.resort.platform.visits.dto;

import com.resort.platform.auth.Viewer;
import com.resort.platform.common.Cpf;
import com.resort.platform.visits.Relationship;
import com.resort.platform.visits.Visit;
import com.resort.platform.visits.VisitCompanion;
import com.resort.platform.visits.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Visita como a API a entrega. CPF de acompanhante mascarado para quem não é ADMIN (§15, D-060).
 * {@code canEdit} só orienta a interface; o backend valida toda escrita (D-078).
 */
public record VisitResponse(
        UUID id,
        Ref lead,
        Ref prospector,
        LocalDate scheduledDate,
        VisitStatus status,
        String notes,
        String hostNotes,
        List<CompanionResponse> companions,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt,
        boolean canEdit) {

    public record Ref(UUID id, String name) {}

    public record CompanionResponse(UUID id, String name, String cpf, LocalDate birthDate, Relationship relationship) {

        static CompanionResponse of(VisitCompanion companion, Viewer viewer) {
            return new CompanionResponse(
                    companion.getId(),
                    companion.getName(),
                    viewer.isAdmin() ? Cpf.format(companion.getCpf()) : Cpf.mask(companion.getCpf()),
                    companion.getBirthDate(),
                    companion.getRelationship());
        }
    }

    public static VisitResponse of(Visit visit, Viewer viewer, boolean canWrite) {
        return new VisitResponse(
                visit.getId(),
                new Ref(visit.getLead().getId(), visit.getLead().getName()),
                new Ref(visit.getProspector().getId(), visit.getProspector().getUser().getName()),
                visit.getScheduledDate(),
                visit.getStatus(),
                visit.getNotes(),
                visit.getHostNotes(),
                visit.getCompanions().stream().map(companion -> CompanionResponse.of(companion, viewer)).toList(),
                visit.getCancelledAt(),
                visit.getCreatedAt(),
                visit.getUpdatedAt(),
                canWrite && visit.getStatus() == VisitStatus.SCHEDULED);
    }
}
