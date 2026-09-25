package com.resort.platform.visits;

import com.resort.platform.leads.Lead;
import com.resort.platform.prospectors.Prospector;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false, updatable = false)
    private Lead lead;

    /** Responsável no momento do agendamento; imutável (RN03, D-013). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prospector_id", nullable = false, updatable = false)
    private Prospector prospector;

    @Column(name = "scheduled_date", nullable = false, updatable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status = VisitStatus.SCHEDULED;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "host_notes", columnDefinition = "text")
    private String hostNotes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Preenchido se e somente se a visita está CANCELLED (CHECK da V10, D-098). */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 20)
    private VisitCancelReason cancelReason;

    @OneToMany(mappedBy = "visit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 50)
    private List<VisitCompanion> companions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Visit() {}

    Visit(Lead lead, Prospector prospector, LocalDate scheduledDate, String notes, String hostNotes) {
        this.lead = lead;
        this.prospector = prospector;
        this.scheduledDate = scheduledDate;
        this.notes = notes;
        this.hostNotes = hostNotes;
    }

    VisitCompanion addCompanion(String name, String cpf, LocalDate birthDate, Relationship relationship) {
        VisitCompanion companion = new VisitCompanion(this, name, cpf, birthDate, relationship);
        companions.add(companion);
        return companion;
    }

    /** Entrada registrada na Portaria (§12.3). */
    public void complete() {
        this.status = VisitStatus.COMPLETED;
    }

    /** Job noturno (§20). */
    public void markNoShow() {
        this.status = VisitStatus.NO_SHOW;
    }

    void cancel(Instant when, VisitCancelReason reason) {
        this.status = VisitStatus.CANCELLED;
        this.cancelledAt = when;
        this.cancelReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public Lead getLead() {
        return lead;
    }

    public Prospector getProspector() {
        return prospector;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    public String getHostNotes() {
        return hostNotes;
    }

    void setHostNotes(String hostNotes) {
        this.hostNotes = hostNotes;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public VisitCancelReason getCancelReason() {
        return cancelReason;
    }

    public List<VisitCompanion> getCompanions() {
        return companions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
