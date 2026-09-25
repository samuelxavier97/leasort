package com.resort.platform.exports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resort.platform.TestData;
import com.resort.platform.users.Role;
import com.resort.platform.users.User;
import com.zaxxer.hikari.HikariDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Contagem, auditoria e envio (D-101): a contagem e o envio usam a mesma conexão e a mesma transação
 * REPEATABLE READ, passada da thread da requisição para a do streaming; a conexão sempre volta ao pool.
 */
class ExportConsistencyTest extends ExportTestSupport {

    @Autowired
    ExportService exports;

    @Autowired
    DataSource dataSource;

    @Test
    void aWriteBetweenTheAuditAndTheFirstByteIsNotInTheFileAndRowsIsExact() throws Exception {
        ProspectorSession me = loggedInProspector();
        for (int i = 0; i < 3; i++) {
            testData.lead(me.prospector());
        }
        UUID admin = testData.user(Role.ADMIN).getId();

        ExportService.Prepared prepared =
                exports.prepare(ExportType.LEADS, null, null, null, me.prospector().getId(), admin, "127.0.0.1");
        // Escrita concorrente, já confirmada, depois da auditoria e antes do primeiro byte.
        UUID late = testData.lead(me.prospector()).getId();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        prepared.stream(out);

        String csv = out.toString(StandardCharsets.UTF_8);
        long dataRows = Rfc4180.parse(csv.substring(1)).size() - 1;
        long audited = json(exportAudits(admin).getFirst()).get("rows").asLong();
        assertThat(dataRows).isEqualTo(3).isEqualTo(audited);
        assertThat(csv).doesNotContain(late.toString());
        // E a exportação seguinte já vê o Lead novo.
        assertThat(download(client().login(testData.reload(testData.user(Role.ADMIN)).getEmail(), TestData.PASSWORD),
                "/api/exports/leads?prospectorId=" + me.prospector().getId()).data()).hasSize(4);
    }

    @Test
    void aClientThatAbortsMidStreamReleasesTheConnectionWithoutAnOpenTransaction() throws Exception {
        ProspectorSession me = loggedInProspector();
        UUID prospector = me.prospector().getId();
        jdbc.sql("""
                INSERT INTO leads (id, name, status, prospector_id)
                SELECT gen_random_uuid(), 'Lead Fictício Interrompido ' || n, 'NEW', :p FROM generate_series(1, 3000) AS n
                """).param("p", prospector).update();
        User adminUser = testData.user(Role.ADMIN);
        int activeBefore = activeConnections();
        long openBefore = openTransactions();

        ExportService.Prepared prepared =
                exports.prepare(ExportType.LEADS, null, null, null, prospector, adminUser.getId(), "127.0.0.1");
        AbortingStream client = new AbortingStream(64 * 1024);
        assertThatThrownBy(() -> prepared.stream(client)).isInstanceOf(IOException.class).hasMessage("cliente desistiu");

        // A interrupção aconteceu durante a leitura: bem menos que o arquivo inteiro (~300 KB) saiu.
        assertThat(client.written).isBetween(64L * 1024, 200L * 1024);
        waitUntil(() -> activeConnections() <= activeBefore);
        assertThat(activeConnections()).isLessThanOrEqualTo(activeBefore);
        assertThat(openTransactions()).isLessThanOrEqualTo(openBefore);

        Download next = download(client().login(adminUser.getEmail(), TestData.PASSWORD), "/api/exports/leads?prospectorId=" + prospector);
        assertThat(next.data()).hasSize(3_000);
        assertThat(exportAudits(adminUser.getId())).hasSize(2);
    }

    /** Sessões do banco com transação aberta e ociosa, fora a desta consulta. */
    private long openTransactions() {
        return jdbc.sql("""
                        SELECT count(*) FROM pg_stat_activity
                        WHERE datname = current_database() AND pid <> pg_backend_pid()
                          AND state IN ('idle in transaction', 'idle in transaction (aborted)')
                        """).query(Long.class).single();
    }

    private int activeConnections() {
        try {
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    /** Cliente que fecha a conexão depois de receber {@code limit} bytes. */
    private static final class AbortingStream extends OutputStream {

        private final long limit;
        private long written;

        AbortingStream(long limit) {
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (written >= limit) {
                throw new IOException("cliente desistiu");
            }
            written += length;
        }
    }
}
