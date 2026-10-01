package com.resort.platform.dashboard;

import com.resort.platform.access.DenialReason;
import com.resort.platform.dashboard.dto.DashboardPeriod;
import com.resort.platform.dashboard.dto.ProspectorPerformanceResponse;
import com.resort.platform.dashboard.dto.TodayVisit;
import com.resort.platform.dashboard.dto.UpcomingVisit;
import com.resort.platform.dashboard.dto.VisitsByDayResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas agregadas do dashboard em SQL, sem carregar entidades (D-098). O escopo por Prospector conta
 * visitas, convites e entradas por {@code visits.prospector_id} (crédito, D-013) e Leads pelo dono atual.
 */
@Repository
class DashboardQueries {

    /** Contagens por status de visita, num só passe pela tabela; {@code previous*} no período anterior (D-128). */
    record VisitCounts(long scheduled, long visitsToday, long arrivedToday, long completed, long noShows, long cancellations,
            long previousCompleted, long previousNoShows, long previousCancellations) {}

    record LeadCounts(long total, long assigned) {}

    private final JdbcClient jdbc;

    DashboardQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Leads ativos (D-008); com Prospector, os da carteira atual dele. */
    LeadCounts leads(UUID prospectorId) {
        return jdbc.sql("""
                        SELECT count(*) AS total, count(prospector_id) AS assigned
                        FROM leads
                        WHERE status <> 'CANCELLED'
                        """ + (prospectorId == null ? "" : " AND prospector_id = :p"))
                .params(scope(prospectorId))
                .query((rs, n) -> new LeadCounts(rs.getLong("total"), rs.getLong("assigned")))
                .single();
    }

    VisitCounts visits(LocalDate today, DashboardPeriod period, DashboardPeriod previous, UUID prospectorId) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE status = 'SCHEDULED' AND scheduled_date >= :today) AS scheduled,
                               count(*) FILTER (WHERE scheduled_date = :today AND status IN ('SCHEDULED', 'COMPLETED')) AS visits_today,
                               count(*) FILTER (WHERE scheduled_date = :today AND status = 'COMPLETED') AS arrived_today,
                               count(*) FILTER (WHERE status = 'COMPLETED' AND scheduled_date BETWEEN :from AND :to) AS completed,
                               count(*) FILTER (WHERE status = 'NO_SHOW' AND scheduled_date BETWEEN :from AND :to) AS no_shows,
                               count(*) FILTER (WHERE status = 'CANCELLED' AND cancel_reason <> 'RESCHEDULED'
                                                  AND scheduled_date BETWEEN :from AND :to) AS cancellations,
                               count(*) FILTER (WHERE status = 'COMPLETED' AND scheduled_date BETWEEN :pfrom AND :pto) AS p_completed,
                               count(*) FILTER (WHERE status = 'NO_SHOW' AND scheduled_date BETWEEN :pfrom AND :pto) AS p_no_shows,
                               count(*) FILTER (WHERE status = 'CANCELLED' AND cancel_reason <> 'RESCHEDULED'
                                                  AND scheduled_date BETWEEN :pfrom AND :pto) AS p_cancellations
                        FROM visits
                        WHERE (scheduled_date >= :today OR scheduled_date BETWEEN :from AND :to
                               OR scheduled_date BETWEEN :pfrom AND :pto)
                        """ + (prospectorId == null ? "" : " AND prospector_id = :p"))
                .param("today", today)
                .param("from", period.from())
                .param("to", period.to())
                .param("pfrom", previous.from())
                .param("pto", previous.to())
                .params(scope(prospectorId))
                .query((rs, n) -> new VisitCounts(rs.getLong("scheduled"), rs.getLong("visits_today"),
                        rs.getLong("arrived_today"), rs.getLong("completed"), rs.getLong("no_shows"),
                        rs.getLong("cancellations"), rs.getLong("p_completed"), rs.getLong("p_no_shows"),
                        rs.getLong("p_cancellations")))
                .single();
    }

    /** Convites ACTIVE ainda dentro da validade: um vencido que o job não processou não conta (D-091). */
    long activeInvitations(Instant now, UUID prospectorId) {
        return jdbc.sql("""
                        SELECT count(*)
                        FROM invitations i JOIN visits v ON v.id = i.visit_id
                        WHERE i.status = 'ACTIVE' AND i.expires_at > :now
                        """ + (prospectorId == null ? "" : " AND v.prospector_id = :p"))
                .param("now", ts(now))
                .params(scope(prospectorId))
                .query(Long.class)
                .single();
    }

    /** Pessoas que entraram: o Lead mais os acompanhantes presentes de cada entrada liberada do período. */
    long entries(Instant from, Instant to, UUID prospectorId) {
        return jdbc.sql("""
                        SELECT coalesce(sum(1 + (SELECT count(*) FROM access_record_companions c
                                                 WHERE c.access_record_id = r.id)), 0)
                        FROM access_records r
                            JOIN invitations i ON i.id = r.invitation_id
                            JOIN visits v ON v.id = i.visit_id
                        WHERE r.result = 'AUTHORIZED' AND r.entry_at >= :from AND r.entry_at < :to
                        """ + (prospectorId == null ? "" : " AND v.prospector_id = :p"))
                .param("from", ts(from))
                .param("to", ts(to))
                .params(scope(prospectorId))
                .query(Long.class)
                .single();
    }

    List<VisitsByDayResponse.Day> visitsByDay(DashboardPeriod period, UUID prospectorId) {
        return jdbc.sql("""
                        SELECT CAST(d AS date) AS day,
                               count(v.id) FILTER (WHERE v.status = 'SCHEDULED') AS scheduled,
                               count(v.id) FILTER (WHERE v.status = 'COMPLETED') AS completed,
                               count(v.id) FILTER (WHERE v.status = 'NO_SHOW') AS no_show,
                               count(v.id) FILTER (WHERE v.status = 'CANCELLED' AND v.cancel_reason <> 'RESCHEDULED') AS cancelled
                        FROM generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp), interval '1 day') AS d
                            LEFT JOIN visits v ON v.scheduled_date = CAST(d AS date)
                        """ + (prospectorId == null ? "" : " AND v.prospector_id = :p") + """

                        GROUP BY d
                        ORDER BY d
                        """)
                .param("from", period.from())
                .param("to", period.to())
                .params(scope(prospectorId))
                .query((rs, n) -> new VisitsByDayResponse.Day(rs.getObject("day", LocalDate.class),
                        rs.getLong("scheduled"), rs.getLong("completed"), rs.getLong("no_show"), rs.getLong("cancelled")))
                .list();
    }

    /**
     * Negativas por motivo (D-129), pelo {@code created_at} da tentativa. Com Prospector, só as ligadas a um
     * convite dele; {@code INVALID_CODE} (sem convite) aparece só na visão geral, como nas exportações.
     */
    Map<DenialReason, Long> denials(Instant from, Instant to, UUID prospectorId) {
        String scoped = prospectorId == null
                ? ""
                : " AND r.invitation_id IN (SELECT i.id FROM invitations i JOIN visits v ON v.id = i.visit_id WHERE v.prospector_id = :p)";
        Map<DenialReason, Long> counts = new EnumMap<>(DenialReason.class);
        for (DenialReason reason : DenialReason.values()) {
            counts.put(reason, 0L);
        }
        jdbc.sql("""
                        SELECT r.denial_reason, count(*) AS total
                        FROM access_records r
                        WHERE r.result = 'DENIED' AND r.created_at >= :from AND r.created_at < :to
                        """ + scoped + """

                        GROUP BY r.denial_reason
                        """)
                .param("from", ts(from))
                .param("to", ts(to))
                .params(scope(prospectorId))
                .query((rs, n) -> Map.entry(DenialReason.valueOf(rs.getString("denial_reason")), rs.getLong("total")))
                .list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /**
     * Desempenho por Prospector (D-128): realizadas e sem comparecimento pela data da visita, por crédito
     * (D-013). Só quem teve ao menos uma das duas no período; ordem por realizadas, taxa e nome.
     */
    List<ProspectorPerformanceResponse.Row> prospectorPerformance(DashboardPeriod period) {
        return jdbc.sql("""
                        SELECT p.id, u.name,
                               count(*) FILTER (WHERE v.status = 'COMPLETED') AS completed,
                               count(*) FILTER (WHERE v.status = 'NO_SHOW') AS no_shows
                        FROM visits v
                            JOIN prospectors p ON p.id = v.prospector_id
                            JOIN users u ON u.id = p.user_id
                        WHERE v.status IN ('COMPLETED', 'NO_SHOW') AND v.scheduled_date BETWEEN :from AND :to
                        GROUP BY p.id, u.name
                        ORDER BY completed DESC,
                                 CAST(count(*) FILTER (WHERE v.status = 'COMPLETED') AS numeric) / count(*) DESC,
                                 u.name, p.id
                        """)
                .param("from", period.from())
                .param("to", period.to())
                .query((rs, n) -> {
                    long completed = rs.getLong("completed");
                    long noShows = rs.getLong("no_shows");
                    return new ProspectorPerformanceResponse.Row(rs.getObject("id", UUID.class), rs.getString("name"),
                            completed, noShows, (double) completed / (completed + noShows));
                })
                .list();
    }

    /**
     * Visitas de hoje (D-128), por crédito como os cartões: as que chegaram pela hora de entrada, depois as
     * agendadas pelo nome do Lead. A entrada é o registro liberado do convite da visita (D-021).
     */
    List<TodayVisit> todayVisits(LocalDate today, UUID prospectorId, int limit) {
        return jdbc.sql("""
                        SELECT v.id, l.name AS lead_name, u.name AS prospector_name,
                               (SELECT count(*) FROM visit_companions c WHERE c.visit_id = v.id) AS companions,
                               entry.entry_at, entry.present
                        FROM visits v
                            JOIN leads l ON l.id = v.lead_id
                            JOIN prospectors p ON p.id = v.prospector_id
                            JOIN users u ON u.id = p.user_id
                            LEFT JOIN LATERAL (
                                SELECT r.entry_at,
                                       (SELECT count(*) FROM access_record_companions ac WHERE ac.access_record_id = r.id) AS present
                                FROM access_records r JOIN invitations i ON i.id = r.invitation_id
                                WHERE i.visit_id = v.id AND r.result = 'AUTHORIZED'
                                ORDER BY r.entry_at
                                LIMIT 1
                            ) entry ON v.status = 'COMPLETED'
                        WHERE v.scheduled_date = :today AND v.status IN ('SCHEDULED', 'COMPLETED')
                        """ + (prospectorId == null ? "" : " AND v.prospector_id = :p") + """

                        ORDER BY entry.entry_at NULLS LAST, l.name, v.id
                        LIMIT :limit
                        """)
                .param("today", today)
                .param("limit", limit)
                .params(scope(prospectorId))
                .query((rs, n) -> {
                    OffsetDateTime entryAt = rs.getObject("entry_at", OffsetDateTime.class);
                    return new TodayVisit(rs.getObject("id", UUID.class), rs.getString("lead_name"),
                            rs.getString("prospector_name"), rs.getInt("companions"),
                            entryAt == null ? null : rs.getInt("present"), entryAt == null ? null : entryAt.toInstant());
                })
                .list();
    }

    /** Leitura da D-041 (responsável ou dono atual), só SCHEDULED a partir de hoje, por data e nome. */
    List<UpcomingVisit> upcomingVisits(LocalDate today, UUID prospectorId, int limit) {
        return jdbc.sql("""
                        SELECT v.id, v.scheduled_date, l.name,
                               (SELECT count(*) FROM visit_companions c WHERE c.visit_id = v.id) AS companions,
                               (l.prospector_id = :p) AS can_edit
                        FROM visits v JOIN leads l ON l.id = v.lead_id
                        WHERE v.status = 'SCHEDULED' AND v.scheduled_date >= :today
                          AND (v.prospector_id = :p OR l.prospector_id = :p)
                        ORDER BY v.scheduled_date, l.name, v.id
                        LIMIT :limit
                        """)
                .param("today", today)
                .param("p", prospectorId)
                .param("limit", limit)
                .query((rs, n) -> new UpcomingVisit(rs.getObject("id", UUID.class),
                        rs.getObject("scheduled_date", LocalDate.class), rs.getString("name"),
                        rs.getInt("companions"), rs.getBoolean("can_edit")))
                .list();
    }

    /** O driver do PostgreSQL não infere o tipo de {@link Instant}; {@code OffsetDateTime} vira timestamptz. */
    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Map<String, Object> scope(UUID prospectorId) {
        return prospectorId == null ? Map.of() : Map.of("p", prospectorId);
    }
}
