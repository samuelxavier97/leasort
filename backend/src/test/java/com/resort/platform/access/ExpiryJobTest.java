package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.HttpBrowser;
import com.resort.platform.TestData;
import com.resort.platform.common.SchedulingConfig;
import com.resort.platform.invitations.InvitationExpiryJob;
import com.resort.platform.leads.LeadStatus;
import java.lang.reflect.Method;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

/** Job noturno (§20, D-006, D-091): expira convites vencidos e marca NO_SHOW, com lock no Lead. */
class ExpiryJobTest extends AccessTestSupport {

    @Autowired
    InvitationExpiryJob job;

    @Autowired
    ApplicationContext context;

    @LocalServerPort
    int port;

    /** G21 */
    @Test
    void expiresOverdueInvitationsAndMarksNoShow() throws Exception {
        LocalDate day = calendar.today();
        Booking booking = book(loggedInProspector(), day, 1);
        runAt(day.plusDays(1));

        assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("EXPIRED");
        assertThat(stateOf("visits", booking.visitId())).isEqualTo("NO_SHOW");
        assertThat(stateOf("leads", booking.leadId())).isEqualTo("CONTACTED");
        assertSystemAudit("INVITATION_EXPIRED", booking.invitationId());
        assertSystemAudit("VISIT_NO_SHOW", booking.visitId());
        assertThat(jdbc.sql("""
                        SELECT user_id IS NULL AND metadata ->> 'cause' = 'VISIT_NO_SHOW' FROM audit_logs
                        WHERE action = 'LEAD_STATUS_CHANGED' AND entity_id = :id ORDER BY created_at DESC LIMIT 1
                        """).param("id", booking.leadId()).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE metadata::text LIKE :code")
                .param("code", "%" + booking.code() + "%").query(Long.class).single()).isZero();
    }

    /** G22 */
    @Test
    void leavesEverythingElseUntouched() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = calendar.today();
        Booking nextDay = book(me, day.plusDays(1), 0);
        Booking future = book(me, day.plusDays(5), 0);
        Booking used = book(me, day, 0);
        register(gate().client(), used.invitationId(), List.of());
        Booking cancelled = book(me, day, 0);
        me.client().patch("/api/visits/" + cancelled.visitId() + "/cancel", null);
        UUID legacyLead = testData.lead(me.prospector(), null, LeadStatus.VISIT_SCHEDULED).getId();
        UUID legacyVisit = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO visits (id, lead_id, prospector_id, scheduled_date, status)
                        VALUES (:id, :lead, :prospector, :date, 'SCHEDULED')
                        """)
                .param("id", legacyVisit).param("lead", legacyLead).param("prospector", me.prospector().getId())
                .param("date", day).update();

        runAt(day.plusDays(1));

        assertThat(stateOf("invitations", nextDay.invitationId())).isEqualTo("ACTIVE");
        assertThat(stateOf("invitations", future.invitationId())).isEqualTo("ACTIVE");
        assertThat(stateOf("invitations", used.invitationId())).isEqualTo("USED");
        assertThat(stateOf("visits", used.visitId())).isEqualTo("COMPLETED");
        assertThat(stateOf("invitations", cancelled.invitationId())).isEqualTo("CANCELLED");
        assertThat(stateOf("visits", legacyVisit)).isEqualTo("SCHEDULED");
        assertThat(stateOf("leads", legacyLead)).isEqualTo("VISIT_SCHEDULED");
    }

    /** G23: idempotente, e recupera noites em que o job não rodou numa única execução. */
    @Test
    void isIdempotentAndCatchesUpMissedNights() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = calendar.today();
        List<Booking> bookings = List.of(book(me, day, 0), book(me, day.plusDays(1), 0), book(me, day.plusDays(2), 0));

        // Três noites sem job; roda uma vez, cinco dias depois.
        runAt(day.plusDays(5));
        for (Booking booking : bookings) {
            assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("EXPIRED");
            assertThat(stateOf("visits", booking.visitId())).isEqualTo("NO_SHOW");
        }
        long audits = auditTotal();

        assertThat(runAt(day.plusDays(5))).isZero();
        assertThat(auditTotal()).isEqualTo(audits);
        for (Booking booking : bookings) {
            assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'INVITATION_EXPIRED' AND entity_id = :id")
                    .param("id", booking.invitationId()).query(Long.class).single()).isEqualTo(1);
        }
    }

    /** G24: job e cancelamento no mesmo Lead ao mesmo tempo; sem estado misturado nem deadlock. */
    @RepeatedTest(3)
    void jobRacingWithCancelIsConsistent() throws Exception {
        ProspectorSession owner = loggedInProspector();
        LocalDate day = calendar.today();
        Booking booking = book(owner, day, 0);
        HttpBrowser prospector = session(owner);
        clock.set(calendar.endOfDay(day).plusSeconds(60));

        HttpResponse<String> cancel = raceWithJob(() -> prospector.patch("/api/visits/" + booking.visitId() + "/cancel", null));

        assertThat(cancel.statusCode()).as(cancel.body()).isIn(200, 409);
        String visit = stateOf("visits", booking.visitId());
        String invitation = stateOf("invitations", booking.invitationId());
        assertThat(visit + "/" + invitation).isIn("NO_SHOW/EXPIRED", "CANCELLED/CANCELLED");
    }

    /** G24: job e reemissão; a visita nunca fica NO_SHOW com convite ativo. */
    @RepeatedTest(3)
    void jobRacingWithReissueIsConsistent() throws Exception {
        ProspectorSession owner = loggedInProspector();
        LocalDate day = calendar.today();
        Booking booking = book(owner, day, 0);
        HttpBrowser prospector = session(owner);
        clock.set(calendar.endOfDay(day).plusSeconds(60));

        HttpResponse<String> reissue = raceWithJob(
                () -> prospector.post("/api/invitations/" + booking.invitationId() + "/reissue", null));

        assertThat(reissue.statusCode()).as(reissue.body()).isIn(200, 409);
        long active = jdbc.sql("SELECT count(*) FROM invitations WHERE visit_id = :v AND status = 'ACTIVE'")
                .param("v", booking.visitId()).query(Long.class).single();
        String visit = stateOf("visits", booking.visitId());
        assertThat(visit + "/" + active).isIn("NO_SHOW/0", "SCHEDULED/1");
        // Uma próxima execução termina o trabalho, se a reemissão venceu.
        job.run();
        assertThat(stateOf("visits", booking.visitId())).isEqualTo("NO_SHOW");
    }

    /** G25 */
    @Test
    void runsAtQuarterPastMidnightInTheOperationTimezoneAndIsOffInTests() throws Exception {
        Method method = InvitationExpiryJob.class.getMethod("scheduled");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 15 0 * * *");
        assertThat(scheduled.zone()).isEqualTo("${app.timezone}");
        assertThat(context.getBeansOfType(SchedulingConfig.class)).isEmpty();
    }

    /** Relógio às 00:15 do dia informado, em APP_TIMEZONE, como o agendamento real. */
    private int runAt(LocalDate day) {
        clock.set(day.atTime(LocalTime.of(0, 15)).atZone(calendar.zone()).toInstant());
        return job.run();
    }

    private void assertSystemAudit(String action, UUID entityId) {
        assertThat(jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = :a AND entity_id = :id AND user_id IS NULL")
                .param("a", action).param("id", entityId).query(Long.class).single()).isEqualTo(1);
    }

    private HttpResponse<String> raceWithJob(java.util.concurrent.Callable<HttpResponse<String>> call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Integer> jobRun = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return job.run();
            });
            Future<HttpResponse<String>> http = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call.call();
            });
            jobRun.get(30, TimeUnit.SECONDS);
            return http.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpBrowser session(ProspectorSession owner) throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login",
                jsonMapper.writeValueAsString(Map.of("email", owner.user().getEmail(), "password", TestData.PASSWORD)));
        return browser;
    }
}
