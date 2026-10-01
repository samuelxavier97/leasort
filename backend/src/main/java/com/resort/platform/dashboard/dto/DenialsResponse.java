package com.resort.platform.dashboard.dto;

import com.resort.platform.access.DenialReason;
import java.time.LocalDate;
import java.util.List;

/**
 * Negativas da Portaria no período por motivo (D-129): os cinco motivos, inclusive os de zero, do mais
 * frequente ao menos frequente (empate na ordem do enum). Cada tentativa negada conta uma vez.
 */
public record DenialsResponse(LocalDate from, LocalDate to, long total, List<Reason> reasons) {

    public record Reason(DenialReason reason, long count) {}
}
