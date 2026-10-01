package com.resort.platform.demo;

import com.resort.platform.demo.DemoDataPlan.DemoData;
import com.resort.platform.demo.DemoDataPlan.DemoUser;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Grava a carga de demonstração (D-125) numa transação só: ou tudo, ou nada. A trava 2 roda dentro da
 * mesma transação, com as tabelas travadas, e recusa um banco com qualquer dado além do ADMIN inicial;
 * duas cargas ao mesmo tempo, ou uma segunda carga, são recusadas. Nenhuma senha nem código de
 * convite vai para a saída.
 */
final class DemoLoader {

    /** Tabelas de negócio que precisam estar vazias antes da carga. A auditoria e as sessões não entram. */
    static final List<String> BUSINESS_TABLES = List.of(
            "prospectors", "leads", "visits", "visit_companions", "invitations", "access_records", "access_record_companions");

    /** Recusa de uma trava: nada foi gravado nem apagado. */
    static final class Refused extends Exception {
        Refused(String message) {
            super(message);
        }
    }

    /** Senhas dos usuários da demonstração que entram no sistema, informadas no momento da carga. */
    record Passwords(String admin, String prospector, String gate, String host) {
        String of(String email) {
            return switch (email) {
                case DemoDataPlan.ADMIN_EMAIL -> admin;
                case DemoDataPlan.PROSPECTOR_EMAIL -> prospector;
                case DemoDataPlan.GATE_EMAIL -> gate;
                case DemoDataPlan.HOST_EMAIL -> host;
                default -> throw new IllegalArgumentException("sem senha informada para este usuário");
            };
        }

        @Override
        public String toString() {
            return "Passwords[***]";
        }
    }

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /** Carrega a demonstração e devolve os totais gravados. */
    Map<String, Integer> load(Connection connection, Clock clock, ZoneId zone, String gate, Passwords passwords, String adminName)
            throws SQLException, Refused {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            lockAndCheckEmpty(connection);
            Instant now = clock.instant();
            DemoData data = DemoDataPlan.generate(LocalDate.ofInstant(now, zone), now, zone, DemoDataPlan.SEED, secureRandom, adminName);
            write(connection, data, gate, passwords);
            connection.commit();
            return totals(data);
        } catch (SQLException | Refused | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Proteção da recarga (D-125): recusa se algum usuário não for da demonstração nem o ADMIN inicial
     * ({@code bootstrapEmail}). Só lê. Devolve quantos usuários conferiu.
     */
    int checkReset(Connection connection, String bootstrapEmail) throws SQLException, Refused {
        int total = 0;
        int others = 0;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT email, role FROM users")) {
            while (rows.next()) {
                total++;
                String email = rows.getString("email");
                boolean bootstrap = email.equalsIgnoreCase(bootstrapEmail) && "ADMIN".equals(rows.getString("role"));
                if (!bootstrap && !DemoDataPlan.USER_EMAILS.contains(email)) {
                    others++;
                }
            }
        }
        if (others > 0) {
            // Só a contagem: os e-mails de usuários de verdade não vão para a saída.
            throw new Refused(others + " usuário(s) não são da demonstração nem o ADMIN inicial. Este banco não parece "
                    + "uma instalação de demonstração; nada foi apagado.");
        }
        return total;
    }

    private void lockAndCheckEmpty(Connection connection) throws SQLException, Refused {
        try (Statement statement = connection.createStatement()) {
            statement.execute("LOCK TABLE users, " + String.join(", ", BUSINESS_TABLES) + " IN SHARE ROW EXCLUSIVE MODE");
            try (ResultSet rows = statement.executeQuery("SELECT role FROM users")) {
                List<String> roles = new ArrayList<>();
                while (rows.next()) {
                    roles.add(rows.getString(1));
                }
                if (roles.isEmpty()) {
                    throw new Refused("O banco não tem o ADMIN inicial. Suba a pilha primeiro: o backend cria o ADMIN inicial na subida.");
                }
                if (!roles.equals(List.of("ADMIN"))) {
                    throw new Refused("O banco já tem " + roles.size() + " usuários; a carga só roda num banco com o ADMIN inicial e nada mais.");
                }
            }
            for (String table : BUSINESS_TABLES) {
                try (ResultSet rows = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM " + table + ")")) {
                    rows.next();
                    if (rows.getBoolean(1)) {
                        throw new Refused("O banco já tem dados em " + table + "; a carga só roda num banco com o ADMIN inicial e nada mais.");
                    }
                }
            }
        }
    }

    private void write(Connection connection, DemoData data, String gate, Passwords passwords) throws SQLException {
        try (PreparedStatement users = connection.prepareStatement("""
                        INSERT INTO users (id, name, email, password_hash, role, active, must_change_password, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, true, false, ?, ?)""");
                PreparedStatement audit = connection.prepareStatement("""
                        INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id, metadata, created_at)
                        VALUES (?, NULL, 'USER_CREATED', 'USER', ?, CAST(? AS jsonb), ?)""")) {
            for (DemoUser user : data.users()) {
                // Quem não entra no sistema fica com uma senha aleatória descartada.
                String password = user.login() ? passwords.of(user.email()) : HexFormat.of().formatHex(secureRandom.generateSeed(24));
                users.setObject(1, user.id());
                users.setString(2, user.name());
                users.setString(3, user.email());
                users.setString(4, passwordEncoder.encode(password));
                users.setString(5, user.role().name());
                users.setTimestamp(6, ts(user.createdAt()));
                users.setTimestamp(7, ts(user.createdAt()));
                users.addBatch();
                audit.setObject(1, UUID.randomUUID());
                audit.setObject(2, user.id());
                audit.setString(3, "{\"role\": \"" + user.role().name() + "\", \"source\": \"DEMO\"}");
                audit.setTimestamp(4, ts(user.createdAt()));
                audit.addBatch();
            }
            users.executeBatch();
            audit.executeBatch();
        }
        batch(connection, "INSERT INTO prospectors (id, user_id, employee_code, phone, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                data.prospectors(), (s, p) -> {
                    s.setObject(1, p.id());
                    s.setObject(2, p.userId());
                    s.setString(3, p.employeeCode());
                    s.setString(4, p.phone());
                    s.setTimestamp(5, ts(p.createdAt()));
                    s.setTimestamp(6, ts(p.createdAt()));
                });
        batch(connection, """
                        INSERT INTO leads (id, name, cpf, phone, email, birth_date, notes, status, prospector_id, created_at, updated_at)
                        VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)""",
                data.leads(), (s, l) -> {
                    s.setObject(1, l.id());
                    s.setString(2, l.name());
                    s.setString(3, l.phone());
                    s.setString(4, l.email());
                    s.setDate(5, Date.valueOf(l.birthDate()));
                    s.setString(6, l.notes());
                    s.setString(7, l.status().name());
                    s.setObject(8, l.prospectorId());
                    s.setTimestamp(9, ts(l.createdAt()));
                    s.setTimestamp(10, ts(l.updatedAt()));
                });
        batch(connection, """
                        INSERT INTO visits (id, lead_id, prospector_id, scheduled_date, status, cancel_reason, notes, host_notes,
                                            cancelled_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                data.visits(), (s, v) -> {
                    s.setObject(1, v.id());
                    s.setObject(2, v.leadId());
                    s.setObject(3, v.prospectorId());
                    s.setDate(4, Date.valueOf(v.date()));
                    s.setString(5, v.status().name());
                    s.setString(6, v.cancelReason() == null ? null : v.cancelReason().name());
                    s.setString(7, v.notes());
                    s.setString(8, v.hostNotes());
                    s.setTimestamp(9, ts(v.cancelledAt()));
                    s.setTimestamp(10, ts(v.createdAt()));
                    s.setTimestamp(11, ts(v.updatedAt()));
                });
        batch(connection, """
                        INSERT INTO visit_companions (id, visit_id, name, cpf, birth_date, relationship, created_at, updated_at)
                        VALUES (?, ?, ?, NULL, ?, ?, ?, ?)""",
                data.companions(), (s, c) -> {
                    s.setObject(1, c.id());
                    s.setObject(2, c.visitId());
                    s.setString(3, c.name());
                    s.setDate(4, Date.valueOf(c.birthDate()));
                    s.setString(5, c.relationship().name());
                    s.setTimestamp(6, ts(c.createdAt()));
                    s.setTimestamp(7, ts(c.createdAt()));
                });
        batch(connection, """
                        INSERT INTO invitations (id, visit_id, code, status, expires_at, used_at, cancelled_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                data.invitations(), (s, i) -> {
                    s.setObject(1, i.id());
                    s.setObject(2, i.visitId());
                    s.setString(3, i.code());
                    s.setString(4, i.status().name());
                    s.setTimestamp(5, ts(i.expiresAt()));
                    s.setTimestamp(6, ts(i.usedAt()));
                    s.setTimestamp(7, ts(i.cancelledAt()));
                    s.setTimestamp(8, ts(i.createdAt()));
                    s.setTimestamp(9, ts(i.updatedAt()));
                });
        batch(connection, """
                        INSERT INTO access_records (id, invitation_id, attempted_code, validated_by_user_id, gate, result, denial_reason,
                                                    entry_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                data.accesses(), (s, a) -> {
                    s.setObject(1, a.id());
                    s.setObject(2, a.invitationId());
                    s.setString(3, a.attemptedCode());
                    s.setObject(4, data.gateUserId());
                    s.setString(5, gate);
                    s.setString(6, a.result().name());
                    s.setString(7, a.denialReason() == null ? null : a.denialReason().name());
                    s.setTimestamp(8, ts(a.entryAt()));
                    s.setTimestamp(9, ts(a.createdAt()));
                });
        batch(connection, "INSERT INTO access_record_companions (access_record_id, companion_id) VALUES (?, ?)",
                data.presences(), (s, p) -> {
                    s.setObject(1, p.accessId());
                    s.setObject(2, p.companionId());
                });
    }

    @FunctionalInterface
    private interface Binder<T> {
        void bind(PreparedStatement statement, T row) throws SQLException;
    }

    private static <T> void batch(Connection connection, String sql, List<T> rows, Binder<T> binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (T row : rows) {
                binder.bind(statement, row);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Map<String, Integer> totals(DemoData data) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        totals.put("usuários", data.users().size());
        totals.put("Prospectores", data.prospectors().size());
        totals.put("Leads", data.leads().size());
        totals.put("visitas", data.visits().size());
        totals.put("acompanhantes", data.companions().size());
        totals.put("convites", data.invitations().size());
        totals.put("acessos", data.accesses().size());
        return totals;
    }
}
