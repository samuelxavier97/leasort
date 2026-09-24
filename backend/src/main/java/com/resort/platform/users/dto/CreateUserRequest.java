package com.resort.platform.users.dto;

import com.resort.platform.users.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @NotNull Role role,
        @Size(max = 30) String employeeCode,
        @Pattern(regexp = PhoneFormat.REGEX, message = PhoneFormat.MESSAGE) String phone) {}
