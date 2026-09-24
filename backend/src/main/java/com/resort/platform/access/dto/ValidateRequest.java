package com.resort.platform.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Código lido do QR ou digitado. O {@code toString} não expõe o código (regra 5). */
public record ValidateRequest(@NotBlank @Size(max = 64) String code) {

    @Override
    public String toString() {
        return "ValidateRequest[code=***]";
    }
}
