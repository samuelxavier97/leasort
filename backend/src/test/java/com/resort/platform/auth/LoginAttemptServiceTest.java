package com.resort.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService service = new LoginAttemptService(clock);

    @Test
    void blocksAfterFiveFailuresWithinTheWindow() {
        failTimes(4);
        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).isFalse();

        failTimes(1);

        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).isTrue();
    }

    @Test
    void failuresOlderThanFifteenMinutesDoNotCount() {
        failTimes(3);
        clock.advance(Duration.ofMinutes(10));
        failTimes(2);
        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).isTrue();

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).as("as 3 primeiras saíram da janela").isFalse();
    }

    @Test
    void blockEndsWhenTheWindowPasses() {
        failTimes(5);
        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).isTrue();

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).isFalse();
    }

    @Test
    void resetClearsFailures() {
        failTimes(5);

        service.reset("a@test.local", "1.1.1.1");

        assertThat(service.isBlocked("a@test.local", "1.1.1.1")).isFalse();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure("a@test.local", "1.1.1.1");
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
