package com.resort.platform.auth;

import com.resort.platform.users.Role;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Principal guardado na sessão. O nome é o id do usuário, para que o índice de sessões por
 * principal não dependa do e-mail (D-053).
 */
public record AuthenticatedUser(UUID id, Role role, boolean mustChangePassword)
        implements AuthenticatedPrincipal, Serializable {

    /** Única authority de quem precisa trocar a senha; não satisfaz nenhuma regra de perfil (D-055). */
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    @Override
    public String getName() {
        return id.toString();
    }

    public List<GrantedAuthority> authorities() {
        String authority = mustChangePassword ? PASSWORD_CHANGE_REQUIRED : "ROLE_" + role.name();
        return List.of(new SimpleGrantedAuthority(authority));
    }
}
