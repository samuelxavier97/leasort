package com.resort.platform.dashboard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Visita de hoje no quadro "Hoje na sala de vendas" (D-128). Só os dados que a tela de Chegadas já
 * mostra (§15, D-095). Sem entrada, {@code arrivedAt} e {@code companionsPresent} são nulos; a visita não
 * tem horário marcado, então nenhum é inventado.
 */
public record TodayVisit(
        UUID visitId,
        String leadName,
        String prospectorName,
        int companionsCount,
        Integer companionsPresent,
        Instant arrivedAt) {}
