package com.resort.platform.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.TestData;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.users.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeadAssignmentTest extends IntegrationTestSupport {

    ApiClient admin;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        admin = loggedIn(Role.ADMIN);
    }

    @Test
    void assignsInBulkAndAuditsEachLead() throws Exception {
        Prospector previous = prospector(true);
        Prospector target = prospector(true);
        List<Lead> leads = List.of(testData.lead(null), testData.lead(previous), testData.lead(null, null, LeadStatus.CANCELLED));

        admin.patch("/api/leads/assign", Map.of("leadIds", ids(leads), "prospectorId", target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned").value(3));

        for (Lead lead : leads) {
            assertThat(testData.reloadLead(lead).getProspector().getId()).isEqualTo(target.getId());
        }
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM audit_logs WHERE action = 'LEAD_ASSIGNED' AND entity_id = :id
                          AND metadata ->> 'fromProspectorId' = :from AND metadata ->> 'toProspectorId' = :to
                        """).param("id", leads.get(1).getId()).param("from", previous.getId().toString())
                        .param("to", target.getId().toString()).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void unknownLeadAbortsTheWholeOperation() throws Exception {
        Prospector target = prospector(true);
        Lead lead = testData.lead(null);
        List<UUID> ids = new ArrayList<>(List.of(lead.getId(), UUID.randomUUID()));

        admin.patch("/api/leads/assign", Map.of("leadIds", ids, "prospectorId", target.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEAD_NOT_FOUND"));

        assertThat(testData.reloadLead(lead).getProspector()).isNull();
        assertThat(auditCount(lead.getId())).isZero();
    }

    @Test
    void unknownOrInactiveProspectorChangesNothing() throws Exception {
        Lead lead = testData.lead(null);

        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(lead.getId()), "prospectorId", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROSPECTOR_NOT_FOUND"));
        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(lead.getId()), "prospectorId", prospector(false).getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROSPECTOR_INACTIVE"));

        assertThat(testData.reloadLead(lead).getProspector()).isNull();
    }

    @Test
    void invalidListsAreRejected() throws Exception {
        Prospector target = prospector(true);
        UUID id = testData.lead(null).getId();
        List<UUID> tooMany = IntStream.range(0, 501).mapToObj(i -> UUID.randomUUID()).toList();

        for (List<UUID> leadIds : List.of(Collections.<UUID>emptyList(), tooMany, List.of(id, id))) {
            admin.patch("/api/leads/assign", Map.of("leadIds", leadIds, "prospectorId", target.getId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void leadsAlreadyWithTheTargetAreSkippedWithoutAudit() throws Exception {
        Prospector target = prospector(true);
        Lead already = testData.lead(target);
        Lead other = testData.lead(null);

        admin.patch("/api/leads/assign", Map.of("leadIds", List.of(already.getId(), other.getId()), "prospectorId", target.getId()))
                .andExpect(jsonPath("$.assigned").value(1));

        assertThat(auditCount(already.getId())).isZero();
        assertThat(auditCount(other.getId())).isEqualTo(1);
    }

    private Prospector prospector(boolean active) {
        return testData.prospectorOf(testData.user(Role.PROSPECTOR, TestData.PASSWORD, false, active));
    }

    private long auditCount(UUID leadId) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE action = 'LEAD_ASSIGNED' AND entity_id = :id")
                .param("id", leadId).query(Long.class).single();
    }

    private static List<UUID> ids(List<Lead> leads) {
        return leads.stream().map(Lead::getId).toList();
    }
}
