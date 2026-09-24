package com.resort.platform.imports;

import com.resort.platform.common.ApiException;
import com.resort.platform.common.ProblemResponses;
import com.resort.platform.imports.LeadImportService.ImportResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/leads/import")
public class LeadImportController {

    private final LeadImportService importService;

    public LeadImportController(LeadImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importLeads(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "O arquivo não tem Leads para importar.");
        }
        return importService.importCsv(file.getBytes());
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("modelo-importacao-leads.csv").build().toString())
                .body(LeadImportService.TEMPLATE.getBytes(StandardCharsets.UTF_8));
    }

    /** Relatório por linha em Problem Details (D-065). */
    @ExceptionHandler(ImportRejectedException.class)
    ResponseEntity<ProblemDetail> rejected(ImportRejectedException ex) {
        ProblemDetail problem = ProblemResponses.problem(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_REJECTED", ex.getMessage());
        problem.setProperty("totalRows", ex.getTotalRows());
        problem.setProperty("errors", ex.getErrors());
        return ResponseEntity.unprocessableContent().body(problem);
    }
}
