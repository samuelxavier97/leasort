package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.HttpBrowser;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** Reemissão em HTTP real, com duas sessões liberadas juntas; o lock no Lead serializa (D-073, D-084). */
class InvitationConcurrencyTest extends InvitationTestSupport {

    @LocalServerPort
    int port;

    ExecutorService executor;
    User owner;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        owner = testData.user(Role.PROSPECTOR);
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    /** I19 */
    @RepeatedTest(5)
    void twoSimultaneousReissuesLeaveASingleActiveInvitation() throws Exception {
        HttpBrowser a = session();
        HttpBrowser b = session();
        UUID visit = createVisit(a);
        UUID invitation = activeOf(visit).id();

        List<HttpResponse<String>> responses = race(
                () -> a.post("/api/invitations/" + invitation + "/reissue", null),
                () -> b.post("/api/invitations/" + invitation + "/reissue", null));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
        // Serializado pelo lock no Lead: a segunda lê o convite já cancelado, e não esbarra no índice.
        assertThat(responses).filteredOn(r -> r.statusCode() == 409).singleElement()
                .satisfies(r -> assertThat(r.body()).contains("INVITATION_NOT_ACTIVE"));
        assertThat(activeCount(visit)).isEqualTo(1);
        assertThat(invitationsOf(visit)).hasSize(2);
    }

    /** I20: a reemissão nunca ressuscita o convite de uma visita cancelada. */
    @RepeatedTest(5)
    void reissueRacingWithCancelNeverLeavesAnActiveInvitation() throws Exception {
        HttpBrowser a = session();
        HttpBrowser b = session();
        UUID visit = createVisit(a);
        UUID invitation = activeOf(visit).id();

        race(() -> a.post("/api/invitations/" + invitation + "/reissue", null),
                () -> b.patch("/api/visits/" + visit + "/cancel", null));

        assertThat(visitStatus(visit)).isEqualTo("CANCELLED");
        assertThat(activeCount(visit)).isZero();
    }

    private UUID createVisit(HttpBrowser browser) throws Exception {
        UUID leadId = testData.lead(testData.prospectorOf(owner)).getId();
        HttpResponse<String> created = browser.post("/api/visits", jsonMapper.writeValueAsString(
                Map.of("leadId", leadId, "scheduledDate", calendar.today().plusDays(1).toString())));
        return UUID.fromString(jsonMapper.readTree(created.body()).get("id").asString());
    }

    private HttpBrowser session() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login",
                jsonMapper.writeValueAsString(Map.of("email", owner.getEmail(), "password", TestData.PASSWORD)));
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
