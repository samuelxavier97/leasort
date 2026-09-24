package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.users.Role;
import com.resort.platform.visits.VisitRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** I28: regra 5 do CLAUDE.md e D-003 — nenhum código de convite em log, com ou sem hífen. */
@ExtendWith(OutputCaptureExtension.class)
class InvitationLogSafetyTest extends InvitationTestSupport {

    @Autowired
    InvitationRepository invitations;

    @Autowired
    VisitRepository visits;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void invitationCodesNeverReachTheLogs(CapturedOutput output) throws Exception {
        ProspectorSession me = loggedInProspector();
        ProspectorSession stranger = loggedInProspector();
        ApiClient admin = loggedIn(Role.ADMIN);
        UUID leadId = testData.lead(me.prospector()).getId();
        UUID visit = scheduledVisit(me.client(), leadId, calendar.today().plusDays(1));
        UUID first = activeOf(visit).id();

        me.client().get("/api/invitations/" + first).andExpect(status().isOk());
        me.client().get("/api/invitations/" + first + "/qr-code").andExpect(status().isOk());
        me.client().get("/api/invitations?leadId=" + leadId).andExpect(status().isOk());
        stranger.client().get("/api/invitations/" + first).andExpect(status().isNotFound());
        UUID second = UUID.fromString(json(me.client().post("/api/invitations/" + first + "/reissue", null)
                .andExpect(status().isOk())).get("id").asString());
        me.client().post("/api/invitations/" + first + "/reissue", null).andExpect(status().isConflict());
        me.client().get("/api/invitations/" + first + "/qr-code").andExpect(status().isConflict());
        admin.post("/api/invitations/" + second + "/reissue", null).andExpect(status().isOk());
        UUID moved = UUID.fromString(json(me.client().post("/api/visits/" + visit + "/reschedule",
                Map.of("scheduledDate", calendar.today().plusDays(3).toString())).andExpect(status().isOk())).get("id").asString());
        me.client().patch("/api/visits/" + moved + "/cancel", null).andExpect(status().isOk());

        // Colisão que só o índice pega: a mensagem do PostgreSQL traz o valor da chave.
        String taken = activeOrLast(moved);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> invitations.saveAndFlush(
                        new Invitation(visits.getReferenceById(moved), taken, calendar.endOfDay(calendar.today())))))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<String> codes = new ArrayList<>();
        invitationsOf(visit).forEach(row -> codes.add(row.code()));
        invitationsOf(moved).forEach(row -> codes.add(row.code()));
        assertThat(codes).hasSize(4);
        for (String code : codes) {
            assertThat(output.getAll()).doesNotContain(code).doesNotContain(InvitationCodeGenerator.format(code));
        }
    }

    private String activeOrLast(UUID visitId) {
        List<InvitationRow> rows = invitationsOf(visitId);
        return rows.get(rows.size() - 1).code();
    }
}
