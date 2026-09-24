package com.resort.platform.leads.dto;

import com.resort.platform.leads.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeLeadStatusRequest(@NotNull LeadStatus status) {}
