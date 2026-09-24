package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.HttpBrowser;
import com.resort.platform.TestData;
import com.resort.platform.leads.Lead;
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

/**
 * Concorrência em HTTP real, com duas sessões liberadas ao mesmo tempo. O lock pessimista no Lead
 * serializa as operações; o índice parcial é o último seguro (RN04, D-073, D-077).
 */
class VisitConcurrencyTest extends VisitTestSupport {

    @LocalServerPort
    int port;

    ExecutorService executor;
    User owner;
    UUID prospectorId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        owner = testData.user(com.resort.platform.users.Role.PROSPECTOR);
        prospectorId = testData.prospectorOf(owner).getId();
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @RepeatedTest(5)
    void twoSimultaneousSchedulesForTheSameLeadCreateOnlyOne() throws Exception {
        Lead lead = testData.lead(testData.prospectorOf(owner));
        String body = jsonMapper.writeValueAsString(visitBody(lead.getId(), calendar.today().plusDays(1), List.of()));
        HttpBrowser first = session();
        HttpBrowser second = session();

        List<Integer> statuses = race(() -> first.post("/api/visits", body), () -> second.post("/api/visits", body));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(scheduledCount(lead.getId())).isEqualTo(1);
        assertThat(activeInvitations(lead.getId())).isEqualTo(1);
    }

    /** Descarte × agendamento: nunca um Lead descartado com visita agendada. */
    @RepeatedTest(5)
    void discardRacingWithScheduleNeverLeavesAnOrphanVisit() throws Exception {
        Lead lead = testData.lead(testData.prospectorOf(owner));
        String visit = jsonMapper.writeValueAsString(visitBody(lead.getId(), calendar.today().plusDays(1), List.of()));
        String discard = jsonMapper.writeValueAsString(Map.of("status", "CANCELLED"));
        HttpBrowser a = session();
        HttpBrowser b = session();

        race(() -> a.post("/api/visits", visit), () -> b.patch("/api/leads/" + lead.getId() + "/status", discard));

        assertThat(leadStatus(lead.getId())).isEqualTo("CANCELLED");
        assertThat(scheduledCount(lead.getId())).isZero();
        assertThat(activeInvitations(lead.getId())).isZero();
    }

    /** Descarte × cancelamento: o descarte nunca é desfeito por um cancelamento que leu estado antigo. */
    @RepeatedTest(5)
    void discardRacingWithCancelKeepsTheLeadDiscarded() throws Exception {
        Lead lead = testData.lead(testData.prospectorOf(owner));
        HttpBrowser a = session();
        HttpBrowser b = session();
        HttpResponse<String> created = a.post("/api/visits",
                jsonMapper.writeValueAsString(visitBody(lead.getId(), calendar.today().plusDays(1), List.of())));
        String visitId = jsonMapper.readTree(created.body()).get("id").asString();
        String discard = jsonMapper.writeValueAsString(Map.of("status", "CANCELLED"));

        race(() -> a.patch("/api/visits/" + visitId + "/cancel", null),
                () -> b.patch("/api/leads/" + lead.getId() + "/status", discard));

        assertThat(leadStatus(lead.getId())).isEqualTo("CANCELLED");
        assertThat(visitStatus(UUID.fromString(visitId))).isEqualTo("CANCELLED");
        assertThat(activeInvitations(lead.getId())).isZero();
    }

    /** I30: convites ACTIVE do Lead, que acompanham as visitas SCHEDULED (RN06). */
    private long activeInvitations(UUID leadId) {
        return jdbc.sql("""
                        SELECT count(*) FROM invitations i JOIN visits v ON v.id = i.visit_id
                        WHERE v.lead_id = :id AND i.status = 'ACTIVE'
                        """).param("id", leadId).query(Long.class).single();
    }

    private HttpBrowser session() throws Exception {
        HttpBrowser browser = new HttpBrowser(port);
        browser.post("/api/auth/login",
                jsonMapper.writeValueAsString(Map.of("email", owner.getEmail(), "password", TestData.PASSWORD)));
        return browser;
    }

    @SafeVarargs
    private List<Integer> race(Callable<HttpResponse<String>>... calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.length);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (Callable<HttpResponse<String>> call : calls) {
            futures.add(executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call.call();
            }));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<HttpResponse<String>> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS).statusCode());
        }
        return statuses;
    }
}
