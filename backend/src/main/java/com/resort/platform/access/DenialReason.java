package com.resort.platform.access;

/** Motivos de negativa, na ordem em que são verificados (§12.2). */
public enum DenialReason {
    INVALID_CODE, CANCELLED, ALREADY_USED, EXPIRED, WRONG_DATE
}
