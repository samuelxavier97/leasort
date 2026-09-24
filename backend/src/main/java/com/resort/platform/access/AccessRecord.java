package com.resort.platform.access;

import com.resort.platform.invitations.Invitation;
import com.resort.platform.users.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tentativa de acesso na Portaria (§8.7), autorizada ou negada. {@code attemptedCode} é o único lugar em
 * que o código tentado é guardado (D-044): esta classe não sobrescreve {@code toString}.
 */
@Entity
@Table(name = "access_records")
public class AccessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id", updatable = false)
    private Invitation invitation;

    @Column(name = "attempted_code", nullable = false, length = 20, updatable = false)
    private String attemptedCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validated_by_user_id", nullable = false, updatable = false)
    private User validatedBy;

    @Column(nullable = false, length = 40, updatable = false)
    private String gate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccessResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "denial_reason", length = 20, updatable = false)
    private DenialReason denialReason;

    @Column(name = "entry_at", updatable = false)
    private Instant entryAt;

    /** Acompanhantes que efetivamente entraram (§8.8, D-021). */
    @ElementCollection
    @CollectionTable(name = "access_record_companions", joinColumns = @JoinColumn(name = "access_record_id"))
    @Column(name = "companion_id", nullable = false)
    private Set<UUID> presentCompanionIds = new HashSet<>();

    /** Pelo Clock da aplicação, o mesmo de "hoje" e de {@code entry_at} (D-092). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccessRecord() {}

    private AccessRecord(
            Invitation invitation, String attemptedCode, User validatedBy, String gate, AccessResult result, Instant createdAt) {
        this.invitation = invitation;
        this.createdAt = createdAt;
        this.attemptedCode = attemptedCode;
        this.validatedBy = validatedBy;
        this.gate = gate;
        this.result = result;
    }

    static AccessRecord denied(
            Invitation invitation, String attemptedCode, User validatedBy, String gate, DenialReason reason, Instant at) {
        AccessRecord record = new AccessRecord(invitation, attemptedCode, validatedBy, gate, AccessResult.DENIED, at);
        record.denialReason = reason;
        return record;
    }

    static AccessRecord authorized(Invitation invitation, User validatedBy, String gate, Instant entryAt, Set<UUID> companions) {
        AccessRecord record =
                new AccessRecord(invitation, invitation.getCode(), validatedBy, gate, AccessResult.AUTHORIZED, entryAt);
        record.entryAt = entryAt;
        record.presentCompanionIds.addAll(companions);
        return record;
    }

    public UUID getId() {
        return id;
    }

    public Instant getEntryAt() {
        return entryAt;
    }

    public Set<UUID> getPresentCompanionIds() {
        return Collections.unmodifiableSet(presentCompanionIds);
    }
}
