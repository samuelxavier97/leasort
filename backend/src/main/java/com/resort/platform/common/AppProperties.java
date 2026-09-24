package com.resort.platform.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Parâmetros de ambiente (SPEC §21, D-026). */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        BootstrapAdmin bootstrapAdmin,
        @NotBlank String timezone,
        @Min(0) @Max(20) int maxCompanions) {

    public record BootstrapAdmin(String email, String password) {}
}
