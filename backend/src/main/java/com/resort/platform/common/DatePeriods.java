package com.resort.platform.common;

import java.time.LocalDate;

/**
 * Regras de período por datas de calendário em {@code APP_TIMEZONE}, os dois extremos incluídos: as duas datas
 * ou nenhuma, início não posterior ao fim e, quando houver, limite de dias (D-098, D-101, D-102).
 */
public final class DatePeriods {

    private DatePeriods() {}

    /** Valida o par; devolve {@code false} quando nenhuma data foi informada. */
    public static boolean validate(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return false;
        }
        if (from == null || to == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Informe as duas datas do período.");
        }
        if (from.isAfter(to)) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A data inicial não pode ser posterior à final.");
        }
        return true;
    }

    public static void requireAtMost(LocalDate from, LocalDate to, int maxDays) {
        if (to.toEpochDay() - from.toEpochDay() + 1 > maxDays) {
            throw ApiException.badRequest("PERIOD_TOO_LONG", "O período pode ter no máximo " + maxDays + " dias.");
        }
    }
}
