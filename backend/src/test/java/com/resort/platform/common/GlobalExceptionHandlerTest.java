package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Corridas que só a constraint do banco pega viram 409 com code estável, sem expor o valor. */
class GlobalExceptionHandlerTest {

    @ParameterizedTest
    @CsvSource({
        "visits_lead_scheduled_uk, VISIT_ALREADY_SCHEDULED",
        "invitations_code_uk, INVITATION_CODE_CONFLICT",
        "leads_cpf_uk, CPF_ALREADY_EXISTS",
        "users_email_uk, EMAIL_ALREADY_EXISTS",
        "prospectors_employee_code_uk, EMPLOYEE_CODE_ALREADY_EXISTS",
        "outra_constraint, DATA_CONFLICT"
    })
    void uniqueConstraintBecomesConflictWithCode(String constraint, String code) {
        var cause = new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + constraint
                + "\" Detail: Key (x)=(valor-sensivel) already exists.");

        ResponseEntity<ProblemDetail> response =
                new GlobalExceptionHandler().handleDataIntegrity(new DataIntegrityViolationException("x", cause));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getProperties()).containsEntry("code", code);
        assertThat(response.getBody().getDetail()).doesNotContain("valor-sensivel");
    }
}
