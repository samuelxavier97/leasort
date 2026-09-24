package com.resort.platform.invitations;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.common.PageResponse;
import com.resort.platform.invitations.dto.InvitationResponse;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.visits.Visit;
import com.resort.platform.visits.VisitService;
import com.resort.platform.visits.VisitStatus;
import com.resort.platform.visits.dto.VisitResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convites (§8.6, §13, RN06–RN08). O convite nasce e é cancelado dentro das transações da visita
 * ({@link #issue} e {@link #cancelActive}, D-071, D-081); a reemissão trava o Lead antes de ler o estado,
 * como as escritas de visita (D-073, D-084). O código nunca vai para log nem para a auditoria (regra 5).
 */
@Service
@Transactional
public class InvitationService {

    static final String ENTITY_TYPE = "INVITATION";
    static final int MAX_CODE_ATTEMPTS = 5;

    private final InvitationRepository invitations;
    private final LeadRepository leads;
    private final InvitationCodeGenerator codes;
    private final AuditService audit;
    private final BusinessCalendar calendar;
    private final Clock clock;

    public InvitationService(
            InvitationRepository invitations,
            LeadRepository leads,
            InvitationCodeGenerator codes,
            AuditService audit,
            BusinessCalendar calendar,
            Clock clock) {
        this.invitations = invitations;
        this.leads = leads;
        this.codes = codes;
        this.audit = audit;
        this.calendar = calendar;
        this.clock = clock;
    }

    /** RN06: convite ACTIVE da visita recém-criada, na transação de quem chama. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(Visit visit) {
        Invitation invitation = invitations.save(
                new Invitation(visit, uniqueCode(), calendar.endOfDay(visit.getScheduledDate())));
        audit.record(AuditAction.INVITATION_CREATED, ENTITY_TYPE, invitation.getId(), Map.of("visitId", visit.getId()));
    }

    /**
     * Cancela o convite ACTIVE da visita, na transação de quem chama. Visita sem convite ativo (criada
     * antes da Fase 5) não é erro (D-071, D-081).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelActive(Visit visit, String reason) {
        invitations.findByVisitIdAndStatus(visit.getId(), InvitationStatus.ACTIVE).ifPresent(invitation -> {
            invitation.cancel(clock.instant());
            invitations.saveAndFlush(invitation);
            audit.record(AuditAction.INVITATION_CANCELLED, ENTITY_TYPE, invitation.getId(),
                    Map.of("visitId", visit.getId(), "reason", reason));
        });
    }

    /** Convite atual de cada visita (o ACTIVE ou, sem ele, o mais recente), sem o código (D-086). */
    @Transactional(readOnly = true)
    public Map<UUID, VisitResponse.InvitationRef> currentByVisit(Collection<UUID> visitIds) {
        Map<UUID, Invitation> current = new HashMap<>();
        if (visitIds.isEmpty()) {
            return Map.of();
        }
        Comparator<Invitation> preference = Comparator
                .comparing((Invitation i) -> i.getStatus() == InvitationStatus.ACTIVE)
                .thenComparing(Invitation::getCreatedAt);
        for (Invitation invitation : invitations.findByVisitIdIn(visitIds)) {
            current.merge(invitation.getVisit().getId(), invitation,
                    (a, b) -> preference.compare(a, b) >= 0 ? a : b);
        }
        Map<UUID, VisitResponse.InvitationRef> refs = new HashMap<>();
        current.forEach((visitId, invitation) ->
                refs.put(visitId, new VisitResponse.InvitationRef(invitation.getId(), invitation.getStatus())));
        return refs;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> list(InvitationFilter filter, Pageable pageable, Viewer viewer) {
        Sort.Direction direction = filter.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(direction, "visit.scheduledDate").and(Sort.by(direction, "createdAt")));
        return PageResponse.of(invitations.findAll(specification(filter, viewer), sorted),
                invitation -> InvitationResponse.of(invitation, VisitService.canWrite(invitation.getVisit(), viewer)));
    }

    @Transactional(readOnly = true)
    public InvitationResponse get(UUID id, Viewer viewer) {
        Invitation invitation = findReadable(id, viewer);
        return InvitationResponse.of(invitation, VisitService.canWrite(invitation.getVisit(), viewer));
    }

    /** RN08: QR só de convite ACTIVE (D-083). */
    @Transactional(readOnly = true)
    public byte[] qrCode(UUID id, Viewer viewer) {
        Invitation invitation = findReadable(id, viewer);
        if (invitation.getStatus() != InvitationStatus.ACTIVE) {
            throw notActive();
        }
        return QrCodes.png(invitation.getCode());
    }

    /**
     * Reemissão (§7.2, §13, D-084): o convite atual vira CANCELLED e um novo nasce ACTIVE, na mesma
     * transação, com o mesmo {@code expires_at}. Trava o Lead antes de ler o estado; sem escrita, 404.
     */
    public InvitationResponse reissue(UUID id, Viewer viewer) {
        UUID leadId = invitations.findLeadIdById(id).orElseThrow(InvitationService::notFound);
        leads.findByIdForUpdate(leadId);
        Invitation current = invitations.findWithDetailsById(id).orElseThrow(InvitationService::notFound);
        Visit visit = current.getVisit();
        if (!VisitService.canWrite(visit, viewer)) {
            throw notFound();
        }
        if (current.getStatus() != InvitationStatus.ACTIVE || visit.getStatus() != VisitStatus.SCHEDULED) {
            throw notActive();
        }

        current.cancel(clock.instant());
        // O índice parcial exige que o convite atual deixe de ser ACTIVE antes de inserir o novo.
        invitations.saveAndFlush(current);
        Invitation replacement = invitations.save(new Invitation(visit, uniqueCode(), current.getExpiresAt()));

        audit.record(AuditAction.INVITATION_REISSUED, ENTITY_TYPE, current.getId(),
                Map.of("newInvitationId", replacement.getId()));
        audit.record(AuditAction.INVITATION_CREATED, ENTITY_TYPE, replacement.getId(),
                Map.of("visitId", visit.getId(), "reissuedFrom", current.getId()));
        return InvitationResponse.of(replacement, true);
    }

    /**
     * RN07 e D-082: até {@value #MAX_CODE_ATTEMPTS} tentativas, cada uma checando se o código já existe,
     * mesmo em convite cancelado; códigos nunca são reaproveitados. O índice único é o último seguro.
     */
    private String uniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codes.next();
            if (!invitations.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de convite único.");
    }

    private Invitation findReadable(UUID id, Viewer viewer) {
        Invitation invitation = invitations.findWithDetailsById(id).orElseThrow(InvitationService::notFound);
        if (!VisitService.canRead(invitation.getVisit(), viewer)) {
            throw notFound();
        }
        return invitation;
    }

    /** D-041 e D-072: o PROSPECTOR vê os convites das visitas que pode ler. */
    private static Specification<Invitation> specification(InvitationFilter filter, Viewer viewer) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Object, Object> visit = root.join("visit", JoinType.INNER);
            if (viewer.isAdmin()) {
                if (filter.prospectorId() != null) {
                    predicates.add(cb.equal(visit.get("prospector").get("id"), filter.prospectorId()));
                }
            } else {
                Join<Object, Object> lead = visit.join("lead", JoinType.INNER);
                predicates.add(cb.or(
                        cb.equal(visit.get("prospector").get("id"), viewer.prospectorId()),
                        cb.equal(lead.join("prospector", JoinType.LEFT).get("id"), viewer.prospectorId())));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.visitId() != null) {
                predicates.add(cb.equal(visit.get("id"), filter.visitId()));
            }
            if (filter.leadId() != null) {
                predicates.add(cb.equal(visit.get("lead").get("id"), filter.leadId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static ApiException notFound() {
        return ApiException.notFound("INVITATION_NOT_FOUND", "Convite não encontrado.");
    }

    private static ApiException notActive() {
        return ApiException.conflict("INVITATION_NOT_ACTIVE", "O convite não está ativo.");
    }
}
