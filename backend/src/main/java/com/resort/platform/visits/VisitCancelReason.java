package com.resort.platform.visits;

/** Por que a visita foi cancelada (D-098). Só {@code RESCHEDULED} não conta como cancelamento. */
public enum VisitCancelReason {
    RESCHEDULED, CANCELLED_BY_USER, LEAD_DISCARDED
}
