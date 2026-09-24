package com.resort.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resort.platform.IntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AuditLogImmutabilityTest extends IntegrationTestSupport {

    @Autowired
    AuditService audit;

    @Autowired
    TransactionTemplate transaction;

    UUID entityId;
    UUID auditId;

    @BeforeEach
    void insertAuditRow() {
        entityId = UUID.randomUUID();
        transaction.executeWithoutResult(status ->
                audit.recordAs(null, AuditAction.EXPORT_GENERATED, "TEST", entityId, Map.of("type", "leads")));
        auditId = jdbc.sql("SELECT id FROM audit_logs WHERE entity_id = :entityId")
                .param("entityId", entityId)
                .query(UUID.class)
                .single();
    }

    @Test
    void updateIsRejectedAndRowStaysIntact() {
        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_logs SET action = 'LOGIN' WHERE id = :id")
                        .param("id", auditId)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_logs is append-only (UPDATE)");
        assertThat(jdbc.sql("SELECT action FROM audit_logs WHERE id = :id").param("id", auditId)
                        .query(String.class).single())
                .isEqualTo("EXPORT_GENERATED");
    }

    @Test
    void deleteIsRejectedAndRowRemains() {
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_logs WHERE id = :id").param("id", auditId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_logs is append-only (DELETE)");
        assertThat(countByEntity(entityId)).isEqualTo(1);
    }

    @Test
    void truncateIsRejected() {
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit_logs").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_logs is append-only (TRUNCATE)");
        assertThat(countByEntity(entityId)).isEqualTo(1);
    }

    @Test
    void recordingOutsideATransactionFails() {
        assertThatThrownBy(() -> audit.recordAs(null, AuditAction.EXPORT_GENERATED, "TEST", UUID.randomUUID(), null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void auditIsRolledBackWithTheBusinessTransaction() {
        UUID rolledBackEntity = UUID.randomUUID();
        transaction.executeWithoutResult(status -> {
            audit.recordAs(null, AuditAction.EXPORT_GENERATED, "TEST", rolledBackEntity, null);
            status.setRollbackOnly();
        });
        assertThat(countByEntity(rolledBackEntity)).isZero();
    }

    private long countByEntity(UUID id) {
        return jdbc.sql("SELECT count(*) FROM audit_logs WHERE entity_id = :id").param("id", id)
                .query(Long.class).single();
    }
}
