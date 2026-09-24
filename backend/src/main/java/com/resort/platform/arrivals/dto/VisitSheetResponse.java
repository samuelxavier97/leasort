package com.resort.platform.arrivals.dto;

import com.resort.platform.visits.Relationship;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ficha da visita (§15, §16.6, D-022, D-096). Idades na data da visita; nunca CPF, e-mail, data de
 * nascimento, {@code leads.notes} nem {@code visits.notes}: os tipos não têm onde colocá-los.
 */
public record VisitSheetResponse(
        UUID visitId,
        LocalDate scheduledDate,
        Instant entryAt,
        LeadInfo lead,
        String prospectorName,
        List<PresentCompanion> presentCompanions,
        List<AbsentCompanion> absentCompanions,
        String hostNotes) {

    public record LeadInfo(String name, Integer age, String phone) {}

    public record PresentCompanion(String name, Relationship relationship, int age) {}

    public record AbsentCompanion(String name, Relationship relationship) {}
}
