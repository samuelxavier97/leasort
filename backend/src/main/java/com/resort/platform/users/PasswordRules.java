package com.resort.platform.users;

import com.resort.platform.common.ApiException;
import java.nio.charset.StandardCharsets;

/** Regras de senha (RN19, D-049): mínimo de 10 caracteres e máximo de 72 bytes, limite do BCrypt. */
public final class PasswordRules {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordRules() {}

    public static boolean isValid(String password) {
        return password != null && password.length() >= MIN_LENGTH && fitsBcrypt(password);
    }

    /** O BCrypt da Spring Security 7 rejeita entradas acima de 72 bytes em vez de truncá-las. */
    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public static void check(String password) {
        if (!isValid(password)) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "A senha deve ter no mínimo 10 caracteres e no máximo 72 bytes.");
        }
    }
}
