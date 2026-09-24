package com.resort.platform.access;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Limite de validações em memória: 30 por minuto por usuário GATE, janela deslizante (§12.4, D-017, D-090). */
@Component
public class ValidationRateLimiter {

    static final int MAX_PER_WINDOW = 30;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<UUID, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final Clock clock;

    public ValidationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Conta a chamada e diz se ela está dentro do limite; chamadas recusadas não entram na conta. */
    public boolean tryAcquire(UUID userId) {
        boolean[] allowed = {false};
        calls.compute(userId, (key, previous) -> {
            Deque<Instant> window = previous == null ? new ArrayDeque<>() : previous;
            Instant now = clock.instant();
            Instant limit = now.minus(WINDOW);
            while (!window.isEmpty() && !window.peekFirst().isAfter(limit)) {
                window.removeFirst();
            }
            if (window.size() < MAX_PER_WINDOW) {
                window.addLast(now);
                allowed[0] = true;
            }
            return window;
        });
        return allowed[0];
    }
}
