package com.resort.platform.access;

import com.resort.platform.access.dto.AccessResponse;
import com.resort.platform.access.dto.RecentAccessResponse;
import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.AppProperties;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.invitations.Invitation;
import com.resort.platform.invitations.InvitationRepository;
import com.resort.platform.invitations.InvitationStatus;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.User;
import com.resort.platform.users.UserRepository;
import com.resort.platform.visits.Visit;
import com.resort.platform.visits.VisitCompanion;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portaria (§12, RN09–RN12). {@code validate} só lê o convite e grava apenas as negativas; {@code register}
 * trava o Lead e depois o convite (ordem da D-073, D-089), revalida tudo e grava a entrada numa transação.
 * O código nunca vai para log nem para a auditoria: fica só em {@code access_records.attempted_code} (D-044).
 */
@Service
@Transactional
public class AccessService {

    static final String ENTITY_TYPE = "ACCESS_RECORD";
    static final int RECENT_LIMIT = 50;

    private final AccessRecordRepository records;
    private final InvitationRepository invitations;
    private final LeadRepository leads;
    private final UserRepository users;
    private final ValidationRateLimiter limiter;
    private final AuditService audit;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final String gate;

    public AccessService(
            AccessRecordRepository records,
            InvitationRepository invitations,
            LeadRepository leads,
            UserRepository users,
            ValidationRateLimiter limiter,
            AuditService audit,
            BusinessCalendar calendar,
            Clock clock,
            AppProperties properties) {
        this.records = records;
        this.invitations = invitations;
        this.leads = leads;
        this.users = users;
        this.limiter = limiter;
        this.audit = audit;
        this.calendar = calendar;
        this.clock = clock;
        this.gate = properties.gateName();
    }

    /** §12.2: leitura do convite, sem lock; só a negativa é gravada. */
    public AccessResponse validate(String raw, UUID userId) {
        if (!limiter.tryAcquire(userId)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_VALIDATIONS",
                    "Muitas validações em pouco tempo. Aguarde um minuto.");
        }
        String code = AccessCodes.normalize(raw);
        Invitation invitation = AccessCodes.isValid(code) ? invitations.findWithDetailsByCode(code).orElse(null) : null;
        if (invitation == null) {
            return deny(null, attempted(code), userId, DenialReason.INVALID_CODE);
        }
        DenialReason reason = check(invitation);
        if (reason != null) {
            return deny(invitation, code, userId, reason);
        }
        Visit visit = invitation.getVisit();
        List<AccessResponse.CompanionRef> companions = visit.getCompanions().stream()
                .map(c -> new AccessResponse.CompanionRef(c.getId(), c.getName(), c.getRelationship()))
                .toList();
        return AccessResponse.authorized(invitation.getId(), visit.getLead().getName(), visit.getScheduledDate(),
                visit.getProspector().getUser().getName(), companions);
    }

    /** §12.3 e D-089: Lead antes do convite, revalidação completa, entrada numa única transação. */
    public AccessResponse register(UUID invitationId, List<UUID> presentCompanionIds, UUID userId) {
        UUID leadId = invitations.findLeadIdById(invitationId).orElseThrow(AccessService::invitationNotFound);
        Lead lead = leads.findByIdForUpdate(leadId).orElseThrow(AccessService::invitationNotFound);
        Invitation invitation = invitations.findByIdForUpdate(invitationId).orElseThrow(AccessService::invitationNotFound);

        DenialReason reason = check(invitation);
        if (reason != null) {
            return deny(invitation, invitation.getCode(), userId, reason);
        }
        Visit visit = invitation.getVisit();
        Set<UUID> present = presentCompanions(visit, presentCompanionIds);

        Instant now = clock.instant();
        AccessRecord record = records.save(AccessRecord.authorized(invitation, user(userId), gate, now, present));
        invitation.markUsed(now);
        visit.complete();
        audit.record(AuditAction.ACCESS_VALIDATED, ENTITY_TYPE, record.getId(), Map.of(
                "access_record_id", record.getId(), "visit_id", visit.getId(), "companions_present", present.size()));
        if (lead.getStatus() == LeadStatus.VISIT_SCHEDULED) {
            lead.setStatus(LeadStatus.VISITED);
            audit.record(AuditAction.LEAD_STATUS_CHANGED, "LEAD", lead.getId(), Map.of(
                    "from", LeadStatus.VISIT_SCHEDULED.name(), "to", LeadStatus.VISITED.name(), "cause", "ACCESS_REGISTERED"));
        }
        return AccessResponse.registered(invitation.getId(), lead.getName(), record.getId(), now);
    }

    /** Acessos de hoje, em APP_TIMEZONE, do mais recente para o mais antigo (D-092). */
    @Transactional(readOnly = true)
    public List<RecentAccessResponse> recent() {
        return records.findRecent(calendar.startOfDay(calendar.today()), PageRequest.of(0, RECENT_LIMIT));
    }

    /** Verificações 2 a 5 da §12.2, nesta ordem; {@code null} quando o convite libera a entrada. */
    private DenialReason check(Invitation invitation) {
        if (invitation.getStatus() == InvitationStatus.CANCELLED) {
            return DenialReason.CANCELLED;
        }
        if (invitation.getStatus() == InvitationStatus.USED) {
            return DenialReason.ALREADY_USED;
        }
        LocalDate today = calendar.today();
        LocalDate date = invitation.getVisit().getScheduledDate();
        if (invitation.getStatus() == InvitationStatus.EXPIRED || today.isAfter(date)) {
            return DenialReason.EXPIRED;
        }
        if (today.isBefore(date)) {
            return DenialReason.WRONG_DATE;
        }
        return null;
    }

    /** RN12 e D-044: negativa gravada, com auditoria só com o id do registro. */
    private AccessResponse deny(Invitation invitation, String attemptedCode, UUID userId, DenialReason reason) {
        AccessRecord record = records.save(
                AccessRecord.denied(invitation, attemptedCode, user(userId), gate, reason, clock.instant()));
        audit.record(AuditAction.ACCESS_DENIED, ENTITY_TYPE, record.getId(), Map.of("access_record_id", record.getId()));
        return AccessResponse.denied(reason, invitation == null ? null : invitation.getVisit().getScheduledDate());
    }

    /** §12.3, passo 4: todos os ids presentes precisam ser acompanhantes desta visita, sem repetição. */
    private static Set<UUID> presentCompanions(Visit visit, List<UUID> requested) {
        Set<UUID> ofVisit = visit.getCompanions().stream().map(VisitCompanion::getId).collect(Collectors.toSet());
        Set<UUID> present = new HashSet<>();
        for (UUID id : requested) {
            if (!ofVisit.contains(id)) {
                throw ApiException.badRequest("COMPANION_NOT_FOUND", "Acompanhante não pertence a esta visita.");
            }
            if (!present.add(id)) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Acompanhante repetido na lista.");
            }
        }
        return present;
    }

    private User user(UUID id) {
        return users.getReferenceById(id);
    }

    /** O valor normalizado cabe na coluna {@code attempted_code} (varchar 20). */
    private static String attempted(String code) {
        return code.length() > 20 ? code.substring(0, 20) : code;
    }

    private static ApiException invitationNotFound() {
        return ApiException.notFound("INVITATION_NOT_FOUND", "Convite não encontrado.");
    }
}
