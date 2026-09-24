package com.resort.platform.visits.dto;

import com.resort.platform.common.ValidCpf;
import com.resort.platform.visits.Relationship;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Acompanhante no agendamento ou na edição. {@code id} identifica um acompanhante existente desta
 * visita; sem {@code id}, é um novo. {@code cpf} nulo mantém o atual; {@code ""} remove (só ADMIN).
 */
public record CompanionRequest(
        UUID id,
        @NotBlank @Size(max = 120) String name,
        @ValidCpf String cpf,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull Relationship relationship) {

    @Override
    public String toString() {
        return "CompanionRequest[id=" + id + "]";
    }
}
