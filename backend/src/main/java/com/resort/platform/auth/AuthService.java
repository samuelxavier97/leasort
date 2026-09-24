package com.resort.platform.auth;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.Emails;
import com.resort.platform.users.PasswordRules;
import com.resort.platform.users.User;
import com.resort.platform.users.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String ENTITY_TYPE = "USER";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final AuditService audit;
    private final SessionInvalidator sessions;
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar isso. */
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttempts,
            AuditService audit,
            SessionInvalidator sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.sessions = sessions;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * Valida as credenciais. Toda falha gera LOGIN_FAILED na mesma transação, que não é revertida pelo
     * erro devolvido ao cliente. Usuário inativo recebe o mesmo erro genérico (D-045, D-050).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public User authenticate(String rawEmail, String password, String ip) {
        String email = Emails.normalize(rawEmail);
        Optional<User> user = users.findByEmail(email);
        UUID userId = user.map(User::getId).orElse(null);
        String entityType = userId == null ? null : ENTITY_TYPE;

        if (loginAttempts.isBlocked(email, ip)) {
            audit.recordAs(userId, AuditAction.LOGIN_FAILED, entityType, userId, Map.of("reason", "RATE_LIMITED"));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
                    "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
        }

        boolean passwordMatches = matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        String failureReason = null;
        if (user.isEmpty() || !passwordMatches) {
            failureReason = "INVALID_CREDENTIALS";
        } else if (!user.get().isActive()) {
            failureReason = "USER_INACTIVE";
        }
        if (failureReason != null) {
            loginAttempts.recordFailure(email, ip);
            audit.recordAs(userId, AuditAction.LOGIN_FAILED, entityType, userId, Map.of("reason", failureReason));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
        }

        loginAttempts.reset(email, ip);
        audit.recordAs(userId, AuditAction.LOGIN, ENTITY_TYPE, userId, null);
        return user.get();
    }

    @Transactional(readOnly = true)
    public User currentUser(UUID userId) {
        return users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Autenticação necessária."));
    }

    /** Troca a própria senha e encerra as demais sessões do usuário (D-047, D-049). */
    @Transactional
    public User changePassword(UUID userId, String currentPassword, String newPassword, String currentSessionId) {
        User user = currentUser(userId);
        PasswordRules.check(newPassword);
        if (!matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Senha atual incorreta.");
        }
        if (currentPassword.equals(newPassword)) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "A nova senha deve ser diferente da atual.");
        }
        user.changePassword(passwordEncoder.encode(newPassword), false);
        audit.recordAs(userId, AuditAction.PASSWORD_CHANGED, ENTITY_TYPE, userId, null);
        sessions.invalidateAllExcept(userId, currentSessionId);
        return user;
    }

    @Transactional
    public void recordLogout(UUID userId) {
        audit.recordAs(userId, AuditAction.LOGOUT, ENTITY_TYPE, userId, null);
    }

    private boolean matches(String rawPassword, String hash) {
        return rawPassword != null && PasswordRules.fitsBcrypt(rawPassword) && passwordEncoder.matches(rawPassword, hash);
    }
}
