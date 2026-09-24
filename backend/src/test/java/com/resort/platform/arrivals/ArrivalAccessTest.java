package com.resort.platform.arrivals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Perfis e efeitos das rotas de chegadas e ficha (§4.4, D-095, D-096). */
class ArrivalAccessTest extends ArrivalTestSupport {

    // H10
    @Test
    void gateIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
        UUID visit = UUID.randomUUID();
        ApiClient gate = gate();
        gate.get("/api/arrivals").andExpect(status().isForbidden());
        gate.get("/api/visits/" + visit + "/sheet").andExpect(status().isForbidden());
        client().get("/api/arrivals").andExpect(status().isUnauthorized());
        client().get("/api/visits/" + visit + "/sheet").andExpect(status().isUnauthorized());
    }

    // H20
    @Test
    void hostReachesNothingBeyondArrivalsAndSheets() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking booking = book(me, day, 0);
        enter(gate(), booking, at(day, 10, 0));
        clock.set(at(day, 11, 0));
        ApiClient host = host();

        host.get("/api/visits/" + booking.visitId() + "/sheet").andExpect(status().isOk());
        for (String path : List.of("/api/visits", "/api/visits/" + booking.visitId(), "/api/leads",
                "/api/leads/" + booking.leadId(), "/api/invitations", "/api/invitations/" + booking.invitationId(),
                "/api/access/recent")) {
            host.get(path).andExpect(status().isForbidden());
        }
    }

    // H21
    @Test
    void readingChangesNothingAndARevokedSessionStopsTheList() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate day = uniqueDay();
        Booking booking = book(me, day, 1);
        enter(gate(), booking, at(day, 10, 0));
        clock.set(at(day, 11, 0));
        User hostUser = testData.user(Role.HOST);
        ApiClient host = client().login(hostUser.getEmail(), TestData.PASSWORD);
        ApiClient admin = admin();
        long audits = auditTotal();
        Map<String, Object> before = state(booking);

        for (ApiClient reader : List.of(admin, me.client(), host)) {
            reader.get("/api/arrivals").andExpect(status().isOk());
            reader.get("/api/visits/" + booking.visitId() + "/sheet").andExpect(status().isOk());
        }

        assertThat(auditTotal()).isEqualTo(audits);
        assertThat(state(booking)).isEqualTo(before);

        // Polling mantém a sessão viva, mas a desativação pelo ADMIN a encerra (D-047, D-095).
        host.get("/api/arrivals").andExpect(status().isOk());
        admin.patch("/api/users/" + hostUser.getId() + "/status", Map.of("active", false)).andExpect(status().isOk());
        host.get("/api/arrivals").andExpect(status().isUnauthorized());
    }

    private Map<String, Object> state(Booking booking) {
        return jdbc.sql("""
                        SELECT v.status AS visit, v.updated_at AS visit_updated, l.status AS lead, l.updated_at AS lead_updated,
                               i.status AS invitation,
                               (SELECT count(*) FROM access_records r WHERE r.invitation_id = i.id) AS accesses
                        FROM visits v JOIN leads l ON l.id = v.lead_id JOIN invitations i ON i.visit_id = v.id
                        WHERE v.id = :v
                        """)
                .param("v", booking.visitId()).query().singleRow();
    }
}
