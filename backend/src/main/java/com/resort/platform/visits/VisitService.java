package com.resort.platform.visits;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.AppProperties;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.common.Cpf;
import com.resort.platform.common.PageResponse;
import com.resort.platform.invitations.InvitationService;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadService;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.visits.dto.CompanionRequest;
import com.resort.platform.visits.dto.CreateVisitRequest;
import com.resort.platform.visits.dto.UpdateVisitRequest;
import com.resort.platform.visits.dto.VisitResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Visitas e acompanhantes (SPEC §6.2, §7.2, RN03–RN05, RN13). Toda operação que muda o estado da
 * visita trava a linha do Lead, o que serializa agendamento, remarcação, cancelamento e descarte.
 * O convite é criado e cancelado nestas mesmas transações (RN06, D-071, D-081).
 */
@Service
@Transactional
public class VisitService {

    static final String ENTITY_TYPE = "VISIT";
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final VisitRepository visits;
    private final LeadRepository leads;
    private final AuditService audit;
    private final InvitationService invitations;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final int maxCompanions;

    public VisitService(
            VisitRepository visits,
            LeadRepository leads,
            AuditService audit,
            InvitationService invitations,
            BusinessCalendar calendar,
            Clock clock,
            AppProperties properties) {
        this.visits = visits;
        this.leads = leads;
        this.audit = audit;
        this.invitations = invitations;
        this.calendar = calendar;
        this.clock = clock;
        this.maxCompanions = properties.maxCompanions();
    }

    @Transactional(readOnly = true)
    public PageResponse<VisitResponse> list(VisitFilter filter, Pageable pageable, Viewer viewer) {
        Sort.Direction direction = filter.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(direction, "scheduledDate").and(Sort.by(direction, "createdAt")));
        Page<Visit> page = visits.findAll(specification(filter, viewer, calendar.today()), sorted);
        Map<UUID, VisitResponse.InvitationRef> current =
                invitations.currentByVisit(page.getContent().stream().map(Visit::getId).toList());
        return PageResponse.of(page, visit -> response(visit, viewer, current.get(visit.getId())));
    }

    @Transactional(readOnly = true)
    public VisitResponse get(UUID id, Viewer viewer) {
        Visit visit = find(id);
        if (!canRead(visit, viewer)) {
            throw visitNotFound();
        }
        return response(visit, viewer);
    }

    public VisitResponse create(CreateVisitRequest request, Viewer viewer) {
        Lead lead = leads.findByIdForUpdate(request.leadId()).orElseThrow(VisitService::leadNotFound);
        if (!LeadService.isInWallet(lead, viewer)) {
            throw leadNotFound();
        }
        Prospector owner = schedulableOwner(lead);
        if (lead.getStatus() == LeadStatus.VISIT_SCHEDULED
                || visits.existsByLeadIdAndStatus(lead.getId(), VisitStatus.SCHEDULED)) {
            throw visitAlreadyScheduled();
        }
        validateDate(request.scheduledDate());
        List<CompanionRequest> companions = request.companions() == null ? List.of() : request.companions();
        validateCompanions(companions);

        Visit visit = new Visit(lead, owner, request.scheduledDate(), trimToNull(request.notes()), trimToNull(request.hostNotes()));
        for (CompanionRequest companion : companions) {
            if (companion.id() != null) {
                throw companionNotFound();
            }
            visit.addCompanion(companion.name().trim(), Cpf.normalize(companion.cpf()), companion.birthDate(), companion.relationship());
        }
        visits.save(visit);
        invitations.issue(visit);
        LeadStatus from = lead.getStatus();
        lead.setStatus(LeadStatus.VISIT_SCHEDULED);

        audit.record(AuditAction.VISIT_CREATED, ENTITY_TYPE, visit.getId(),
                Map.of("leadId", lead.getId(), "companions", visit.getCompanions().size()));
        recordLeadStatus(lead, from, LeadStatus.VISIT_SCHEDULED, "VISIT_CREATED");
        return response(visit, viewer);
    }

    public VisitResponse update(UUID id, UpdateVisitRequest request, Viewer viewer) {
        Visit visit = findForWrite(id, viewer);
        if (visit.getStatus() != VisitStatus.SCHEDULED) {
            throw ApiException.conflict("VISIT_NOT_EDITABLE", "Só é possível editar uma visita agendada.");
        }
        validateCompanions(request.companions());

        List<String> changedFields = new ArrayList<>();
        String notes = trimToNull(request.notes());
        if (!Objects.equals(notes, visit.getNotes())) {
            visit.setNotes(notes);
            changedFields.add("notes");
        }
        String hostNotes = trimToNull(request.hostNotes());
        if (!Objects.equals(hostNotes, visit.getHostNotes())) {
            visit.setHostNotes(hostNotes);
            changedFields.add("hostNotes");
        }

        Map<UUID, VisitCompanion> existing = new LinkedHashMap<>();
        visit.getCompanions().forEach(companion -> existing.put(companion.getId(), companion));
        Set<UUID> kept = new HashSet<>();
        int added = 0;
        int updated = 0;
        for (CompanionRequest item : request.companions()) {
            if (item.id() == null) {
                visit.addCompanion(item.name().trim(), Cpf.normalize(item.cpf()), item.birthDate(), item.relationship());
                added++;
                continue;
            }
            VisitCompanion companion = existing.get(item.id());
            if (companion == null) {
                throw companionNotFound();
            }
            if (!kept.add(item.id())) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Acompanhante repetido na lista.");
            }
            if (updateCompanion(companion, item, viewer)) {
                updated++;
            }
        }
        List<VisitCompanion> removed = existing.values().stream().filter(c -> !kept.contains(c.getId())).toList();
        visit.getCompanions().removeAll(removed);

        if (!changedFields.isEmpty() || added > 0 || updated > 0 || !removed.isEmpty()) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("changedFields", changedFields);
            metadata.put("companionsAdded", added);
            metadata.put("companionsUpdated", updated);
            metadata.put("companionsRemoved", removed.size());
            audit.record(AuditAction.VISIT_UPDATED, ENTITY_TYPE, visit.getId(), metadata);
        }
        return response(visit, viewer);
    }

    /** Remarcar = cancelar e agendar na mesma transação, com cópia de notas e acompanhantes (D-009, D-039, D-076). */
    public VisitResponse reschedule(UUID id, LocalDate newDate, Viewer viewer) {
        Visit old = findForWrite(id, viewer);
        Lead lead = old.getLead();
        requireScheduled(old);
        if (newDate.equals(old.getScheduledDate())) {
            throw ApiException.badRequest("SAME_DATE", "A nova data é igual à data atual da visita.");
        }
        validateDate(newDate);
        Prospector owner = schedulableOwner(lead);

        invitations.cancelActive(old, "VISIT_RESCHEDULED");
        old.cancel(clock.instant());
        // O índice parcial exige que a visita antiga deixe de ser SCHEDULED antes de inserir a nova.
        visits.saveAndFlush(old);

        Visit replacement = new Visit(lead, owner, newDate, old.getNotes(), old.getHostNotes());
        old.getCompanions().forEach(companion -> replacement.getCompanions().add(companion.copyTo(replacement)));
        visits.save(replacement);
        invitations.issue(replacement);

        audit.record(AuditAction.VISIT_RESCHEDULED, ENTITY_TYPE, old.getId(), Map.of("newVisitId", replacement.getId()));
        audit.record(AuditAction.VISIT_CREATED, ENTITY_TYPE, replacement.getId(),
                Map.of("leadId", lead.getId(), "companions", replacement.getCompanions().size(), "rescheduledFrom", old.getId()));
        return response(replacement, viewer);
    }

    public VisitResponse cancel(UUID id, Viewer viewer) {
        Visit visit = findForWrite(id, viewer);
        Lead lead = visit.getLead();
        requireScheduled(visit);

        invitations.cancelActive(visit, "VISIT_CANCELLED");
        visit.cancel(clock.instant());
        audit.record(AuditAction.VISIT_CANCELLED, ENTITY_TYPE, visit.getId(), null);
        if (lead.getStatus() == LeadStatus.VISIT_SCHEDULED) {
            lead.setStatus(LeadStatus.CONTACTED);
            recordLeadStatus(lead, LeadStatus.VISIT_SCHEDULED, LeadStatus.CONTACTED, "VISIT_CANCELLED");
        }
        return response(visit, viewer);
    }

    /**
     * Descarte do Lead (D-040, D-063, D-077): cancela a visita agendada na transação de quem chama. O
     * Lead já deve estar travado por quem chama.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void cancelScheduledForDiscard(Lead lead) {
        visits.findByLeadIdAndStatus(lead.getId(), VisitStatus.SCHEDULED).ifPresent(visit -> {
            invitations.cancelActive(visit, "LEAD_DISCARDED");
            visit.cancel(clock.instant());
            audit.record(AuditAction.VISIT_CANCELLED, ENTITY_TYPE, visit.getId(), Map.of("reason", "LEAD_DISCARDED"));
        });
    }

    /** D-041: lê quem é responsável pela visita ou dono atual do Lead. Também vale para o convite. */
    public static boolean canRead(Visit visit, Viewer viewer) {
        return viewer.isAdmin()
                || (viewer.isProspector()
                        && (visit.getProspector().getId().equals(viewer.prospectorId())
                                || LeadService.isInWallet(visit.getLead(), viewer)));
    }

    /** D-041: escreve só o dono atual do Lead ou o ADMIN. Também vale para a reemissão do convite. */
    public static boolean canWrite(Visit visit, Viewer viewer) {
        return LeadService.isInWallet(visit.getLead(), viewer);
    }

    /**
     * Trava o Lead antes de carregar a visita: quem esperou o lock lê o estado já confirmado pela
     * transação concorrente (cancelamento × descarte, remarcação × cancelamento). Sem permissão de
     * escrita, 404, mesmo para quem pode ler (D-041, D-072).
     */
    private Visit findForWrite(UUID id, Viewer viewer) {
        UUID leadId = visits.findLeadIdById(id).orElseThrow(VisitService::visitNotFound);
        leads.findByIdForUpdate(leadId);
        Visit visit = find(id);
        if (!canWrite(visit, viewer)) {
            throw visitNotFound();
        }
        return visit;
    }

    private Visit find(UUID id) {
        return visits.findWithDetailsById(id).orElseThrow(VisitService::visitNotFound);
    }

    private VisitResponse response(Visit visit, Viewer viewer) {
        return response(visit, viewer, invitations.currentByVisit(List.of(visit.getId())).get(visit.getId()));
    }

    private static VisitResponse response(Visit visit, Viewer viewer, VisitResponse.InvitationRef invitation) {
        // Escrever na visita e abrir o Lead seguem a mesma regra de carteira (D-041, D-078).
        boolean inWallet = canWrite(visit, viewer);
        return VisitResponse.of(visit, viewer, inWallet, inWallet, invitation);
    }

    /** RN01, RN03 e D-073: o Lead precisa estar ativo, atribuído e com o dono ativo. */
    private static Prospector schedulableOwner(Lead lead) {
        if (lead.getStatus() == LeadStatus.CANCELLED) {
            throw ApiException.conflict("LEAD_INACTIVE", "Lead descartado não recebe visita.");
        }
        Prospector owner = lead.getProspector();
        if (owner == null) {
            throw ApiException.conflict("LEAD_NOT_ASSIGNED", "Atribua o Lead a um Prospector antes de agendar.");
        }
        if (!owner.getUser().isActive()) {
            throw ApiException.conflict("PROSPECTOR_INACTIVE", "O Prospector do Lead está inativo; reatribua o Lead.");
        }
        return owner;
    }

    /** RN05 e D-074: de hoje até 12 meses à frente, no fuso da operação. */
    private void validateDate(LocalDate date) {
        LocalDate today = calendar.today();
        if (date.isBefore(today)) {
            throw ApiException.badRequest("SCHEDULED_DATE_IN_PAST", "A data da visita não pode estar no passado.");
        }
        if (date.isAfter(today.plusMonths(12))) {
            throw ApiException.badRequest("SCHEDULED_DATE_TOO_FAR", "A data da visita pode ser no máximo 12 meses a partir de hoje.");
        }
    }

    private void validateCompanions(List<CompanionRequest> companions) {
        if (companions.size() > maxCompanions) {
            throw ApiException.badRequest("TOO_MANY_COMPANIONS",
                    "No máximo " + maxCompanions + " acompanhantes por visita.");
        }
        for (CompanionRequest companion : companions) {
            if (companion.birthDate().isBefore(MIN_BIRTH_DATE)) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Data de nascimento do acompanhante inválida.");
            }
        }
    }

    /**
     * Mesmas regras do CPF do Lead (D-061, D-075): nulo mantém; {@code ""} remove, só ADMIN. O PROSPECTOR
     * não envia CPF para acompanhante que já tem CPF, nem igual ao gravado, para não haver oráculo.
     */
    private static boolean updateCompanion(VisitCompanion companion, CompanionRequest item, Viewer viewer) {
        boolean changed = false;
        String name = item.name().trim();
        if (!name.equals(companion.getName())) {
            companion.setName(name);
            changed = true;
        }
        if (!item.birthDate().equals(companion.getBirthDate())) {
            companion.setBirthDate(item.birthDate());
            changed = true;
        }
        if (item.relationship() != companion.getRelationship()) {
            companion.setRelationship(item.relationship());
            changed = true;
        }
        if (item.cpf() != null) {
            if (!viewer.isAdmin() && companion.getCpf() != null) {
                throw ApiException.conflict("CPF_CHANGE_NOT_ALLOWED", "O CPF deste acompanhante só pode ser alterado pelo administrador.");
            }
            String cpf = Cpf.normalize(item.cpf());
            if (!Objects.equals(cpf, companion.getCpf())) {
                companion.setCpf(cpf);
                changed = true;
            }
        }
        return changed;
    }

    private void requireScheduled(Visit visit) {
        if (visit.getStatus() != VisitStatus.SCHEDULED) {
            throw ApiException.conflict("INVALID_VISIT_TRANSITION", "A visita não está agendada.");
        }
    }

    private void recordLeadStatus(Lead lead, LeadStatus from, LeadStatus to, String cause) {
        audit.record(AuditAction.LEAD_STATUS_CHANGED, "LEAD", lead.getId(),
                Map.of("from", from.name(), "to", to.name(), "cause", cause));
    }

    private static Specification<Visit> specification(VisitFilter filter, Viewer viewer, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (viewer.isAdmin()) {
                if (filter.prospectorId() != null) {
                    predicates.add(cb.equal(root.get("prospector").get("id"), filter.prospectorId()));
                }
            } else {
                Join<Object, Object> lead = root.join("lead", JoinType.INNER);
                predicates.add(cb.or(
                        cb.equal(root.get("prospector").get("id"), viewer.prospectorId()),
                        cb.equal(lead.join("prospector", JoinType.LEFT).get("id"), viewer.prospectorId())));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), filter.to()));
            }
            if (filter.leadId() != null) {
                predicates.add(cb.equal(root.get("lead").get("id"), filter.leadId()));
            }
            if (filter.history()) {
                // Tudo o que não está na Agenda: NOT (SCHEDULED e data >= hoje em APP_TIMEZONE).
                predicates.add(cb.or(
                        cb.notEqual(root.get("status"), VisitStatus.SCHEDULED),
                        cb.lessThan(root.get("scheduledDate"), today)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static ApiException visitNotFound() {
        return ApiException.notFound("VISIT_NOT_FOUND", "Visita não encontrada.");
    }

    private static ApiException leadNotFound() {
        return ApiException.notFound("LEAD_NOT_FOUND", "Lead não encontrado.");
    }

    private static ApiException visitAlreadyScheduled() {
        return ApiException.conflict("VISIT_ALREADY_SCHEDULED", "O Lead já tem uma visita agendada.");
    }

    private static ApiException companionNotFound() {
        return ApiException.badRequest("COMPANION_NOT_FOUND", "Acompanhante não pertence a esta visita.");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
