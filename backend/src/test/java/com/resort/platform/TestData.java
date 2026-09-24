package com.resort.platform;

import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.prospectors.ProspectorRepository;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import com.resort.platform.users.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Cria dados de teste únicos e já confirmados no banco. */
@TestComponent
public class TestData {

    public static final String PASSWORD = "senha-de-teste-123";

    private final UserRepository users;
    private final ProspectorRepository prospectors;
    private final LeadRepository leads;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public TestData(
            UserRepository users,
            ProspectorRepository prospectors,
            LeadRepository leads,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transaction) {
        this.users = users;
        this.prospectors = prospectors;
        this.leads = leads;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
    }

    /** E-mail único na execução (TestSequence), sempre em minúsculas. */
    public static String uniqueEmail(String prefix) {
        return TestSequence.next(prefix + "-") + "@test.local";
    }

    /** Código de funcionário único na execução (TestSequence). */
    public static String uniqueCode() {
        return TestSequence.next("EMP-");
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

    /** Lead fictício com CPF gerado, atribuído a {@code owner} (ou sem dono quando nulo). */
    public Lead lead(Prospector owner) {
        return lead(owner, FakeCpf.generate(), LeadStatus.NEW);
    }

    public Lead lead(Prospector owner, String cpf, LeadStatus status) {
        return transaction.execute(status_ -> {
            Lead lead = new Lead(TestSequence.next("Lead Fictício "));
            lead.setCpf(cpf);
            lead.setPhone("11 90000-0000");
            lead.setEmail(uniqueEmail("lead"));
            lead.setStatus(status);
            lead.setProspector(owner == null ? null : prospectors.findById(owner.getId()).orElseThrow());
            return leads.save(lead);
        });
    }

    public Lead reloadLead(Lead lead) {
        return transaction.execute(status -> {
            Lead reloaded = leads.findWithProspectorById(lead.getId()).orElseThrow();
            if (reloaded.getProspector() != null) {
                reloaded.getProspector().getId();
            }
            return reloaded;
        });
    }

    public Prospector prospectorOf(User user) {
        return prospectors.findByUserId(user.getId()).orElseThrow();
    }

    public User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }
}
