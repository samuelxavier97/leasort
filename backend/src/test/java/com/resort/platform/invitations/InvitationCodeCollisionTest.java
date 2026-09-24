package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.TestCodes;
import com.resort.platform.leads.Lead;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** RN07 e D-082: nova tentativa em colisão; códigos nunca reaproveitados. */
@ExtendWith(OutputCaptureExtension.class)
class InvitationCodeCollisionTest extends InvitationTestSupport {

    /** I10: um código já usado, mesmo por convite cancelado, é descartado e outro é gerado. */
    @Test
    void collisionWithAnExistingCodeGeneratesAnotherOne() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID existingVisit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(1));
        InvitationRow existing = activeOf(existingVisit);
        me.client().patch("/api/visits/" + existingVisit + "/cancel", null).andExpect(status().isOk());
        String fresh = TestCodes.unique();
        random.enqueueCodes(existing.code(), fresh);

        UUID visit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(2));

        assertThat(activeOf(visit).code()).isEqualTo(fresh);
        assertThat(invitationsOf(existingVisit)).singleElement()
                .satisfies(row -> {
                    assertThat(row.code()).isEqualTo(existing.code());
                    assertThat(row.status()).isEqualTo("CANCELLED");
                });
    }

    /** I11: esgotadas as tentativas, 500 genérico, nada gravado e o código fora do log. */
    @Test
    void exhaustedAttemptsFailWithoutSavingOrLoggingTheCode(CapturedOutput output) throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID existingVisit = scheduledVisit(me.client(), testData.lead(me.prospector()).getId(), calendar.today().plusDays(1));
        String taken = activeOf(existingVisit).code();
        for (int i = 0; i < InvitationService.MAX_CODE_ATTEMPTS; i++) {
            random.enqueueCodes(taken);
        }
        Lead lead = testData.lead(me.prospector());

        schedule(me.client(), lead.getId(), calendar.today().plusDays(2))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(jdbc.sql("SELECT count(*) FROM visits WHERE lead_id = :id").param("id", lead.getId())
                .query(Long.class).single()).isZero();
        assertThat(leadStatus(lead.getId())).isEqualTo("NEW");
        assertThat(output.getAll()).doesNotContain(taken).doesNotContain(InvitationCodeGenerator.format(taken));
    }
}
