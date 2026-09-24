package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.resort.platform.TestCodes;
import com.resort.platform.invitations.InvitationExpiryJob;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** G26 e G27: regra 5 e D-044 — o código só fica em access_records.attempted_code. */
@ExtendWith(OutputCaptureExtension.class)
class AccessLogSafetyTest extends AccessTestSupport {

    @Autowired
    InvitationExpiryJob job;

    @Test
    void codesNeverReachLogsNorAudit(CapturedOutput output) throws Exception {
        ProspectorSession me = loggedInProspector();
        GateSession gate = gate();
        LocalDate day = calendar.today();
        Booking valid = book(me, day, 1);
        Booking future = book(me, day.plusDays(2), 0);
        Booking expiring = book(me, day, 0);
        String unknown = TestCodes.unique();
        String typed = ("rsv: " + valid.code().substring(0, 5) + "-" + valid.code().substring(5)).toLowerCase();

        validate(gate.client(), typed).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        validate(gate.client(), "RSV:" + future.code()).andExpect(jsonPath("$.denialReason").value("WRONG_DATE"));
        validate(gate.client(), unknown).andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        validate(gate.client(), "rsv:" + unknown.substring(0, 7) + "!").andExpect(jsonPath("$.denialReason").value("INVALID_CODE"));
        register(gate.client(), valid.invitationId(), valid.companionIds()).andExpect(jsonPath("$.result").value("AUTHORIZED"));
        register(gate.client(), valid.invitationId(), List.of()).andExpect(jsonPath("$.denialReason").value("ALREADY_USED"));
        validate(gate.client(), valid.code()).andExpect(jsonPath("$.denialReason").value("ALREADY_USED"));
        clock.set(calendar.endOfDay(day).plusSeconds(60));
        job.run();
        clock.reset();

        List<String> secrets = new ArrayList<>(List.of(typed, unknown, unknown.substring(0, 7)));
        for (Booking booking : List.of(valid, future, expiring)) {
            secrets.add(booking.code());
            secrets.add(booking.code().substring(0, 5) + "-" + booking.code().substring(5));
        }
        String audit = String.join("\n", jdbc.sql("SELECT coalesce(metadata::text, '') FROM audit_logs").query(String.class).list());
        for (String secret : secrets) {
            assertThat(output.getAll()).as("log").doesNotContain(secret).doesNotContainIgnoringCase(secret);
            assertThat(audit).as("auditoria").doesNotContain(secret);
        }

        // G27: o código tentado fica só em attempted_code, normalizado.
        assertThat(accessRowsBy(gate.user().getId())).extracting(AccessRow::attemptedCode)
                .containsExactly(future.code(), unknown, unknown.substring(0, 7) + "!", valid.code(), valid.code(), valid.code());
    }
}
