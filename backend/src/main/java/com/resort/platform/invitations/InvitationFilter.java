package com.resort.platform.invitations;

import java.util.UUID;

/** Filtros da lista. {@code prospectorId} só vale para o ADMIN; {@code descending} inverte a ordem por data da visita. */
public record InvitationFilter(
        InvitationStatus status, UUID visitId, UUID leadId, UUID prospectorId, boolean descending) {}
