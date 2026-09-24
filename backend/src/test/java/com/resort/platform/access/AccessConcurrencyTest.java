package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.HttpBrowser;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Concorrência em HTTP real (§12.3, D-089): o registro trava o Lead antes do convite, na mesma ordem das
 * escritas de visita e convite (D-073). Nunca duas entradas, nunca estado misturado, nunca deadlock.
 */
class AccessConcurrencyTest extends AccessTestSupport {

    @LocalServerPort
    int port;

    ExecutorService executor;
    User gateUser;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        gateUser = testData.user(Role.GATE);
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    /** G14: duas confirmações simultâneas; uma entrada, e a outra recebe ALREADY_USED. */
    @RepeatedTest(5)
    void twoSimultaneousRegistrationsRecordASingleEntry() throws Exception {
        Booking booking = book(loggedInProspector(), calendar.today(), 1);
        HttpBrowser a = session(gateUser);
        HttpBrowser b = session(gateUser);
        String body = registerBody(booking);

        List<HttpResponse<String>> responses = race(() -> a.post("/api/access/register", body),
                () -> b.post("/api/access/register", body));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(200, 200);
        assertThat(responses).extracting(r -> jsonMapper.readTree(r.body()).get("result").asString())
                .containsExactlyInAnyOrder("AUTHORIZED", "DENIED");
        assertThat(responses).filteredOn(r -> r.body().contains("DENIED")).singleElement()
                .satisfies(r -> assertThat(jsonMapper.readTree(r.body()).get("denialReason").asString()).isEqualTo("ALREADY_USED"));
        assertThat(authorizedCount(booking.invitationId())).isEqualTo(1);
        assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("USED");
    }

    /** G15 */
    @RepeatedTest(5)
    void registrationRacingWithCancelIsConsistent() throws Exception {
        ProspectorSession owner = loggedInProspector();
        Booking booking = book(owner, calendar.today(), 0);
        HttpBrowser gate = session(gateUser);
        HttpBrowser prospector = session(owner.user());

        List<HttpResponse<String>> responses = race(() -> gate.post("/api/access/register", registerBody(booking)),
                () -> prospector.patch("/api/visits/" + booking.visitId() + "/cancel", null));

        assertNoServerError(responses);
        assertConsistent(booking);
    }

    /** G16 */
    @RepeatedTest(5)
    void registrationRacingWithReissueIsConsistent() throws Exception {
        ProspectorSession owner = loggedInProspector();
        Booking booking = book(owner, calendar.today(), 0);
        HttpBrowser gate = session(gateUser);
        HttpBrowser prospector = session(owner.user());

        List<HttpResponse<String>> responses = race(() -> gate.post("/api/access/register", registerBody(booking)),
                () -> prospector.post("/api/invitations/" + booking.invitationId() + "/reissue", null));

        assertNoServerError(responses);
        if (authorizedCount(booking.invitationId()) == 1) {
            assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("USED");
            assertThat(stateOf("visits", booking.visitId())).isEqualTo("COMPLETED");
            assertThat(activeInvitations(booking)).isZero();
        } else {
            assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("CANCELLED");
            assertThat(stateOf("visits", booking.visitId())).isEqualTo("SCHEDULED");
            assertThat(activeInvitations(booking)).isEqualTo(1);
        }
    }

    /** G16 */
    @RepeatedTest(5)
    void registrationRacingWithRescheduleIsConsistent() throws Exception {
        ProspectorSession owner = loggedInProspector();
        Booking booking = book(owner, calendar.today(), 0);
        HttpBrowser gate = session(gateUser);
        HttpBrowser prospector = session(owner.user());
        String reschedule = jsonMapper.writeValueAsString(Map.of("scheduledDate", calendar.today().plusDays(2).toString()));

        List<HttpResponse<String>> responses = race(() -> gate.post("/api/access/register", registerBody(booking)),
                () -> prospector.post("/api/visits/" + booking.visitId() + "/reschedule", reschedule));

        assertNoServerError(responses);
        assertConsistent(booking);
    }

    /** G17 */
    @RepeatedTest(5)
    void registrationRacingWithDiscardIsConsistent() throws Exception {
        ProspectorSession owner = loggedInProspector();
        Booking booking = book(owner, calendar.today(), 0);
        HttpBrowser gate = session(gateUser);
        HttpBrowser prospector = session(owner.user());
        String discard = jsonMapper.writeValueAsString(Map.of("status", "CANCELLED"));

        List<HttpResponse<String>> responses = race(() -> gate.post("/api/access/register", registerBody(booking)),
                () -> prospector.patch("/api/leads/" + booking.leadId() + "/status", discard));

        assertNoServerError(responses);
        assertConsistent(booking);
        assertThat(stateOf("leads", booking.leadId())).isEqualTo("CANCELLED");
    }

    /** Ou a entrada venceu (convite USED, visita COMPLETED), ou a outra escrita venceu (convite e visita CANCELLED). */
    private void assertConsistent(Booking booking) {
        if (authorizedCount(booking.invitationId()) == 1) {
            assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("USED");
            assertThat(stateOf("visits", booking.visitId())).isEqualTo("COMPLETED");
        } else {
            assertThat(stateOf("invitations", booking.invitationId())).isEqualTo("CANCELLED");
            assertThat(stateOf("visits", booking.visitId())).isEqualTo("CANCELLED");
            assertThat(accessRowsBy(gateUser.getId())).last()
                    .satisfies(row -> assertThat(row.denialReason()).isEqualTo("CANCELLED"));
        }
    }

    private long activeInvitations(Booking booking) {
        return jdbc.sql("SELECT count(*) FROM invitations WHERE visit_id = :v AND status = 'ACTIVE'")
                .param("v", booking.visitId()).query(Long.class).single();
    }

    private static void assertNoServerError(List<HttpResponse<String>> responses) {
        // Um deadlock apareceria como 500; conflitos de negócio são 200 (DENIED) ou 409.
        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isLessThan(500));
    }

    private String registerBody(Booking booking) {
        return jsonMapper.writeValueAsString(Map.of("invitationId", booking.invitationId(), "presentCompanionIds", booking.companionIds()));
    }

    private HttpBrowser session(User user) throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login", jsonMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", TestData.PASSWORD)));
        return browser;
    }

    @SafeVarargs
    private List<HttpResponse<String>> race(Callable<HttpResponse<String>>... calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.length);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (Callable<HttpResponse<String>> call : calls) {
            futures.add(executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call.call();
            }));
        }
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (Future<HttpResponse<String>> future : futures) {
            responses.add(future.get(30, TimeUnit.SECONDS));
        }
        return responses;
    }
}
