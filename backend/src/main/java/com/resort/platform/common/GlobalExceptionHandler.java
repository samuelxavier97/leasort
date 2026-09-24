package com.resort.platform.common;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Todo erro da API sai em Problem Details com {@code code} estável (D-033). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Constraints únicas que viram 409 quando a verificação prévia perde uma corrida. */
    private static final Map<String, String> UNIQUE_CONSTRAINTS = Map.of(
            "users_email_uk", "EMAIL_ALREADY_EXISTS",
            "prospectors_employee_code_uk", "EMPLOYEE_CODE_ALREADY_EXISTS",
            "leads_cpf_uk", "CPF_ALREADY_EXISTS",
            "visits_lead_scheduled_uk", "VISIT_ALREADY_SCHEDULED");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ProblemResponses.problem(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        String code = UNIQUE_CONSTRAINTS.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("DATA_CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemResponses.problem(HttpStatus.CONFLICT, code, "Conflito com dados existentes."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemResponses.problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemResponses.problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dados inválidos.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /**
     * Erros do próprio Spring MVC (404, 405, 413, JSON malformado...) recebem um {@code code} pelo status.
     * Para essas exceções o corpo chega nulo aqui e o Spring o monta a partir de {@link ErrorResponse}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        Object resolved = body == null && ex instanceof ErrorResponse errorResponse ? errorResponse.getBody() : body;
        if (resolved instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", switch (statusCode.value()) {
                case 400 -> "BAD_REQUEST";
                case 404 -> "NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED";
                case 413 -> "FILE_TOO_LARGE";
                case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                default -> "ERROR";
            });
        }
        return super.handleExceptionInternal(ex, resolved, headers, statusCode, request);
    }
}
