package com.resort.platform.exports;

import com.resort.platform.audit.AuditAction;
import com.resort.platform.audit.AuditService;
import com.resort.platform.common.ApiException;
import com.resort.platform.common.BusinessCalendar;
import com.resort.platform.common.CsvWriter;
import com.resort.platform.common.DatePeriods;
import com.resort.platform.prospectors.ProspectorRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exportações CSV (§18, D-101), só ADMIN. Em duas etapas:
 * <ol>
 *   <li>{@link #prepare}, na thread da requisição: valida os filtros, abre uma conexão própria em REPEATABLE READ
 *       somente leitura, conta as linhas e grava {@code EXPORT_GENERATED} numa transação própria, confirmada antes
 *       do primeiro byte. Se a auditoria falhar, a conexão é fechada e a resposta é erro: nenhuma linha sai.</li>
 *   <li>{@link Prepared#stream}, na thread assíncrona: lê as linhas do mesmo instantâneo da contagem, com cursor
 *       ({@code fetchSize}), e escreve direto na resposta. A conexão fica presa durante o download (aceito).</li>
 * </ol>
 */
@Service
public class ExportService {

    static final int FETCH_SIZE = 500;
    static final String ENTITY_TYPE = "EXPORT";
    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final DataSource dataSource;
    private final ExportQueries queries;
    private final AuditService audit;
    private final ProspectorRepository prospectors;
    private final BusinessCalendar calendar;
    private final TransactionTemplate auditTransaction;

    public ExportService(DataSource dataSource, ExportQueries queries, AuditService audit, ProspectorRepository prospectors,
            BusinessCalendar calendar, PlatformTransactionManager transactionManager) {
        this.dataSource = dataSource;
        this.queries = queries;
        this.audit = audit;
        this.prospectors = prospectors;
        this.calendar = calendar;
        this.auditTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Exportação pronta para enviar: a auditoria já está confirmada. */
    public final class Prepared {

        private final Connection connection;
        private final NamedParameterJdbcTemplate jdbc;
        private final ExportType type;
        private final ExportFilters filters;
        private final String fileName;

        private Prepared(Connection connection, NamedParameterJdbcTemplate jdbc, ExportType type, ExportFilters filters,
                String fileName) {
            this.connection = connection;
            this.jdbc = jdbc;
            this.type = type;
            this.filters = filters;
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }

        /** Escreve o arquivo e sempre devolve a conexão ao pool, mesmo se o cliente desistir no meio. */
        public void stream(OutputStream output) throws IOException {
            try {
                queries.write(jdbc, type, filters, new CsvWriter(output));
            } finally {
                release(connection);
            }
        }
    }

    public Prepared prepare(ExportType type, LocalDate from, LocalDate to, String status, UUID prospectorId,
            UUID adminId, String ip) {
        ExportFilters filters = filters(type, from, to, status, prospectorId);
        Connection connection = open();
        try {
            NamedParameterJdbcTemplate jdbc = template(connection);
            long rows = queries.count(jdbc, type, filters);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("type", type.name());
            metadata.put("filters", filters.asMetadata());
            metadata.put("rows", rows);
            auditTransaction.executeWithoutResult(tx ->
                    audit.recordFrom(adminId, ip, AuditAction.EXPORT_GENERATED, ENTITY_TYPE, null, metadata));
            String fileName = type.fileName() + "-" + calendar.today() + ".csv";
            return new Prepared(connection, jdbc, type, filters, fileName);
        } catch (RuntimeException e) {
            release(connection);
            throw e;
        }
    }

    private ExportFilters filters(ExportType type, LocalDate from, LocalDate to, String status, UUID prospectorId) {
        DatePeriods.validate(from, to);
        if (prospectorId != null && !prospectors.existsById(prospectorId)) {
            throw ApiException.notFound("PROSPECTOR_NOT_FOUND", "Prospector não encontrado.");
        }
        return new ExportFilters(from, to, status(type, status), prospectorId);
    }

    /** O status de cada arquivo (D-101): do Lead, da visita ou o resultado do acesso. */
    static String status(ExportType type, String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        var allowed = switch (type) {
            case LEADS -> ExportLabels.LEAD_STATUS.keySet();
            case VISITS, COMPANIONS -> ExportLabels.VISIT_STATUS.keySet();
            case ACCESS -> ExportLabels.ACCESS_RESULT.keySet();
        };
        if (!allowed.contains(status)) {
            throw ApiException.badRequest("BAD_REQUEST", "Status inválido para este arquivo.");
        }
        return status;
    }

    private Connection open() {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível abrir a conexão da exportação.", e);
        }
    }

    private static NamedParameterJdbcTemplate template(Connection connection) {
        JdbcTemplate template = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        // O PostgreSQL só usa cursor com autocommit desligado e fetchSize definido: as linhas chegam aos poucos.
        template.setFetchSize(FETCH_SIZE);
        return new NamedParameterJdbcTemplate(template);
    }

    private static void release(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            log.warn("Falha ao encerrar a transação da exportação: {}", e.getClass().getSimpleName());
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Falha ao devolver a conexão da exportação: {}", e.getClass().getSimpleName());
        }
    }
}
