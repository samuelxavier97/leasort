package com.resort.platform.leads;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.auth.Viewer;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.Cpf;
import com.resort.platform.common.Emails;
import com.resort.platform.common.PageResponse;
import com.resort.platform.leads.dto.AssignLeadsRequest;
import com.resort.platform.leads.dto.LeadRequest;
import com.resort.platform.leads.dto.LeadResponse;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.prospectors.ProspectorRepository;
import com.resort.platform.visits.VisitService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class LeadService {

    static final String ENTITY_TYPE = "LEAD";
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final LeadRepository leads;
    private final ProspectorRepository prospectors;
    private final AuditService audit;
    private final VisitService visitService;

    public LeadService(LeadRepository leads, ProspectorRepository prospectors, AuditService audit, VisitService visitService) {
        this.leads = leads;
        this.prospectors = prospectors;
        this.audit = audit;
        this.visitService = visitService;
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadResponse> list(LeadFilter filter, Pageable pageable, Viewer viewer) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name", "id"));
        return PageResponse.of(leads.findAll(specification(filter, viewer), sorted), lead -> LeadResponse.of(lead, viewer));
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID id, Viewer viewer) {
        return LeadResponse.of(findAccessible(id, viewer), viewer);
    }

    public LeadResponse create(LeadRequest request, Viewer viewer) {
        Lead lead = new Lead(request.name().trim());
        String cpf = Cpf.normalize(request.cpf());
        if (cpf != null) {
            ensureCpfAvailable(cpf, null);
            lead.setCpf(cpf);
        }
        applyContactFields(lead, request, new ArrayList<>());
        Prospector prospector = request.prospectorId() == null ? null : activeProspector(request.prospectorId());
        lead.setProspector(prospector);
        leads.save(lead);

        audit.record(AuditAction.LEAD_CREATED, ENTITY_TYPE, lead.getId(), null);
        if (prospector != null) {
            audit.record(AuditAction.LEAD_ASSIGNED, ENTITY_TYPE, lead.getId(), assignment(null, prospector.getId()));
        }
        return LeadResponse.of(lead, viewer);
    }

    public LeadResponse update(UUID id, LeadRequest request, Viewer viewer) {
        Lead lead = findAccessible(id, viewer);
        List<String> changedFields = new ArrayList<>();

        String name = request.name().trim();
        if (!name.equals(lead.getName())) {
            lead.setName(name);
            changedFields.add("name");
        }
        applyCpfChange(lead, request.cpf(), viewer, changedFields);
        applyContactFields(lead, request, changedFields);

        if (!changedFields.isEmpty()) {
            audit.record(AuditAction.LEAD_UPDATED, ENTITY_TYPE, id, Map.of("changedFields", changedFields));
        }
        return LeadResponse.of(lead, viewer);
    }

    public LeadResponse changeStatus(UUID id, LeadStatus target, Viewer viewer) {
        // Trava o Lead antes de ler o status: um descarte concorrente com um agendamento ou cancelamento
        // não pode trabalhar sobre um estado obsoleto nem deixar visita agendada órfã (D-077).
        leads.findByIdForUpdate(id);
        Lead lead = findAccessible(id, viewer);
        LeadStatus from = lead.getStatus();
        LeadStatusTransitions.check(from, target, viewer);
        if (target == LeadStatus.CANCELLED) {
            // D-040, D-063: o descarte cancela a visita agendada na mesma transação (convite: Fase 5).
            visitService.cancelScheduledForDiscard(lead);
        }
        lead.setStatus(target);
        audit.record(AuditAction.LEAD_STATUS_CHANGED, ENTITY_TYPE, id, Map.of("from", from.name(), "to", target.name()));
        return LeadResponse.of(lead, viewer);
    }

    /** Atribuição e reatribuição em lote, atômica (RN15, D-064). Não altera visitas. */
    public int assign(AssignLeadsRequest request) {
        if (new HashSet<>(request.leadIds()).size() != request.leadIds().size()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A lista de Leads tem itens repetidos.");
        }
        Prospector target = activeProspector(request.prospectorId());
        List<Lead> found = leads.findAllForUpdate(request.leadIds());
        if (found.size() != request.leadIds().size()) {
            throw leadNotFound();
        }
        int changed = 0;
        for (Lead lead : found) {
            UUID from = lead.getProspector() == null ? null : lead.getProspector().getId();
            if (target.getId().equals(from)) {
                continue;
            }
            lead.setProspector(target);
            audit.record(AuditAction.LEAD_ASSIGNED, ENTITY_TYPE, lead.getId(), assignment(from, target.getId()));
            changed++;
        }
        return changed;
    }

    /** Carteira (RN02): o PROSPECTOR só enxerga Leads atribuídos a ele; o resto é 404, sem revelar existência. */
    Lead findAccessible(UUID id, Viewer viewer) {
        Lead lead = leads.findWithProspectorById(id).orElseThrow(LeadService::leadNotFound);
        if (!isInWallet(lead, viewer)) {
            throw leadNotFound();
        }
        return lead;
    }

    /**
     * Regra de carteira do Lead (D-041): ADMIN, ou PROSPECTOR dono atual. É a mesma regra do
     * {@code GET /api/leads/{id}}; as visitas a usam para escrita e para {@code lead.accessible} (D-078).
     */
    public static boolean isInWallet(Lead lead, Viewer viewer) {
        if (viewer.isAdmin()) {
            return true;
        }
        UUID owner = lead.getProspector() == null ? null : lead.getProspector().getId();
        return viewer.isProspector() && Objects.equals(owner, viewer.prospectorId());
    }

    /**
     * D-061: {@code null} mantém; {@code ""} remove (só ADMIN). O PROSPECTOR só informa CPF em Lead sem
     * CPF. Qualquer valor enviado por ele para um Lead que já tem CPF é recusado, mesmo que igual, para
     * a resposta não servir de oráculo do CPF mascarado.
     */
    private void applyCpfChange(Lead lead, String requested, Viewer viewer, List<String> changedFields) {
        if (requested == null) {
            return;
        }
        String cpf = Cpf.normalize(requested);
        if (!viewer.isAdmin() && lead.getCpf() != null) {
            throw ApiException.conflict("CPF_CHANGE_NOT_ALLOWED", "O CPF deste Lead só pode ser alterado pelo administrador.");
        }
        if (Objects.equals(cpf, lead.getCpf())) {
            return;
        }
        if (cpf != null) {
            ensureCpfAvailable(cpf, lead.getId());
        }
        lead.setCpf(cpf);
        changedFields.add("cpf");
    }

    private void applyContactFields(Lead lead, LeadRequest request, List<String> changedFields) {
        String phone = trimToNull(request.phone());
        if (!Objects.equals(phone, lead.getPhone())) {
            lead.setPhone(phone);
            changedFields.add("phone");
        }
        String email = StringUtils.hasText(request.email()) ? Emails.normalize(request.email()) : null;
        if (!Objects.equals(email, lead.getEmail())) {
            lead.setEmail(email);
            changedFields.add("email");
        }
        if (request.birthDate() != null && request.birthDate().isBefore(MIN_BIRTH_DATE)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Data de nascimento inválida.");
        }
        if (!Objects.equals(request.birthDate(), lead.getBirthDate())) {
            lead.setBirthDate(request.birthDate());
            changedFields.add("birthDate");
        }
        String notes = trimToNull(request.notes());
        if (!Objects.equals(notes, lead.getNotes())) {
            lead.setNotes(notes);
            changedFields.add("notes");
        }
    }

    private void ensureCpfAvailable(String cpf, UUID leadId) {
        boolean taken = leadId == null ? leads.existsByCpf(cpf) : leads.existsByCpfAndIdNot(cpf, leadId);
        if (taken) {
            throw cpfAlreadyExists();
        }
    }

    private Prospector activeProspector(UUID id) {
        Prospector prospector = prospectors.findWithUserById(id)
                .orElseThrow(() -> ApiException.notFound("PROSPECTOR_NOT_FOUND", "Prospector não encontrado."));
        if (!prospector.getUser().isActive()) {
            throw ApiException.conflict("PROSPECTOR_INACTIVE", "O Prospector está inativo.");
        }
        return prospector;
    }

    private static Specification<Lead> specification(LeadFilter filter, Viewer viewer) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (viewer.isAdmin()) {
                if (filter.prospectorId() != null) {
                    predicates.add(cb.equal(root.get("prospector").get("id"), filter.prospectorId()));
                }
                if (Boolean.TRUE.equals(filter.unassigned())) {
                    predicates.add(cb.isNull(root.get("prospector")));
                }
                String cpf = Cpf.normalize(filter.cpf());
                if (cpf != null) {
                    predicates.add(cb.equal(root.get("cpf"), cpf));
                }
            } else {
                // Carteira: o PROSPECTOR nunca escapa dela, quaisquer que sejam os filtros enviados.
                predicates.add(cb.equal(root.join("prospector", JoinType.INNER).get("id"), viewer.prospectorId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (StringUtils.hasText(filter.q())) {
                String like = "%" + filter.q().trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(root.get("phone"), like, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Map<String, Object> assignment(UUID from, UUID to) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fromProspectorId", from);
        metadata.put("toProspectorId", to);
        return metadata;
    }

    static ApiException leadNotFound() {
        return ApiException.notFound("LEAD_NOT_FOUND", "Lead não encontrado.");
    }

    static ApiException cpfAlreadyExists() {
        return ApiException.conflict("CPF_ALREADY_EXISTS", "Já existe um Lead com este CPF.");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
