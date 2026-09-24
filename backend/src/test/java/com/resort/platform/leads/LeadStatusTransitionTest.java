package com.resort.platform.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.users.Role;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Matriz completa da SPEC §7.2 para o Lead: as 6 transições manuais permitidas passam; os outros 19
 * pares retornam 409. VISIT_SCHEDULED e VISITED são preparados direto no banco (ainda não há visitas).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LeadStatusTransitionTest extends IntegrationTestSupport {

    static final Set<String> ALLOWED = Set.of(
            "NEW>CONTACTED",
            "NEW>CANCELLED",
            "CONTACTED>CANCELLED",
            "VISIT_SCHEDULED>CANCELLED",
            "VISITED>CANCELLED",
            "CANCELLED>NEW");

    ApiClient admin;
    ProspectorSession prospector;

    @BeforeAll
    void login() throws Exception {
        admin = loggedIn(Role.ADMIN);
        prospector = loggedInProspector();
    }

    static Stream<Arguments> allPairs() {
        return Arrays.stream(LeadStatus.values())
                .flatMap(from -> Arrays.stream(LeadStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "ADMIN {0} → {1}")
    @MethodSource("allPairs")
    void adminMatrix(LeadStatus from, LeadStatus to) throws Exception {
        assertTransition(admin, prospector.prospector(), from, to, ALLOWED.contains(from + ">" + to) ? 200 : 409);
    }

    @ParameterizedTest(name = "PROSPECTOR {0} → {1}")
    @MethodSource("allPairs")
    void prospectorMatrix(LeadStatus from, LeadStatus to) throws Exception {
        int expected = from == LeadStatus.CANCELLED && to == LeadStatus.NEW
                ? 403
                : ALLOWED.contains(from + ">" + to) ? 200 : 409;
        assertTransition(prospector.client(), prospector.prospector(), from, to, expected);
    }

    @Test
    void transitionIsAuditedWithFromAndTo() throws Exception {
        Lead lead = testData.lead(prospector.prospector());

        prospector.client().patch("/api/leads/" + lead.getId() + "/status", Map.of("status", "CONTACTED"))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE entity_id = :id AND action = 'LEAD_STATUS_CHANGED'
                          AND metadata ->> 'from' = 'NEW' AND metadata ->> 'to' = 'CONTACTED'
                        """).param("id", lead.getId()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void unknownStatusValueIsRejected() throws Exception {
        Lead lead = testData.lead(prospector.prospector());

        admin.patch("/api/leads/" + lead.getId() + "/status", Map.of("status", "CONVERTED"))
                .andExpect(status().isBadRequest());
        admin.patch("/api/leads/" + lead.getId() + "/status", Map.of())
                .andExpect(status().isBadRequest());
        assertThat(testData.reloadLead(lead).getStatus()).isEqualTo(LeadStatus.NEW);
    }

    private void assertTransition(ApiClient client, Prospector owner, LeadStatus from, LeadStatus to, int expected)
            throws Exception {
        Lead lead = testData.lead(owner, null, from);

        var result = client.patch("/api/leads/" + lead.getId() + "/status", Map.of("status", to.name()))
                .andExpect(status().is(expected));

        LeadStatus stored = testData.reloadLead(lead).getStatus();
        if (expected == 200) {
            result.andExpect(jsonPath("$.status").value(to.name()));
            assertThat(stored).isEqualTo(to);
        } else {
            result.andExpect(jsonPath("$.code").value(expected == 409 ? "INVALID_STATUS_TRANSITION" : "ACCESS_DENIED"));
            assertThat(stored).isEqualTo(from);
        }
    }
}
