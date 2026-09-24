package com.resort.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 160) String email, @NotBlank @Size(max = 200) String password) {

    /** Evita que a senha apareça em logs ou mensagens por meio do toString do record. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
