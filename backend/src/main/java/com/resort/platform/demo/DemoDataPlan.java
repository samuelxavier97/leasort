package com.resort.platform.demo;

import com.resort.platform.access.AccessResult;
import com.resort.platform.access.DenialReason;
import com.resort.platform.invitations.InvitationCodeGenerator;
import com.resort.platform.invitations.InvitationStatus;
import com.resort.platform.leads.LeadStatus;
import com.resort.platform.users.Role;
import com.resort.platform.visits.Relationship;
import com.resort.platform.visits.VisitCancelReason;
import com.resort.platform.visits.VisitStatus;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Dados da instalação de demonstração (D-125), gerados em memória a partir de "hoje" (no fuso da
 * operação), do instante atual e de uma semente fixa: o formato é o mesmo a cada carga, relativo à data.
 * Sem CPF, com telefones de DDD 00, e-mails em example.com e nomes comuns. Todos os instantes ficam no
 * passado, e visitas, convites, acessos e Leads ficam nos estados que as regras do sistema produziriam.
 */
final class DemoDataPlan {

    static final long SEED = 20260930L;
    static final int HISTORY_DAYS = 49;
    static final int FUTURE_DAYS = 10;
    static final int VISITS_TODAY = 2;
    static final String EMAIL_DOMAIN = "@example.com";

    static final String ADMIN_EMAIL = "admin.demo" + EMAIL_DOMAIN;
    static final String PROSPECTOR_EMAIL = "prospector.demo" + EMAIL_DOMAIN;
    static final String GATE_EMAIL = "portaria.demo" + EMAIL_DOMAIN;
    static final String HOST_EMAIL = "anfitriao.demo" + EMAIL_DOMAIN;

    /** Equipe de vendas: nome, e-mail, peso no volume e taxa de visitas realizadas (desempenhos diferentes). */
    private static final List<Staff> SALES_TEAM = List.of(
            new Staff("Marcos Almeida", "marcos.almeida.demo" + EMAIL_DOMAIN, 0.18, 0.78),
            new Staff("Juliana Ribeiro", PROSPECTOR_EMAIL, 0.16, 0.72),
            new Staff("Patrícia Gomes", "patricia.gomes.demo" + EMAIL_DOMAIN, 0.14, 0.68),
            new Staff("Rafael Carvalho", "rafael.carvalho.demo" + EMAIL_DOMAIN, 0.13, 0.64),
            new Staff("Fernanda Lopes", "fernanda.lopes.demo" + EMAIL_DOMAIN, 0.12, 0.60),
            new Staff("Thiago Martins", "thiago.martins.demo" + EMAIL_DOMAIN, 0.10, 0.55),
            new Staff("Aline Barbosa", "aline.barbosa.demo" + EMAIL_DOMAIN, 0.09, 0.50),
            new Staff("Gustavo Rocha", "gustavo.rocha.demo" + EMAIL_DOMAIN, 0.08, 0.45));

    /** Todos os e-mails de usuário que a carga cria. A recarga só apaga um banco com estes e o ADMIN inicial. */
    static final Set<String> USER_EMAILS;

    static {
        Set<String> emails = new HashSet<>(List.of(ADMIN_EMAIL, GATE_EMAIL, HOST_EMAIL));
        SALES_TEAM.forEach(staff -> emails.add(staff.email()));
        USER_EMAILS = Set.copyOf(emails);
    }

    private static final List<String> FEMALE = List.of(
            "Ana", "Maria", "Juliana", "Fernanda", "Camila", "Beatriz", "Larissa", "Patrícia", "Aline", "Mariana",
            "Gabriela", "Letícia", "Vanessa", "Renata", "Carla", "Daniela", "Luciana", "Priscila", "Tatiane", "Bruna");
    private static final List<String> MALE = List.of(
            "João", "José", "Carlos", "Paulo", "Lucas", "Pedro", "Rafael", "Gabriel", "Bruno", "Felipe",
            "Marcelo", "Rodrigo", "Eduardo", "Fábio", "André", "Diego", "Leandro", "Ricardo", "Vinícius", "Gustavo");
    private static final List<String> SURNAMES = List.of(
            "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves", "Pereira", "Lima", "Gomes",
            "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes", "Soares", "Fernandes", "Vieira", "Barbosa");
    private static final List<String> VISIT_NOTES = List.of(
            "Prefere visitar pela manhã.", "Vem de outra cidade; confirmar na véspera.", "Interesse no parque aquático.",
            "Pediu informações sobre a área de lazer infantil.", "Contato indicado por um cliente.");
    private static final List<String> LEAD_NOTES = List.of(
            "Contato feito no quiosque do shopping.", "Retornar depois das 18h.", "Prefere contato por mensagem.");

    record Staff(String name, String email, double weight, double completion) {}

    record DemoUser(UUID id, String name, String email, Role role, boolean login, Instant createdAt) {}

    record DemoProspector(UUID id, UUID userId, String employeeCode, String phone, double weight, double completion, Instant createdAt) {}

    record DemoLead(UUID id, String name, String phone, String email, LocalDate birthDate, String notes, LeadStatus status,
            UUID prospectorId, Instant createdAt, Instant updatedAt) {}

    record DemoVisit(UUID id, UUID leadId, UUID prospectorId, LocalDate date, VisitStatus status, VisitCancelReason cancelReason,
            String notes, String hostNotes, Instant cancelledAt, Instant createdAt, Instant updatedAt) {}

    record DemoCompanion(UUID id, UUID visitId, String name, LocalDate birthDate, Relationship relationship, Instant createdAt) {}

    record DemoInvitation(UUID id, UUID visitId, String code, InvitationStatus status, Instant expiresAt, Instant usedAt,
            Instant cancelledAt, Instant createdAt, Instant updatedAt) {}

    record DemoAccess(UUID id, UUID invitationId, String attemptedCode, AccessResult result, DenialReason denialReason,
            Instant entryAt, Instant createdAt) {}

    record DemoPresence(UUID accessId, UUID companionId) {}

    record DemoData(List<DemoUser> users, List<DemoProspector> prospectors, List<DemoLead> leads, List<DemoVisit> visits,
            List<DemoCompanion> companions, List<DemoInvitation> invitations, List<DemoAccess> accesses,
            List<DemoPresence> presences, UUID gateUserId) {}

    private final LocalDate today;
    private final Instant now;
    private final ZoneId zone;
    private final Random random;
    private final InvitationCodeGenerator codes;
    private final Set<String> usedCodes = new HashSet<>();
    private final Set<String> usedEmails = new HashSet<>();

    private final List<DemoUser> users = new ArrayList<>();
    private final List<DemoProspector> prospectors = new ArrayList<>();
    private final List<DemoLead> leads = new ArrayList<>();
    private final List<DemoVisit> visits = new ArrayList<>();
    private final List<DemoCompanion> companions = new ArrayList<>();
    private final List<DemoInvitation> invitations = new ArrayList<>();
    private final List<DemoAccess> accesses = new ArrayList<>();
    private final List<DemoPresence> presences = new ArrayList<>();
    /** Visita final de cada Lead com visita, e o convite dela. */
    private final List<Chain> chains = new ArrayList<>();

    private record Chain(DemoVisit visit, DemoInvitation invitation, List<DemoCompanion> companions, DemoAccess entry) {}

    private DemoDataPlan(LocalDate today, Instant now, ZoneId zone, long seed, RandomGenerator codeRandom) {
        this.today = today;
        this.now = now;
        this.zone = zone;
        this.random = new Random(seed);
        this.codes = new InvitationCodeGenerator(codeRandom);
    }

    /** Gera os dados; {@code codeRandom} sorteia os códigos dos convites (na carga, um {@code SecureRandom}). */
    static DemoData generate(LocalDate today, Instant now, ZoneId zone, long seed, RandomGenerator codeRandom) {
        return new DemoDataPlan(today, now, zone, seed, codeRandom).build();
    }

    /** Visitas por dia: em média 3 de segunda a sexta e 11 no sábado e no domingo (D-125). */
    int visitsOn(LocalDate date) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return weekend ? 9 + random.nextInt(5) : 2 + random.nextInt(3);
    }

    private DemoData build() {
        Instant staffSince = at(today.minusDays(HISTORY_DAYS + 40), 9 * 60);
        DemoUser gate = user("Portaria (demonstração)", GATE_EMAIL, Role.GATE, true, staffSince);
        user("Administrador (demonstração)", ADMIN_EMAIL, Role.ADMIN, true, staffSince);
        user("Anfitrião (demonstração)", HOST_EMAIL, Role.HOST, true, staffSince);
        for (int i = 0; i < SALES_TEAM.size(); i++) {
            Staff staff = SALES_TEAM.get(i);
            DemoUser account = user(staff.name(), staff.email(), Role.PROSPECTOR, staff.email().equals(PROSPECTOR_EMAIL), staffSince);
            prospectors.add(new DemoProspector(UUID.randomUUID(), account.id(), "P%02d".formatted(i + 1), phone(), staff.weight(),
                    staff.completion(), staffSince));
        }

        for (LocalDate day = today.minusDays(HISTORY_DAYS); day.isBefore(today); day = day.plusDays(1)) {
            int count = visitsOn(day);
            for (int i = 0; i < count; i++) {
                pastVisit(day);
            }
        }
        for (int i = 0; i < VISITS_TODAY; i++) {
            upcomingVisit(today, i == 0 ? 2 : 0);
        }
        for (int ahead = 1; ahead <= FUTURE_DAYS; ahead++) {
            LocalDate day = today.plusDays(ahead);
            // Quanto mais longe, menos agendamentos já feitos.
            double filled = ahead <= 3 ? 1.0 : ahead <= 7 ? 0.7 : 0.4;
            int count = (int) Math.round(visitsOn(day) * filled);
            for (int i = 0; i < count; i++) {
                upcomingVisit(day, companionCount());
            }
        }
        leadsWithoutVisit();
        denials();
        return new DemoData(List.copyOf(users), List.copyOf(prospectors), List.copyOf(leads), List.copyOf(visits),
                List.copyOf(companions), List.copyOf(invitations), List.copyOf(accesses), List.copyOf(presences), gate.id());
    }

    private DemoUser user(String name, String email, Role role, boolean login, Instant createdAt) {
        DemoUser user = new DemoUser(UUID.randomUUID(), name, email, role, login, createdAt);
        users.add(user);
        return user;
    }

    // Visitas.

    private void pastVisit(LocalDate day) {
        DemoProspector prospector = pickProspector();
        VisitStatus status;
        VisitCancelReason reason = null;
        double roll = random.nextDouble();
        if (roll < prospector.completion()) {
            status = VisitStatus.COMPLETED;
        } else if (random.nextDouble() < 0.55) {
            status = VisitStatus.NO_SHOW;
        } else {
            status = VisitStatus.CANCELLED;
            reason = random.nextDouble() < 0.7 ? VisitCancelReason.CANCELLED_BY_USER : VisitCancelReason.LEAD_DISCARDED;
        }
        chain(day, prospector, status, reason, companionCount());
    }

    private void upcomingVisit(LocalDate day, int companionCount) {
        chain(day, pickProspector(), VisitStatus.SCHEDULED, null, companionCount);
    }

    /** Um Lead e a visita final dele; em parte dos casos, antes, uma visita remarcada (D-076). */
    private void chain(LocalDate day, DemoProspector prospector, VisitStatus status, VisitCancelReason reason, int companionCount) {
        Instant created = beforeNow(at(day.minusDays(2 + random.nextInt(13)), 9 * 60 + random.nextInt(9 * 60)));
        UUID leadId = UUID.randomUUID();
        Person person = person();
        Instant leadCreated = created.minus(Duration.ofHours(24 + random.nextInt(20 * 24)));

        if (random.nextDouble() < 0.08) {
            // Remarcação: a visita antiga fica cancelada (RESCHEDULED), com o convite cancelado; a nova nasce no mesmo instante.
            LocalDate cancelDay = maxDate(dateOf(created), day.minusDays(2 + random.nextInt(4)));
            Instant rescheduledAt = beforeNow(at(cancelDay, 9 * 60 + random.nextInt(9 * 60)));
            LocalDate oldDay = dateOf(rescheduledAt).plusDays(1 + random.nextInt(3));
            Instant oldCreated = rescheduledAt.minus(Duration.ofHours(24 + random.nextInt(8 * 24)));
            DemoVisit old = new DemoVisit(UUID.randomUUID(), leadId, prospector.id(), oldDay, VisitStatus.CANCELLED,
                    VisitCancelReason.RESCHEDULED, null, null, rescheduledAt, oldCreated, rescheduledAt);
            visits.add(old);
            invitations.add(new DemoInvitation(UUID.randomUUID(), old.id(), code(), InvitationStatus.CANCELLED, endOfDay(oldDay),
                    null, rescheduledAt, oldCreated, rescheduledAt));
            created = rescheduledAt;
            leadCreated = oldCreated.minus(Duration.ofHours(24 + random.nextInt(10 * 24)));
        }

        Instant entryAt = null;
        Instant cancelledAt = null;
        Instant updated = created;
        InvitationStatus invitationStatus = InvitationStatus.ACTIVE;
        LeadStatus leadStatus = LeadStatus.VISIT_SCHEDULED;
        switch (status) {
            case COMPLETED -> {
                entryAt = at(day, 9 * 60 + random.nextInt(450));
                updated = entryAt;
                invitationStatus = InvitationStatus.USED;
                leadStatus = LeadStatus.VISITED;
            }
            case NO_SHOW -> {
                // O job das 00:15 do dia seguinte marca a visita e expira o convite.
                updated = beforeNow(endOfDay(day).plus(Duration.ofMinutes(15)));
                invitationStatus = InvitationStatus.EXPIRED;
                leadStatus = LeadStatus.CONTACTED;
            }
            case CANCELLED -> {
                Instant latest = beforeNow(at(day, 8 * 60));
                long window = Math.max(1, Duration.between(created, latest).toMinutes());
                cancelledAt = created.plus(Duration.ofMinutes(1 + random.nextLong(window)));
                if (cancelledAt.isAfter(latest)) {
                    cancelledAt = latest;
                }
                updated = cancelledAt;
                invitationStatus = InvitationStatus.CANCELLED;
                leadStatus = reason == VisitCancelReason.LEAD_DISCARDED ? LeadStatus.CANCELLED : LeadStatus.CONTACTED;
            }
            case SCHEDULED -> {
                // Já é o padrão acima.
            }
        }

        String notes = random.nextDouble() < 0.25 ? VISIT_NOTES.get(random.nextInt(VISIT_NOTES.size())) : null;
        String hostNotes = status == VisitStatus.COMPLETED && random.nextDouble() < 0.15 ? "Visita acompanhada pela equipe da recepção." : null;
        DemoVisit visit = new DemoVisit(UUID.randomUUID(), leadId, prospector.id(), day, status, reason, notes, hostNotes,
                cancelledAt, created, updated);
        visits.add(visit);

        List<DemoCompanion> family = companionsOf(visit, person, companionCount);
        DemoInvitation invitation = new DemoInvitation(UUID.randomUUID(), visit.id(), code(), invitationStatus, endOfDay(day),
                entryAt, cancelledAt, created, updated);
        invitations.add(invitation);

        DemoAccess entry = null;
        if (entryAt != null) {
            entry = new DemoAccess(UUID.randomUUID(), invitation.id(), invitation.code(), AccessResult.AUTHORIZED, null, entryAt, entryAt);
            accesses.add(entry);
            for (DemoCompanion companion : family) {
                if (random.nextDouble() < 0.8) {
                    presences.add(new DemoPresence(entry.id(), companion.id()));
                }
            }
        }

        leads.add(new DemoLead(leadId, person.fullName(), phone(), email(person), person.birthDate(), leadNote(),
                leadStatus, prospector.id(), leadCreated, updated));
        chains.add(new Chain(visit, invitation, family, entry));
    }

    private List<DemoCompanion> companionsOf(DemoVisit visit, Person lead, int count) {
        List<DemoCompanion> family = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Relationship relationship;
            LocalDate birth;
            boolean female = random.nextBoolean();
            if (i == 0 && random.nextDouble() < 0.75) {
                relationship = Relationship.SPOUSE;
                birth = lead.birthDate().plusDays(random.nextInt(3650) - 1825);
                female = !lead.female();
            } else if (random.nextDouble() < 0.8) {
                relationship = Relationship.CHILD;
                birth = today.minusYears(2 + random.nextInt(16)).minusDays(random.nextInt(365));
            } else {
                relationship = List.of(Relationship.MOTHER, Relationship.FATHER, Relationship.SIBLING, Relationship.FRIEND)
                        .get(random.nextInt(4));
                birth = lead.birthDate().plusDays(random.nextInt(7300) - 3650);
                if (relationship == Relationship.MOTHER) {
                    female = true;
                    birth = lead.birthDate().minusYears(22 + random.nextInt(10));
                } else if (relationship == Relationship.FATHER) {
                    female = false;
                    birth = lead.birthDate().minusYears(22 + random.nextInt(10));
                }
            }
            String first = (female ? FEMALE : MALE).get(random.nextInt(FEMALE.size()));
            String surname = relationship == Relationship.FRIEND ? SURNAMES.get(random.nextInt(SURNAMES.size())) : lead.surname();
            DemoCompanion companion = new DemoCompanion(UUID.randomUUID(), visit.id(), first + " " + surname, birth, relationship,
                    visit.createdAt());
            companions.add(companion);
            family.add(companion);
        }
        return family;
    }

    private int companionCount() {
        double roll = random.nextDouble();
        return roll < 0.30 ? 0 : roll < 0.60 ? 1 : roll < 0.85 ? 2 : roll < 0.95 ? 3 : 4;
    }

    // Leads sem visita: parte da carteira de cada Prospector e alguns ainda sem Prospector.

    private void leadsWithoutVisit() {
        for (DemoProspector prospector : prospectors) {
            long withVisit = chains.stream().filter(chain -> chain.visit().prospectorId().equals(prospector.id())).count();
            long extra = Math.round(withVisit * 0.3);
            for (int i = 0; i < extra; i++) {
                LeadStatus status = random.nextDouble() < 0.6 ? LeadStatus.CONTACTED : LeadStatus.NEW;
                lead(status, prospector.id(), HISTORY_DAYS);
            }
        }
        for (int i = 0; i < 25; i++) {
            lead(LeadStatus.NEW, null, 20);
        }
    }

    private void lead(LeadStatus status, UUID prospectorId, int withinDays) {
        Person person = person();
        Instant created = beforeNow(at(today.minusDays(random.nextInt(withinDays)), 8 * 60 + random.nextInt(11 * 60)));
        leads.add(new DemoLead(UUID.randomUUID(), person.fullName(), phone(), email(person), person.birthDate(), leadNote(), status,
                prospectorId, created, created));
    }

    // Negativas na Portaria: os cinco motivos, com cancelado e expirado em número pequeno.

    private void denials() {
        List<Chain> past = chains.stream().filter(chain -> chain.visit().date().isBefore(today)).toList();
        int total = Math.max(10, (int) Math.round(past.size() * 0.15));
        int alreadyUsed = Math.round(total * 0.22f);
        int wrongDate = Math.round(total * 0.22f);
        int cancelled = Math.max(1, Math.round(total * 0.08f));
        int expired = Math.max(1, Math.round(total * 0.08f));
        int invalid = total - alreadyUsed - wrongDate - cancelled - expired;

        List<Chain> completed = past.stream().filter(chain -> chain.entry() != null).toList();
        for (int i = 0; i < alreadyUsed && !completed.isEmpty(); i++) {
            Chain chain = completed.get(random.nextInt(completed.size()));
            denied(chain.invitation(), DenialReason.ALREADY_USED, chain.entry().entryAt().plus(Duration.ofMinutes(5 + random.nextInt(85))));
        }
        for (int i = 0, tries = 0; i < wrongDate && tries < 200; tries++) {
            Chain chain = chains.get(random.nextInt(chains.size()));
            Instant at = at(chain.visit().date().minusDays(1 + random.nextInt(3)), 9 * 60 + random.nextInt(8 * 60));
            if (at.isAfter(chain.invitation().createdAt()) && at.isBefore(now) && !chain.visit().date().isBefore(today.minusDays(HISTORY_DAYS))) {
                denied(chain.invitation(), DenialReason.WRONG_DATE, at);
                i++;
            }
        }
        List<Chain> cancelledChains = past.stream().filter(chain -> chain.visit().status() == VisitStatus.CANCELLED).toList();
        for (int i = 0; i < cancelled && !cancelledChains.isEmpty(); i++) {
            Chain chain = cancelledChains.get(random.nextInt(cancelledChains.size()));
            denied(chain.invitation(), DenialReason.CANCELLED, at(chain.visit().date(), 9 * 60 + random.nextInt(3 * 60)));
        }
        List<Chain> noShows = past.stream()
                .filter(chain -> chain.visit().status() == VisitStatus.NO_SHOW && chain.visit().date().isBefore(today.minusDays(1)))
                .toList();
        for (int i = 0; i < expired && !noShows.isEmpty(); i++) {
            Chain chain = noShows.get(random.nextInt(noShows.size()));
            denied(chain.invitation(), DenialReason.EXPIRED, at(chain.visit().date().plusDays(1), 9 * 60 + random.nextInt(3 * 60)));
        }
        for (int i = 0; i < invalid; i++) {
            Instant at = at(today.minusDays(1 + random.nextInt(HISTORY_DAYS)), 9 * 60 + random.nextInt(8 * 60));
            accesses.add(new DemoAccess(UUID.randomUUID(), null, code(), AccessResult.DENIED, DenialReason.INVALID_CODE, null, at));
        }
    }

    private void denied(DemoInvitation invitation, DenialReason reason, Instant at) {
        accesses.add(new DemoAccess(UUID.randomUUID(), invitation.id(), invitation.code(), AccessResult.DENIED, reason, null, beforeNow(at)));
    }

    // Pessoas, contatos e códigos.

    private record Person(String first, String surname, boolean female, LocalDate birthDate) {
        String fullName() {
            return first + " " + surname;
        }
    }

    private Person person() {
        boolean female = random.nextBoolean();
        String first = (female ? FEMALE : MALE).get(random.nextInt(FEMALE.size()));
        String surname = SURNAMES.get(random.nextInt(SURNAMES.size()));
        LocalDate birth = today.minusYears(25 + random.nextInt(40)).minusDays(random.nextInt(365));
        return new Person(first, surname, female, birth);
    }

    /** Telefone com DDD 00, que não existe no Brasil; aceito pela validação do formulário e da API. */
    private String phone() {
        return "(00) 9%04d-%04d".formatted(random.nextInt(10_000), random.nextInt(10_000));
    }

    private String email(Person person) {
        String base = ascii(person.first() + "." + person.surname()).toLowerCase();
        String email = base + EMAIL_DOMAIN;
        for (int n = 2; !usedEmails.add(email); n++) {
            email = base + n + EMAIL_DOMAIN;
        }
        return email;
    }

    private String leadNote() {
        return random.nextDouble() < 0.15 ? LEAD_NOTES.get(random.nextInt(LEAD_NOTES.size())) : null;
    }

    private String code() {
        String code = codes.next();
        while (!usedCodes.add(code)) {
            code = codes.next();
        }
        return code;
    }

    private DemoProspector pickProspector() {
        double roll = random.nextDouble();
        for (DemoProspector prospector : prospectors) {
            roll -= prospector.weight();
            if (roll < 0) {
                return prospector;
            }
        }
        return prospectors.getLast();
    }

    // Datas no fuso da operação.

    private Instant at(LocalDate date, int minuteOfDay) {
        return date.atStartOfDay(zone).plusMinutes(minuteOfDay).toInstant();
    }

    private Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    /** Nada no futuro: um instante depois de agora vira um pouco antes de agora. */
    private Instant beforeNow(Instant instant) {
        Instant limit = now.minus(Duration.ofMinutes(10));
        return instant.isAfter(limit) ? limit : instant;
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static String ascii(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
