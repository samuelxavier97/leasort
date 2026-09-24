package com.resort.platform.prospectors;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.PageResponse;
import com.resort.platform.prospectors.dto.ProspectorResponse;
import com.resort.platform.prospectors.dto.UpdateProspectorRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ProspectorService {

    /** A §19 não tem ação própria para Prospector; usa USER_UPDATED com este entity_type (D-051). */
    static final String ENTITY_TYPE = "PROSPECTOR";

    private final ProspectorRepository prospectors;
    private final AuditService audit;

    public ProspectorService(ProspectorRepository prospectors, AuditService audit) {
        this.prospectors = prospectors;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProspectorResponse> list(Pageable pageable) {
        return PageResponse.of(
                prospectors.findAll(PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(), Sort.by("user.name", "id"))),
                ProspectorResponse::of);
    }

    @Transactional(readOnly = true)
    public ProspectorResponse get(UUID id) {
        return ProspectorResponse.of(find(id));
    }

    public ProspectorResponse update(UUID id, UpdateProspectorRequest request) {
        Prospector prospector = find(id);
        List<String> changedFields = new ArrayList<>();

        String employeeCode = request.employeeCode().trim();
        if (!employeeCode.equals(prospector.getEmployeeCode())) {
            if (prospectors.existsByEmployeeCodeAndIdNot(employeeCode, id)) {
                throw ApiException.conflict("EMPLOYEE_CODE_ALREADY_EXISTS", "Já existe um Prospector com este código.");
            }
            prospector.setEmployeeCode(employeeCode);
            changedFields.add("employeeCode");
        }
        String phone = StringUtils.hasText(request.phone()) ? request.phone().trim() : null;
        if (!Objects.equals(phone, prospector.getPhone())) {
            prospector.setPhone(phone);
            changedFields.add("phone");
        }

        if (!changedFields.isEmpty()) {
            audit.record(AuditAction.USER_UPDATED, ENTITY_TYPE, id, Map.of("changedFields", changedFields));
        }
        return ProspectorResponse.of(prospector);
    }

    private Prospector find(UUID id) {
        return prospectors.findWithUserById(id)
                .orElseThrow(() -> ApiException.notFound("PROSPECTOR_NOT_FOUND", "Prospector não encontrado."));
    }
}
