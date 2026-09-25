package com.resort.platform.audit;

import com.resort.platform.auth.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Grava auditoria na transação de quem chama (D-028). Só faz INSERT: não há entidade JPA, logo não
 * existe caminho de alteração no código. O metadata nunca deve conter CPF, senha ou código de
 * convite.
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(JdbcClient jdbc, EntityManager entityManager, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Registra a ação em nome do usuário autenticado na requisição atual. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, UUID entityId, Map<String, ?> metadata) {
        recordAs(currentUserId(), action, entityType, entityId, metadata);
    }

    /** Registra a ação em nome de um usuário explícito; {@code null} indica o sistema. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(UUID userId, AuditAction action, String entityType, UUID entityId, Map<String, ?> metadata) {
        recordFrom(userId, currentIp(), action, entityType, entityId, metadata);
    }

    /**
     * Com usuário e IP explícitos, capturados por quem chama: para trabalho fora da thread da requisição, como a
     * exportação em streaming (D-101).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFrom(UUID userId, String ip, AuditAction action, String entityType, UUID entityId, Map<String, ?> metadata) {
        // Garante que entidades novas referenciadas pela auditoria já estejam no banco.
        entityManager.flush();
        jdbc.sql("""
                        INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id, metadata, ip_address, created_at)
                        VALUES (:id, :userId, :action, :entityType, :entityId, CAST(:metadata AS jsonb), :ip, :createdAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("action", action.name())
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("metadata", metadata == null ? null : jsonMapper.writeValueAsString(metadata))
                .param("ip", ip)
                .param("createdAt", OffsetDateTime.now(clock))
                .update();
    }

    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
