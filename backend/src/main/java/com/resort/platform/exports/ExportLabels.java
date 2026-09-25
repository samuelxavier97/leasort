package com.resort.platform.exports;

import java.util.Map;

/** Rótulos em português iguais aos da interface, para status, motivos e parentesco nos arquivos. */
final class ExportLabels {

    static final Map<String, String> LEAD_STATUS = Map.of(
            "NEW", "Novo", "CONTACTED", "Contatado", "VISIT_SCHEDULED", "Visita agendada",
            "VISITED", "Visitou", "CANCELLED", "Descartado");

    static final Map<String, String> VISIT_STATUS = Map.of(
            "SCHEDULED", "Agendada", "COMPLETED", "Realizada", "CANCELLED", "Cancelada", "NO_SHOW", "Não compareceu");

    static final Map<String, String> CANCEL_REASON = Map.of(
            "CANCELLED_BY_USER", "Cancelada pelo usuário", "RESCHEDULED", "Remarcação", "LEAD_DISCARDED", "Lead descartado");

    static final Map<String, String> RELATIONSHIP = Map.of(
            "SPOUSE", "Cônjuge", "CHILD", "Filho(a)", "FATHER", "Pai", "MOTHER", "Mãe", "SIBLING", "Irmão(ã)",
            "GRANDPARENT", "Avô/Avó", "GRANDCHILD", "Neto(a)", "FRIEND", "Amigo(a)", "OTHER", "Outro");

    static final Map<String, String> ACCESS_RESULT = Map.of("AUTHORIZED", "Liberado", "DENIED", "Negado");

    static final Map<String, String> DENIAL_REASON = Map.of(
            "INVALID_CODE", "Código inválido", "CANCELLED", "Convite cancelado", "ALREADY_USED", "Convite já utilizado",
            "EXPIRED", "Convite expirado", "WRONG_DATE", "Fora da data");

    private ExportLabels() {}

    static String of(Map<String, String> labels, String value) {
        return value == null ? null : labels.getOrDefault(value, value);
    }
}
