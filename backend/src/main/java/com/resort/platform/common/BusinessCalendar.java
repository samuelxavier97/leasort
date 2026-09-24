package com.resort.platform.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * "Hoje" da operação, sempre no fuso {@code APP_TIMEZONE} e nunca no do servidor (SPEC §10 RN05, D-074).
 * Um fuso inválido impede a inicialização.
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, AppProperties properties) {
        this.clock = clock;
        this.zone = ZoneId.of(properties.timezone());
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZoneId zone() {
        return zone;
    }
}
