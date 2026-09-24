package com.resort.platform.imports;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.Cpf;
import com.resort.platform.common.CsvReader;
import com.resort.platform.common.CsvReader.Row;
import com.resort.platform.common.Emails;
import com.resort.platform.leads.Lead;
import com.resort.platform.leads.LeadRepository;
import com.resort.platform.prospectors.Prospector;
import com.resort.platform.prospectors.ProspectorRepository;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importação de Leads por CSV (SPEC §17, D-018, D-065): valida todas as linhas e só grava se nenhuma
 * tiver erro, numa única transação. O relatório aponta linha, coluna e código, sem repetir valores.
 */
@Service
public class LeadImportService {

    static final List<String> COLUMNS =
            List.of("nome", "cpf", "telefone", "email", "data_nascimento", "codigo_prospector", "observacoes");
    static final int MAX_ROWS = 5000;
    static final String TEMPLATE = "﻿" + String.join(";", COLUMNS) + "\r\n";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\s-]{8,20}$");

    private final LeadRepository leads;
    private final ProspectorRepository prospectors;
    private final AuditService audit;

    public LeadImportService(LeadRepository leads, ProspectorRepository prospectors, AuditService audit) {
        this.leads = leads;
        this.prospectors = prospectors;
        this.audit = audit;
    }

    public record ImportResult(int imported, int totalRows) {}

    @Transactional
    public ImportResult importCsv(byte[] content) {
        List<Row> rows = CsvReader.parse(decode(content), ';');
        if (rows.isEmpty()) {
            throw emptyFile();
        }
        Map<String, Integer> columns = columnIndex(rows.getFirst());
        List<Row> data = rows.subList(1, rows.size());
        if (data.isEmpty()) {
            throw emptyFile();
        }
        if (data.size() > MAX_ROWS) {
            throw ApiException.badRequest("TOO_MANY_ROWS",
                    "O arquivo tem " + data.size() + " linhas; o limite é " + MAX_ROWS + " por arquivo.");
        }

        Validation validation = new Validation(columns, prospectorsByCode(data, columns), existingCpfs(data, columns));
        List<Lead> parsed = new ArrayList<>();
        for (Row row : data) {
            Lead lead = validation.validate(row);
            if (lead != null) {
                parsed.add(lead);
            }
        }
        if (!validation.errors.isEmpty()) {
            throw new ImportRejectedException(data.size(), validation.errors);
        }

        leads.saveAll(parsed);
        long assigned = parsed.stream().filter(lead -> lead.getProspector() != null).count();
        audit.record(AuditAction.LEAD_IMPORTED, "LEAD", null, Map.of("count", parsed.size(), "assignedCount", assigned));
        return new ImportResult(parsed.size(), data.size());
    }

    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("INVALID_ENCODING",
                    "O arquivo não está em UTF-8. No Excel, salve como \"CSV UTF-8 (delimitado por vírgulas)\".");
        }
    }

    /** Cabeçalho obrigatório com as 7 colunas, em qualquer ordem, sem colunas extras (D-065). */
    private static Map<String, Integer> columnIndex(Row header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.fields().size(); i++) {
            String name = header.fields().get(i).trim().toLowerCase(Locale.ROOT);
            if (!COLUMNS.contains(name) || index.put(name, i) != null) {
                throw invalidHeader();
            }
        }
        if (index.size() != COLUMNS.size()) {
            throw invalidHeader();
        }
        return index;
    }

    private Map<String, Prospector> prospectorsByCode(List<Row> data, Map<String, Integer> columns) {
        Set<String> codes = values(data, columns.get("codigo_prospector"));
        return codes.isEmpty()
                ? Map.of()
                : prospectors.findByEmployeeCodeIn(codes).stream()
                        .collect(Collectors.toMap(Prospector::getEmployeeCode, Function.identity()));
    }

    private Set<String> existingCpfs(List<Row> data, Map<String, Integer> columns) {
        Set<String> cpfs = values(data, columns.get("cpf")).stream()
                .map(Cpf::normalize)
                .filter(Cpf::isValid)
                .collect(Collectors.toSet());
        return cpfs.isEmpty() ? Set.of() : new HashSet<>(leads.findExistingCpfs(cpfs));
    }

    private static Set<String> values(List<Row> data, int column) {
        return data.stream()
                .filter(row -> row.fields().size() == COLUMNS.size())
                .map(row -> row.fields().get(column).trim())
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private static ApiException emptyFile() {
        return ApiException.badRequest("EMPTY_FILE", "O arquivo não tem Leads para importar.");
    }

    private static ApiException invalidHeader() {
        return ApiException.badRequest("INVALID_HEADER",
                "Cabeçalho inválido. Use as colunas: " + String.join(";", COLUMNS) + ".");
    }

    /** Validação de todas as linhas, acumulando os erros. */
    private static final class Validation {

        private final Map<String, Integer> columns;
        private final Map<String, Prospector> prospectorsByCode;
        private final Set<String> existingCpfs;
        private final Map<String, Integer> cpfFirstLine = new HashMap<>();
        private final List<ImportError> errors = new ArrayList<>();

        Validation(Map<String, Integer> columns, Map<String, Prospector> prospectorsByCode, Set<String> existingCpfs) {
            this.columns = columns;
            this.prospectorsByCode = prospectorsByCode;
            this.existingCpfs = existingCpfs;
        }

        /** Devolve o Lead pronto para gravar, ou {@code null} se a linha tem erro. */
        Lead validate(Row row) {
            int line = row.line();
            if (row.fields().size() != COLUMNS.size()) {
                error(line, null, "COLUMN_COUNT_MISMATCH",
                        "A linha tem " + row.fields().size() + " colunas; esperado " + COLUMNS.size() + ".");
                return null;
            }
            int errorsBefore = errors.size();

            String name = field(row, "nome");
            if (name.isEmpty()) {
                error(line, "nome", "REQUIRED", "Nome obrigatório.");
            } else if (name.length() > 120) {
                error(line, "nome", "TOO_LONG", "Nome com mais de 120 caracteres.");
            }

            String cpf = Cpf.normalize(field(row, "cpf"));
            if (cpf != null) {
                if (!Cpf.isValid(cpf)) {
                    error(line, "cpf", "CPF_INVALID", "CPF inválido.");
                } else if (existingCpfs.contains(cpf)) {
                    error(line, "cpf", "CPF_ALREADY_EXISTS", "Já existe um Lead com este CPF.");
                } else if (cpfFirstLine.containsKey(cpf)) {
                    error(line, "cpf", "CPF_DUPLICATED_IN_FILE",
                            "CPF repetido; já aparece na linha " + cpfFirstLine.get(cpf) + ".");
                } else {
                    cpfFirstLine.put(cpf, line);
                }
            }

            String phone = field(row, "telefone");
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                error(line, "telefone", "PHONE_INVALID", "Telefone inválido.");
            }

            String email = Emails.normalize(field(row, "email"));
            if (!email.isEmpty() && !Emails.isValid(email)) {
                error(line, "email", "EMAIL_INVALID", "E-mail inválido.");
            }

            LocalDate birthDate = null;
            String rawDate = field(row, "data_nascimento");
            if (!rawDate.isEmpty()) {
                try {
                    birthDate = LocalDate.parse(rawDate, DATE);
                    if (birthDate.isAfter(LocalDate.now()) || birthDate.isBefore(MIN_BIRTH_DATE)) {
                        error(line, "data_nascimento", "DATE_INVALID", "Data de nascimento fora do intervalo aceito.");
                    }
                } catch (DateTimeParseException e) {
                    error(line, "data_nascimento", "DATE_INVALID", "Data inválida; use DD/MM/AAAA.");
                }
            }

            Prospector prospector = null;
            String code = field(row, "codigo_prospector");
            if (!code.isEmpty()) {
                prospector = prospectorsByCode.get(code);
                if (prospector == null) {
                    error(line, "codigo_prospector", "PROSPECTOR_NOT_FOUND", "Prospector não encontrado.");
                } else if (!prospector.getUser().isActive()) {
                    error(line, "codigo_prospector", "PROSPECTOR_INACTIVE", "O Prospector está inativo.");
                }
            }

            String notes = field(row, "observacoes");
            if (notes.length() > 2000) {
                error(line, "observacoes", "TOO_LONG", "Observações com mais de 2000 caracteres.");
            }

            if (errors.size() > errorsBefore) {
                return null;
            }
            Lead lead = new Lead(name);
            lead.setCpf(cpf);
            lead.setPhone(phone.isEmpty() ? null : phone);
            lead.setEmail(email.isEmpty() ? null : email);
            lead.setBirthDate(birthDate);
            lead.setNotes(notes.isEmpty() ? null : notes);
            lead.setProspector(prospector);
            return lead;
        }

        private String field(Row row, String column) {
            return row.fields().get(columns.get(column)).trim();
        }

        private void error(int line, String column, String code, String message) {
            errors.add(new ImportError(line, column, code, message));
        }
    }
}
