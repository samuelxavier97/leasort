package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** G3: 30 validações por minuto por usuário GATE, janela deslizante (§12.4, D-017). */
class ValidationRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final ValidationRateLimiter limiter = new ValidationRateLimiter(clock);

    @Test
    void thirtyPerMinutePerUserWithASlidingWindow() {
        clock.set(Instant.parse("2026-03-10T12:00:00Z"));
        UUID gate = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryAcquire(gate)).as("chamada %d", i + 1).isTrue();
            clock.advance(Duration.ofSeconds(1));
        }
        assertThat(limiter.tryAcquire(gate)).isFalse();
        assertThat(limiter.tryAcquire(other)).as("cada usuário tem o seu contador").isTrue();

        // A primeira chamada foi às 12:00:00; às 12:01:00 ela sai da janela e libera uma vaga.
        clock.set(Instant.parse("2026-03-10T12:01:00Z"));
        assertThat(limiter.tryAcquire(gate)).isTrue();
        assertThat(limiter.tryAcquire(gate)).isFalse();

        clock.set(Instant.parse("2026-03-10T12:02:30Z"));
        assertThat(limiter.tryAcquire(gate)).isTrue();
    }
}
