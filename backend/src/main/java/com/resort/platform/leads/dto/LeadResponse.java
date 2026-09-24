package com.resort.platform.leads.dto;

import com.resort.platform.auth.Viewer;
import com.resort.platform.common.Cpf;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.prospectors.Prospector;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lead como a API o entrega. O CPF é mascarado aqui, conforme quem vê (SPEC §15, D-060): completo e
 * formatado só para o ADMIN; o PROSPECTOR recebe {@code ***.456.789-**}.
 */
public record LeadResponse(
        UUID id,
        String name,
        String cpf,
        String phone,
        String email,
        LocalDate birthDate,
        String notes,
        LeadStatus status,
        ProspectorRef prospector,
        Instant createdAt,
        Instant updatedAt) {

    public record ProspectorRef(UUID id, String name) {}

    public static LeadResponse of(Lead lead, Viewer viewer) {
        Prospector prospector = lead.getProspector();
        return new LeadResponse(
                lead.getId(),
                lead.getName(),
                viewer.isAdmin() ? Cpf.format(lead.getCpf()) : Cpf.mask(lead.getCpf()),
                lead.getPhone(),
                lead.getEmail(),
                lead.getBirthDate(),
                lead.getNotes(),
                lead.getStatus(),
                prospector == null ? null : new ProspectorRef(prospector.getId(), prospector.getUser().getName()),
                lead.getCreatedAt(),
                lead.getUpdatedAt());
    }
}
