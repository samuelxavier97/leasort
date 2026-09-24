package com.resort.platform.leads.dto;

import com.resort.platform.common.ValidCpf;
import com.resort.platform.users.dto.PhoneFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Criação (ADMIN) e edição de Lead. Na edição, {@code prospectorId} é ignorado e {@code cpf} nulo
 * mantém o valor atual; {@code ""} remove, só pelo ADMIN (D-061).
 */
public record LeadRequest(
        @NotBlank @Size(max = 120) String name,
        @ValidCpf String cpf,
        @Pattern(regexp = PhoneFormat.REGEX, message = PhoneFormat.MESSAGE) String phone,
        @Email @Size(max = 160) String email,
        @PastOrPresent LocalDate birthDate,
        @Size(max = 2000) String notes,
        UUID prospectorId) {

    /** Não expõe o CPF em logs nem em mensagens. */
    @Override
    public String toString() {
        return "LeadRequest[name=***]";
    }
}
