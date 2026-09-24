package com.resort.platform.leads;

import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Transições manuais do Lead (SPEC §7.2, D-007, D-008, D-040, D-063). VISIT_SCHEDULED e VISITED são
 * sempre automáticos; qualquer par não listado é rejeitado com 409.
 */
final class LeadStatusTransitions {

    private static final Set<LeadStatus> DISCARDABLE =
            EnumSet.of(LeadStatus.NEW, LeadStatus.CONTACTED, LeadStatus.VISIT_SCHEDULED, LeadStatus.VISITED);

    private LeadStatusTransitions() {}

    static void check(LeadStatus from, LeadStatus to, Viewer viewer) {
        boolean contact = from == LeadStatus.NEW && to == LeadStatus.CONTACTED;
        boolean discard = to == LeadStatus.CANCELLED && DISCARDABLE.contains(from);
        boolean reactivate = from == LeadStatus.CANCELLED && to == LeadStatus.NEW;

        if (reactivate && !viewer.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Só o administrador reativa Leads.");
        }
        if (!contact && !discard && !reactivate) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Transição de status não permitida: " + from + " → " + to + ".");
        }
    }
}
