package com.resort.platform.visits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.users.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cada caminho que cancela grava o motivo (D-098): remarcação, cancelamento e descarte do Lead. */
class VisitCancelReasonTest extends VisitTestSupport {

    @Test
    void eachCancellationPathWritesItsReason() throws Exception {
        ProspectorSession me = loggedInProspector();
        LocalDate date = calendar.today().plusDays(3);

        UUID rescheduled = scheduledId(me.client(), testData.lead(me.prospector()).getId(), date, List.of());
        me.client().post("/api/visits/" + rescheduled + "/reschedule", Map.of("scheduledDate", date.plusDays(1).toString()))
                .andExpect(status().isOk());

        UUID cancelled = scheduledId(me.client(), testData.lead(me.prospector()).getId(), date, List.of());
        me.client().patch("/api/visits/" + cancelled + "/cancel", Map.of()).andExpect(status().isOk());

        UUID discardedLead = testData.lead(me.prospector()).getId();
        UUID discarded = scheduledId(me.client(), discardedLead, date, List.of());
        loggedIn(Role.ADMIN).patch("/api/leads/" + discardedLead + "/status", Map.of("status", "CANCELLED"))
                .andExpect(status().isOk());

        assertThat(reasonOf(rescheduled)).isEqualTo("RESCHEDULED");
        assertThat(reasonOf(cancelled)).isEqualTo("CANCELLED_BY_USER");
        assertThat(reasonOf(discarded)).isEqualTo("LEAD_DISCARDED");
        // A visita nova da remarcação segue agendada, sem motivo.
        assertThat(jdbc.sql("SELECT count(*) FROM visits WHERE lead_id = (SELECT lead_id FROM visits WHERE id = :v) "
                        + "AND status = 'SCHEDULED' AND cancel_reason IS NULL").param("v", rescheduled).query(Long.class).single())
                .isEqualTo(1);
    }

    private String reasonOf(UUID visit) {
        return jdbc.sql("SELECT cancel_reason FROM visits WHERE id = :id").param("id", visit).query(String.class).single();
    }
}
