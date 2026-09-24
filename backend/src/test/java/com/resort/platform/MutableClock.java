package com.resort.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio dos testes: segue o do sistema até um teste fixar um instante; resetado após cada teste. */
public class MutableClock extends Clock {

    private volatile Instant fixed;

    public void set(Instant instant) {
        this.fixed = instant;
    }

    public void advance(Duration duration) {
        this.fixed = instant().plus(duration);
    }

    public void reset() {
        this.fixed = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock parent = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return parent.withZone(other);
            }

            @Override
            public Instant instant() {
                return parent.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        Instant current = fixed;
        return current != null ? current : Instant.now();
    }
}
