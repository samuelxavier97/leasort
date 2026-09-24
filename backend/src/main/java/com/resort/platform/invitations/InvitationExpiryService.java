package com.resort.platform.invitations;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.visits.Visit;
import com.resort.platform.visits.VisitStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expiração de um convite pelo job noturno (§20, D-091), numa transação própria. Trava o Lead antes de reler
 * o convite (ordem da D-073), então não conflita com registro na Portaria, cancelamento, remarcação ou
 * reemissão simultâneos. Auditoria com {@code user_id} nulo e sem o código.
 */
@Service
public class InvitationExpiryService {

    private final InvitationRepository invitations;
    private final LeadRepository leads;
    private final AuditService audit;
    private final Clock clock;

    public InvitationExpiryService(InvitationRepository invitations, LeadRepository leads, AuditService audit, Clock clock) {
        this.invitations = invitations;
        this.leads = leads;
        this.audit = audit;
        this.clock = clock;
    }

    /** Ids dos convites ACTIVE já vencidos, inclusive de noites em que o job não rodou. */
    @Transactional(readOnly = true)
    public List<UUID> dueIds() {
        return invitations.findActiveIdsExpiredAt(clock.instant());
    }

    /** Expira o convite se ele ainda estiver ACTIVE e vencido; devolve se algo mudou (idempotente). */
    @Transactional
    public boolean expire(UUID invitationId) {
        UUID leadId = invitations.findLeadIdById(invitationId).orElse(null);
        if (leadId == null) {
            return false;
        }
        Lead lead = leads.findByIdForUpdate(leadId).orElseThrow();
        Invitation invitation = invitations.findByIdForUpdate(invitationId).orElseThrow();
        if (invitation.getStatus() != InvitationStatus.ACTIVE || invitation.getExpiresAt().isAfter(clock.instant())) {
            return false;
        }
        Visit visit = invitation.getVisit();
        invitation.expire();
        audit.recordAs(null, AuditAction.INVITATION_EXPIRED, InvitationService.ENTITY_TYPE, invitation.getId(),
                Map.of("visitId", visit.getId()));
        if (visit.getStatus() == VisitStatus.SCHEDULED) {
            visit.markNoShow();
            audit.recordAs(null, AuditAction.VISIT_NO_SHOW, "VISIT", visit.getId(), Map.of("leadId", lead.getId()));
        }
        if (lead.getStatus() == LeadStatus.VISIT_SCHEDULED) {
            lead.setStatus(LeadStatus.CONTACTED);
            audit.recordAs(null, AuditAction.LEAD_STATUS_CHANGED, "LEAD", lead.getId(), Map.of(
                    "from", LeadStatus.VISIT_SCHEDULED.name(), "to", LeadStatus.CONTACTED.name(), "cause", "VISIT_NO_SHOW"));
        }
        return true;
    }
}
