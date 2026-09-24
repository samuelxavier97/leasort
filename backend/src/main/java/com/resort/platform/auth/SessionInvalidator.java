package com.resort.platform.auth;

import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Encerra sessões de um usuário no Spring Session JDBC (D-047). */
@Component
public class SessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionInvalidator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void invalidateAll(UUID userId) {
        invalidateAllExcept(userId, null);
    }

    public void invalidateAllExcept(UUID userId, String keepSessionId) {
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .forEach(sessions::deleteById);
    }
}
