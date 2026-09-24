package com.resort.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword, @NotBlank @Size(max = 200) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
