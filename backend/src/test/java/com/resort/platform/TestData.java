package com.resort.platform;

import com.resort.platform.prospectors.Prospector;
import com.resort.platform.prospectors.ProspectorRepository;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import com.resort.platform.users.UserRepository;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Cria dados de teste únicos e já confirmados no banco. */
@TestComponent
public class TestData {

    public static final String PASSWORD = "senha-de-teste-123";

    private final UserRepository users;
    private final ProspectorRepository prospectors;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public TestData(
            UserRepository users,
            ProspectorRepository prospectors,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transaction) {
        this.users = users;
        this.prospectors = prospectors;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.local";
    }

    public static String uniqueCode() {
        return "EMP-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public User user(Role role) {
        return user(role, PASSWORD, false, true);
    }

    public User user(Role role, String password, boolean mustChangePassword, boolean active) {
        return transaction.execute(status -> {
            User user = new User("Usuário " + role, uniqueEmail(role.name().toLowerCase()),
                    passwordEncoder.encode(password), role, mustChangePassword);
            user.setActive(active);
            User saved = users.save(user);
            if (role == Role.PROSPECTOR) {
                prospectors.save(new Prospector(saved, uniqueCode(), null));
            }
            return saved;
        });
    }

    public Prospector prospectorOf(User user) {
        return prospectors.findByUserId(user.getId()).orElseThrow();
    }

    public User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }
}
