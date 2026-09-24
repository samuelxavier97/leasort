package com.resort.platform.access;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização do código recebido do QR ou digitado (§12.1): maiúsculas, sem o prefixo {@code RSV:}, sem
 * espaços nem hífens, com O→0 e I/L→1. O resultado só é um código se tiver 10 caracteres Crockford.
 */
public final class AccessCodes {

    private static final String PREFIX = "RSV:";
    private static final Pattern CODE = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{10}$");

    private AccessCodes() {}

    public static String normalize(String raw) {
        String value = raw.toUpperCase(Locale.ROOT).strip();
        if (value.startsWith(PREFIX)) {
            value = value.substring(PREFIX.length());
        }
        return value.replaceAll("[\\s-]", "").replace('O', '0').replace('I', '1').replace('L', '1');
    }

    public static boolean isValid(String normalized) {
        return CODE.matcher(normalized).matches();
    }
}
