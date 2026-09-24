package com.resort.platform.common;

import java.util.Locale;
import java.util.regex.Pattern;

/** E-mails são armazenados em minúsculas (SPEC §8.1). */
public final class Emails {

    private static final Pattern VALID = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Emails() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String email) {
        return email != null && email.length() <= 160 && VALID.matcher(email).matches();
    }
}
