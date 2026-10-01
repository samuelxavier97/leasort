package com.resort.platform.dashboard.dto;

/** Saudação pelo horário em {@code APP_TIMEZONE} (D-128): 05:00–11:59, 12:00–17:59 e 18:00–04:59. */
public enum Greeting {
    MORNING, AFTERNOON, EVENING;

    public static Greeting at(int hour) {
        if (hour >= 5 && hour < 12) {
            return MORNING;
        }
        return hour >= 12 && hour < 18 ? AFTERNOON : EVENING;
    }
}
