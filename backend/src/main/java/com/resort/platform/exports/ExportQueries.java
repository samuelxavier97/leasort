package com.resort.platform.exports;

import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.common.CsvWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQL dos quatro arquivos (§18, D-101). Cada arquivo tem um cabeçalho, um {@code FROM ... WHERE} usado pela
 * contagem e pelas linhas (o mesmo instantâneo REPEATABLE READ), e a formatação de cada linha em português,
 * no fuso da operação. Nada é carregado como entidade.
 */
@Component
class ExportQueries {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static final List<String> LEADS_HEADER =
            List.of("ID", "Nome", "CPF", "Telefone", "E-mail", "Nascimento", "Status", "Prospector", "Criado em");
    static final List<String> VISITS_HEADER = List.of("ID", "Lead", "CPF do Lead", "Prospector", "Data", "Status",
            "Motivo do cancelamento", "Acompanhantes", "Entrada em");
    static final List<String> COMPANIONS_HEADER = List.of("ID da visita", "Data da visita", "Lead", "Nome", "CPF",
            "Nascimento", "Parentesco", "Presente");
    static final List<String> ACCESS_HEADER = List.of("Data e hora", "Resultado", "Motivo", "Lead", "Porteiro",
            "Portaria", "Acompanhantes presentes");

    private final BusinessCalendar calendar;

    ExportQueries(BusinessCalendar calendar) {
        this.calendar = calendar;
    }

    long count(NamedParameterJdbcTemplate jdbc, ExportType type, ExportFilters filters) {
        Query query = query(type, filters);
        Long count = jdbc.queryForObject("SELECT count(*) " + query.fromWhere(), query.params(), Long.class);
        return count == null ? 0 : count;
    }

    /** Escreve cabeçalho e linhas conforme o cursor do banco avança; devolve o número de linhas de dados. */
    long write(NamedParameterJdbcTemplate jdbc, ExportType type, ExportFilters filters, CsvWriter csv) throws IOException {
        Query query = query(type, filters);
        csv.row(header(type).toArray(String[]::new));
        long[] rows = {0};
        try {
            jdbc.query("SELECT " + query.columns() + " " + query.fromWhere() + " ORDER BY " + query.order(), query.params(),
                    (ResultSet rs) -> {
                        try {
                            csv.row(row(type, rs));
                            rows[0]++;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        csv.flush();
        return rows[0];
    }

    static List<String> header(ExportType type) {
        return switch (type) {
            case LEADS -> LEADS_HEADER;
            case VISITS -> VISITS_HEADER;
            case COMPANIONS -> COMPANIONS_HEADER;
            case ACCESS -> ACCESS_HEADER;
        };
    }

    private record Query(String columns, String fromWhere, String order, MapSqlParameterSource params) {}

    private Query query(ExportType type, ExportFilters filters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        if (filters.status() != null) {
            params.addValue("status", filters.status());
        }
        if (filters.prospectorId() != null) {
            params.addValue("p", filters.prospectorId());
        }
        if (filters.hasPeriod()) {
            params.addValue("from", filters.from());
            params.addValue("to", filters.to());
            params.addValue("fromTs", calendar.startOfDay(filters.from()).atOffset(ZoneOffset.UTC));
            params.addValue("toTs", calendar.endOfDay(filters.to()).atOffset(ZoneOffset.UTC));
        }
        return switch (type) {
            case LEADS -> {
                // Período pela criação do Lead; Prospector pelo dono atual (como os Leads do dashboard, D-098).
                if (filters.hasPeriod()) where.add("l.created_at >= :fromTs AND l.created_at < :toTs");
                if (filters.status() != null) where.add("l.status = :status");
                if (filters.prospectorId() != null) where.add("l.prospector_id = :p");
                yield new Query(
                        "l.id, l.name, l.cpf, l.phone, l.email, l.birth_date, l.status, u.name AS prospector, l.created_at",
                        "FROM leads l LEFT JOIN prospectors p ON p.id = l.prospector_id LEFT JOIN users u ON u.id = p.user_id"
                                + where(where),
                        "l.created_at, l.id", params);
            }
            case VISITS -> {
                visitFilters(filters, where);
                yield new Query("""
                        v.id, l.name AS lead, l.cpf, u.name AS prospector, v.scheduled_date, v.status, v.cancel_reason,
                        (SELECT count(*) FROM visit_companions c WHERE c.visit_id = v.id) AS companions,
                        (SELECT r.entry_at FROM access_records r JOIN invitations i ON i.id = r.invitation_id
                         WHERE i.visit_id = v.id AND r.result = 'AUTHORIZED') AS entry_at""",
                        "FROM visits v JOIN leads l ON l.id = v.lead_id JOIN prospectors p ON p.id = v.prospector_id "
                                + "JOIN users u ON u.id = p.user_id" + where(where),
                        "v.scheduled_date, v.id", params);
            }
            case COMPANIONS -> {
                visitFilters(filters, where);
                yield new Query("""
                        v.id AS visit_id, v.scheduled_date, v.status, l.name AS lead, c.name, c.cpf, c.birth_date,
                        c.relationship,
                        EXISTS (SELECT 1 FROM access_record_companions a JOIN access_records r ON r.id = a.access_record_id
                                WHERE a.companion_id = c.id AND r.result = 'AUTHORIZED') AS present""",
                        "FROM visit_companions c JOIN visits v ON v.id = c.visit_id JOIN leads l ON l.id = v.lead_id"
                                + where(where),
                        "v.scheduled_date, v.id, c.created_at, c.id", params);
            }
            case ACCESS -> {
                // Período pela tentativa; Prospector pelo responsável da visita do convite (D-013). Sem convite
                // (INVALID_CODE), o registro só aparece sem o filtro. O código tentado nunca é lido (D-044).
                if (filters.hasPeriod()) where.add("r.created_at >= :fromTs AND r.created_at < :toTs");
                if (filters.status() != null) where.add("r.result = :status");
                if (filters.prospectorId() != null) where.add("v.prospector_id = :p");
                yield new Query("""
                        r.created_at, r.result, r.denial_reason, l.name AS lead, g.name AS gate_user, r.gate,
                        (SELECT count(*) FROM access_record_companions a WHERE a.access_record_id = r.id) AS present""",
                        "FROM access_records r JOIN users g ON g.id = r.validated_by_user_id "
                                + "LEFT JOIN invitations i ON i.id = r.invitation_id LEFT JOIN visits v ON v.id = i.visit_id "
                                + "LEFT JOIN leads l ON l.id = v.lead_id" + where(where),
                        "r.created_at, r.id", params);
            }
        };
    }

    /** Visitas e acompanhantes: período pela data da visita; Prospector pelo responsável (D-013). */
    private static void visitFilters(ExportFilters filters, List<String> where) {
        if (filters.hasPeriod()) where.add("v.scheduled_date BETWEEN :from AND :to");
        if (filters.status() != null) where.add("v.status = :status");
        if (filters.prospectorId() != null) where.add("v.prospector_id = :p");
    }

    private static String where(List<String> conditions) {
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private String[] row(ExportType type, ResultSet rs) throws SQLException {
        return switch (type) {
            case LEADS -> new String[] {
                rs.getString("id"), rs.getString("name"), cpf(rs.getString("cpf")), rs.getString("phone"),
                rs.getString("email"), date(rs.getObject("birth_date", LocalDate.class)),
                ExportLabels.of(ExportLabels.LEAD_STATUS, rs.getString("status")), rs.getString("prospector"),
                dateTime(rs.getTimestamp("created_at"))
            };
            case VISITS -> new String[] {
                rs.getString("id"), rs.getString("lead"), cpf(rs.getString("cpf")), rs.getString("prospector"),
                date(rs.getObject("scheduled_date", LocalDate.class)),
                ExportLabels.of(ExportLabels.VISIT_STATUS, rs.getString("status")),
                ExportLabels.of(ExportLabels.CANCEL_REASON, rs.getString("cancel_reason")),
                String.valueOf(rs.getLong("companions")), dateTime(rs.getTimestamp("entry_at"))
            };
            case COMPANIONS -> new String[] {
                rs.getString("visit_id"), date(rs.getObject("scheduled_date", LocalDate.class)), rs.getString("lead"),
                rs.getString("name"), cpf(rs.getString("cpf")), date(rs.getObject("birth_date", LocalDate.class)),
                ExportLabels.of(ExportLabels.RELATIONSHIP, rs.getString("relationship")),
                // Presença só existe na visita realizada; nos demais status ainda não há ou não haverá entrada.
                "COMPLETED".equals(rs.getString("status")) ? (rs.getBoolean("present") ? "Sim" : "Não") : null
            };
            case ACCESS -> {
                boolean authorized = "AUTHORIZED".equals(rs.getString("result"));
                yield new String[] {
                    dateTime(rs.getTimestamp("created_at")), ExportLabels.of(ExportLabels.ACCESS_RESULT, rs.getString("result")),
                    ExportLabels.of(ExportLabels.DENIAL_REASON, rs.getString("denial_reason")), rs.getString("lead"),
                    rs.getString("gate_user"), rs.getString("gate"), authorized ? String.valueOf(rs.getLong("present")) : null
                };
            }
        };
    }

    /** {@code 12345678909} → {@code 123.456.789-09}: texto, para o Excel não cortar zeros à esquerda. */
    static String cpf(String digits) {
        if (digits == null) return null;
        String value = digits.trim();
        return value.length() == 11
                ? value.substring(0, 3) + "." + value.substring(3, 6) + "." + value.substring(6, 9) + "-" + value.substring(9)
                : value;
    }

    private static String date(LocalDate value) {
        return value == null ? null : DATE.format(value);
    }

    private String dateTime(Timestamp value) {
        return value == null ? null : DATE_TIME.format(value.toInstant().atZone(calendar.zone()));
    }
}
