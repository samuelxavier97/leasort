package com.resort.platform.leads;

import java.util.UUID;

/** Filtros da lista (D-066). {@code prospectorId}, {@code unassigned} e {@code cpf} só valem para o ADMIN. */
public record LeadFilter(LeadStatus status, String q, UUID prospectorId, Boolean unassigned, String cpf) {}
