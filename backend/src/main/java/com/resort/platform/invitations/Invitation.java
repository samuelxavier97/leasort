package com.resort.platform.invitations;

import com.resort.platform.visits.Visit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Convite de uma visita (§8.6). O código fica em claro (D-003) e nunca vai para log: esta classe não
 * sobrescreve {@code toString}.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false, updatable = false)
    private Visit visit;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 10, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status = InvitationStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Invitation() {}

    Invitation(Visit visit, String code, Instant expiresAt) {
        this.visit = visit;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    /** Entrada registrada na Portaria (§12.3). */
    public void markUsed(Instant when) {
        this.status = InvitationStatus.USED;
        this.usedAt = when;
    }

    /** Job noturno (§20). */
    public void expire() {
        this.status = InvitationStatus.EXPIRED;
    }

    void cancel(Instant when) {
        this.status = InvitationStatus.CANCELLED;
        this.cancelledAt = when;
    }

    public UUID getId() {
        return id;
    }

    public Visit getVisit() {
        return visit;
    }

    public String getCode() {
        return code;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
