package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resort.platform.MutableClock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void todayFollowsTheOperationTimezoneAcrossMidnight() {
        BusinessCalendar calendar = calendar("America/Sao_Paulo");

        clock.set(Instant.parse("2026-03-10T02:59:59Z"));
        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 3, 9));

        clock.set(Instant.parse("2026-03-10T03:00:00Z"));
        assertThat(calendar.today()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void zoneComesFromConfiguration() {
        clock.set(Instant.parse("2026-03-10T16:00:00Z"));

        assertThat(calendar("Asia/Tokyo").today()).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(calendar("America/Sao_Paulo").today()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    /** I3: fim do dia como primeiro instante do dia seguinte no fuso da operação (D-085). */
    @Test
    void endOfDayIsTheFirstInstantOfTheNextDayInTheOperationTimezone() {
        assertThat(calendar("America/Sao_Paulo").endOfDay(LocalDate.of(2026, 3, 9)))
                .isEqualTo(Instant.parse("2026-03-10T03:00:00Z"));
        // Dia em que começa o horário de verão em Nova York (08/03/2026): 00:00 do dia 09 já é UTC-4.
        assertThat(calendar("America/New_York").endOfDay(LocalDate.of(2026, 3, 8)))
                .isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
        assertThat(calendar("America/New_York").endOfDay(LocalDate.of(2026, 3, 7)))
                .isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
    }

    @Test
    void invalidZoneFailsFast() {
        assertThatThrownBy(() -> calendar("Lua/Base")).isInstanceOf(DateTimeException.class);
    }

    private BusinessCalendar calendar(String zone) {
        return new BusinessCalendar(clock, new AppProperties(null, zone, 6, "PRINCIPAL"));
    }
}
