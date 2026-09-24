package com.resort.platform.visits;

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
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Acompanhante de uma visita específica (D-020). O id é estável: a Fase 6 registra presença por ele. */
@Entity
@Table(name = "visit_companions")
public class VisitCompanion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false, updatable = false)
    private Visit visit;

    @Column(nullable = false, length = 120)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 11)
    private String cpf;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Relationship relationship;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VisitCompanion() {}

    VisitCompanion(Visit visit, String name, String cpf, LocalDate birthDate, Relationship relationship) {
        this.visit = visit;
        this.name = name;
        this.cpf = cpf;
        this.birthDate = birthDate;
        this.relationship = relationship;
    }

    VisitCompanion copyTo(Visit other) {
        return new VisitCompanion(other, name, cpf, birthDate, relationship);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public String getCpf() {
        return cpf;
    }

    void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    void setRelationship(Relationship relationship) {
        this.relationship = relationship;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
