package com.resort.platform.leads.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AssignLeadsRequest(
        @NotNull @Size(min = 1, max = 500) List<@NotNull UUID> leadIds, @NotNull UUID prospectorId) {}
