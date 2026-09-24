package com.resort.platform.access.dto;

import com.resort.platform.access.AccessResult;
import com.resort.platform.access.DenialReason;
import java.time.Instant;
import java.util.UUID;

/** Linha de "Acessos recentes" (D-092): sem o código tentado e sem CPF. */
public record RecentAccessResponse(
        UUID id,
        Instant createdAt,
        AccessResult result,
        DenialReason denialReason,
        String leadName,
        String gate,
        String validatedBy) {}
