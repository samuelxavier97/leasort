package com.resort.platform.prospectors.dto;

import com.resort.platform.users.dto.PhoneFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProspectorRequest(
        @NotBlank @Size(max = 30) String employeeCode,
        @Pattern(regexp = PhoneFormat.REGEX, message = PhoneFormat.MESSAGE) String phone) {}
