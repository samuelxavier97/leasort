package com.resort.platform.invitations;

import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Código do convite (RN07, D-001): 10 caracteres do alfabeto Crockford Base32, cerca de 50 bits. Em
 * produção o {@link RandomGenerator} é um {@code SecureRandom} (D-082).
 */
@Component
public class InvitationCodeGenerator {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 10;

    private final RandomGenerator random;

    public InvitationCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }

    /** {@code ABCDEFGHJK} → {@code ABCDE-FGHJK} (§13). */
    public static String format(String code) {
        return code.substring(0, 5) + "-" + code.substring(5);
    }
}
