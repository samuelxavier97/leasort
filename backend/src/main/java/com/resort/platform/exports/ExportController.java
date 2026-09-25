package com.resort.platform.exports;

import com.resort.platform.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Exportações (§11, §18): só ADMIN. O id do ADMIN e o IP são capturados aqui, na thread da requisição; a
 * auditoria é confirmada antes de o corpo começar a ser escrito na thread assíncrona (D-101).
 */
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{file:leads|visits|companions|access}")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable String file,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID prospectorId) {
        ExportType type = ExportType.valueOf(file.toUpperCase());
        ExportService.Prepared export =
                exportService.prepare(type, from, to, status, prospectorId, user.id(), request.getRemoteAddr());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(export::stream);
    }
}
