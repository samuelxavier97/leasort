package com.resort.platform.access.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Confirmação da entrada, com os acompanhantes que vieram (D-021). */
public record RegisterRequest(@NotNull UUID invitationId, @NotNull List<@NotNull UUID> presentCompanionIds) {}
