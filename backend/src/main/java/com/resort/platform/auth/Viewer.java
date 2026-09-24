package com.resort.platform.auth;

import com.resort.platform.users.Role;
import java.util.UUID;

/** Quem está fazendo a requisição, com o Prospector já resolvido para verificar a carteira (RN02). */
public record Viewer(UUID userId, Role role, UUID prospectorId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isProspector() {
        return role == Role.PROSPECTOR;
    }
}
