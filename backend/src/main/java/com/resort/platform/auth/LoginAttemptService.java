package com.resort.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Limite de login em memória: 5 falhas por e-mail + IP em 15 minutos (D-017). */
@Component
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String email, String ip) {
        boolean[] blocked = {false};
        failures.computeIfPresent(key(email, ip), (key, attempts) -> {
            prune(attempts);
            blocked[0] = attempts.size() >= MAX_FAILURES;
            return attempts.isEmpty() ? null : attempts;
        });
        return blocked[0];
    }

    public void recordFailure(String email, String ip) {
        failures.compute(key(email, ip), (key, attempts) -> {
            Deque<Instant> result = attempts == null ? new ArrayDeque<>() : attempts;
            prune(result);
            result.addLast(clock.instant());
            return result;
        });
    }

    public void reset(String email, String ip) {
        failures.remove(key(email, ip));
    }

    private void prune(Deque<Instant> attempts) {
        Instant limit = clock.instant().minus(WINDOW);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(limit)) {
            attempts.removeFirst();
        }
    }

    private static String key(String email, String ip) {
        return email + "|" + ip;
    }
}
