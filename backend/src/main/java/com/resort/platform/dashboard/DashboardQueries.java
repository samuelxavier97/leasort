package com.resort.platform.dashboard;

import com.resort.platform.dashboard.dto.AccessByDayResponse;
import com.resort.platform.dashboard.dto.DashboardPeriod;
import com.resort.platform.dashboard.dto.UpcomingVisit;
import com.resort.platform.dashboard.dto.VisitsByDayResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    /** Contagens por status de visita, num só passe pela tabela. */
    record VisitCounts(long scheduled, long visitsToday, long completed, long noShows, long cancellations) {}

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

    VisitCounts visits(LocalDate today, DashboardPeriod period, UUID prospectorId) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE status = 'SCHEDULED' AND scheduled_date >= :today) AS scheduled,
                               count(*) FILTER (WHERE scheduled_date = :today AND status IN ('SCHEDULED', 'COMPLETED')) AS visits_today,
                               count(*) FILTER (WHERE status = 'COMPLETED' AND scheduled_date BETWEEN :from AND :to) AS completed,
                               count(*) FILTER (WHERE status = 'NO_SHOW' AND scheduled_date BETWEEN :from AND :to) AS no_shows,
                               count(*) FILTER (WHERE status = 'CANCELLED' AND cancel_reason <> 'RESCHEDULED'
                                                  AND scheduled_date BETWEEN :from AND :to) AS cancellations
                        FROM visits
                        WHERE (scheduled_date >= :today OR scheduled_date BETWEEN :from AND :to)
                        """ + (prospectorId == null ? "" : " AND prospector_id = :p"))
                .param("today", today)
                .param("from", period.from())
                .param("to", period.to())
                .params(scope(prospectorId))
                .query((rs, n) -> new VisitCounts(rs.getLong("scheduled"), rs.getLong("visits_today"),
                        rs.getLong("completed"), rs.getLong("no_shows"), rs.getLong("cancellations")))
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
     * Acessos por dia do fuso da operação. Com Prospector, só os registros ligados a um convite dele; negativas
     * sem convite (INVALID_CODE) aparecem só na visão geral.
     */
    List<AccessByDayResponse.Day> accessByDay(DashboardPeriod period, Instant from, Instant to, String zone, UUID prospectorId) {
        String scoped = prospectorId == null
                ? ""
                : " AND r.invitation_id IN (SELECT i.id FROM invitations i JOIN visits v ON v.id = i.visit_id WHERE v.prospector_id = :p)";
        return jdbc.sql("""
                        WITH records AS (
                            SELECT r.id, r.result,
                                   CASE WHEN r.result = 'AUTHORIZED' THEN r.entry_at ELSE r.created_at END AS at
                            FROM access_records r
                            WHERE (CASE WHEN r.result = 'AUTHORIZED' THEN r.entry_at ELSE r.created_at END) >= :fromTs
                              AND (CASE WHEN r.result = 'AUTHORIZED' THEN r.entry_at ELSE r.created_at END) < :toTs
                        """ + scoped + """

                        )
                        SELECT CAST(d AS date) AS day,
                               count(records.id) FILTER (WHERE records.result = 'AUTHORIZED') AS authorized,
                               count(records.id) FILTER (WHERE records.result = 'DENIED') AS denied
                        FROM generate_series(CAST(:from AS timestamp), CAST(:to AS timestamp), interval '1 day') AS d
                            LEFT JOIN records ON CAST(timezone(:zone, records.at) AS date) = CAST(d AS date)
                        GROUP BY d
                        ORDER BY d
                        """)
                .param("from", period.from())
                .param("to", period.to())
                .param("fromTs", ts(from))
                .param("toTs", ts(to))
                .param("zone", zone)
                .params(scope(prospectorId))
                .query((rs, n) -> new AccessByDayResponse.Day(rs.getObject("day", LocalDate.class),
                        rs.getLong("authorized"), rs.getLong("denied")))
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
