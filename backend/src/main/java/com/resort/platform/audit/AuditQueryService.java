package com.resort.platform.audit;

import com.resort.platform.audit.dto.AuditEntryResponse;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.common.DatePeriods;
import com.resort.platform.common.PageResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consulta da auditoria (§19, D-102), só ADMIN: período em {@code APP_TIMEZONE} (padrão de 30 dias, máximo de
 * 366), usuário, ação, tipo e id de entidade; da mais recente para a mais antiga. A consulta não é auditada.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final String SYSTEM = "Sistema";
    /** Tipos de entidade gravados pelos services. */
    static final Set<String> ENTITY_TYPES = Set.of("USER", "PROSPECTOR", "LEAD", "VISIT", "INVITATION", "ACCESS_RECORD", "EXPORT");

    private final NamedParameterJdbcTemplate jdbc;
    private final BusinessCalendar calendar;
    private final JsonMapper jsonMapper;

    public AuditQueryService(NamedParameterJdbcTemplate jdbc, BusinessCalendar calendar, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.calendar = calendar;
        this.jsonMapper = jsonMapper;
    }

    public PageResponse<AuditEntryResponse> search(LocalDate from, LocalDate to, UUID userId, String action,
            String entityType, UUID entityId, Pageable pageable) {
        LocalDate today = calendar.today();
        if (!DatePeriods.validate(from, to)) {
            from = today.minusDays(DEFAULT_DAYS - 1L);
            to = today;
        }
        DatePeriods.requireAtMost(from, to, MAX_DAYS);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTs", calendar.startOfDay(from).atOffset(ZoneOffset.UTC))
                .addValue("toTs", calendar.endOfDay(to).atOffset(ZoneOffset.UTC));
        List<String> where = new ArrayList<>(List.of("a.created_at >= :fromTs", "a.created_at < :toTs"));
        if (userId != null) {
            where.add("a.user_id = :userId");
            params.addValue("userId", userId);
        }
        if (action != null && !action.isBlank()) {
            where.add("a.action = :action");
            params.addValue("action", action(action));
        }
        if (entityType != null && !entityType.isBlank()) {
            if (!ENTITY_TYPES.contains(entityType)) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Tipo de entidade inválido.");
            }
            where.add("a.entity_type = :entityType");
            params.addValue("entityType", entityType);
        }
        if (entityId != null) {
            where.add("a.entity_id = :entityId");
            params.addValue("entityId", entityId);
        }
        String fromWhere = " FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id WHERE " + String.join(" AND ", where);
        Long total = jdbc.queryForObject("SELECT count(*)" + fromWhere, params, Long.class);
        int size = pageable.getPageSize();
        params.addValue("limit", size).addValue("offset", pageable.getOffset());
        List<AuditEntryResponse> content = jdbc.query("""
                        SELECT a.id, a.created_at, a.user_id, u.name AS user_name, a.action, a.entity_type, a.entity_id,
                               a.metadata::text AS metadata, a.ip_address
                        """ + fromWhere + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, n) -> {
                    UUID user = rs.getObject("user_id", UUID.class);
                    String metadata = rs.getString("metadata");
                    return new AuditEntryResponse(
                            rs.getObject("id", UUID.class),
                            rs.getTimestamp("created_at").toInstant(),
                            user,
                            user == null ? SYSTEM : rs.getString("user_name"),
                            rs.getString("action"),
                            rs.getString("entity_type"),
                            rs.getObject("entity_id", UUID.class),
                            metadata == null ? null : jsonMapper.readTree(metadata),
                            rs.getString("ip_address"));
                });
        long totalElements = total == null ? 0 : total;
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, pageable.getPageNumber(), size, totalElements, totalPages);
    }

    private static String action(String action) {
        try {
            return AuditAction.valueOf(action).name();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Ação inválida.");
        }
    }
}
