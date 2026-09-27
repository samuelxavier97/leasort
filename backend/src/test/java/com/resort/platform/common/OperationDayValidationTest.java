package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resort.platform.ApiClient;
import com.resort.platform.IntegrationTestSupport;
import com.resort.platform.users.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Nascimento "no futuro" é decidido pelo dia da operação ({@code APP_TIMEZONE}), não pelo fuso da JVM
 * ou do container (Fase 11). Às 22:30 de 19/09 em São Paulo já é 20/09 em UTC e 21/09 em Kiritimati;
 * o relógio fica num instante passado para que o relógio real da máquina não decida no lugar do da
 * operação.
 */
class OperationDayValidationTest extends IntegrationTestSupport {

    private static final Instant NIGHT_IN_SAO_PAULO = Instant.parse("2026-09-20T01:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 9, 20);

    private TimeZone originalZone;
    private ApiClient admin;

    @BeforeEach
    void farAwayJvmZoneAndOperationNight() throws Exception {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
        clock.set(NIGHT_IN_SAO_PAULO);
        assertThat(calendar.today()).isEqualTo(TODAY);
        admin = loggedIn(Role.ADMIN);
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void leadBirthDateFollowsTheOperationDay() throws Exception {
        admin.post("/api/leads", Map.of("name", "Lead Fictício Hoje", "birthDate", TODAY.toString()))
                .andExpect(status().isCreated());
        admin.post("/api/leads", Map.of("name", "Lead Fictício Amanhã", "birthDate", TOMORROW.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void companionBirthDateFollowsTheOperationDay() throws Exception {
        UUID leadId = testData.lead(testData.prospectorOf(testData.user(Role.PROSPECTOR))).getId();
        admin.post("/api/visits", visit(leadId, TOMORROW))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin.post("/api/visits", visit(leadId, TODAY)).andExpect(status().isCreated());
    }

    @Test
    void importedBirthDateFollowsTheOperationDay() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        String header = "nome;cpf;telefone;email;data_nascimento;codigo_prospector;observacoes\r\n";

        admin.upload("/api/leads/import", "leads.csv",
                        (header + "Lead Fictício " + token + ";;;;20/09/2026;;\r\n").getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].column").value("data_nascimento"));
        admin.upload("/api/leads/import", "leads.csv",
                        (header + "Lead Fictício " + token + ";;;;19/09/2026;;\r\n").getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
    }

    private static Map<String, Object> visit(UUID leadId, LocalDate companionBirthDate) {
        return Map.of(
                "leadId", leadId,
                "scheduledDate", TODAY.toString(),
                "companions", List.of(Map.of(
                        "name", "Acompanhante Fictício", "birthDate", companionBirthDate.toString(), "relationship", "CHILD")));
    }
}
