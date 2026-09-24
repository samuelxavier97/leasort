package com.resort.platform.users;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Senhas temporárias exibidas uma única vez (D-016, D-046). Alfabeto sem caracteres ambíguos. */
@Component
public class TemporaryPasswordGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
