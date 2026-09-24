package com.resort.platform.auth;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.common.AppProperties;
import com.resort.platform.common.Emails;
import com.resort.platform.users.PasswordRules;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import com.resort.platform.users.UserRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Cria o primeiro ADMIN a partir do ambiente, só se não houver nenhum ADMIN (D-032, D-052). */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    static final String VARIABLES = "APP_BOOTSTRAP_ADMIN_EMAIL e APP_BOOTSTRAP_ADMIN_PASSWORD";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AppProperties properties;

    public BootstrapAdminInitializer(
            UserRepository users, PasswordEncoder passwordEncoder, AuditService audit, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            log.info("Já existe ADMIN cadastrado; {} são ignoradas.", VARIABLES);
            return;
        }
        AppProperties.BootstrapAdmin config = properties.bootstrapAdmin();
        String email = config == null ? null : config.email();
        String password = config == null ? null : config.password();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "Nenhum ADMIN cadastrado. Defina " + VARIABLES + " para criar o primeiro administrador.");
        }
        String normalizedEmail = Emails.normalize(email);
        if (!Emails.isValid(normalizedEmail)) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_EMAIL não é um e-mail válido.");
        }
        if (!PasswordRules.isValid(password)) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_PASSWORD deve ter no mínimo 10 caracteres e no máximo 72 bytes.");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_EMAIL já pertence a um usuário que não é ADMIN; nenhum usuário foi alterado.");
        }

        User admin = users.save(
                new User("Administrador", normalizedEmail, passwordEncoder.encode(password), Role.ADMIN, true));
        audit.recordAs(null, AuditAction.USER_CREATED, "USER", admin.getId(), Map.of("role", "ADMIN", "source", "BOOTSTRAP"));
        log.info("ADMIN inicial criado a partir de {}; troca de senha obrigatória no primeiro acesso.", VARIABLES);
    }
}
