package com.resort.platform;

import com.resort.platform.invitations.InvitationCodeGenerator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Códigos de convite para os testes, nunca repetidos na execução: um contador atômico com início sorteado,
 * escrito em Crockford Base32 com 10 caracteres. Nenhum código fixo no repositório. Os códigos gerados pela
 * aplicação continuam aleatórios, e ela já descarta os que existem (D-082).
 */
public final class TestCodes {

    private static final String ALPHABET = InvitationCodeGenerator.ALPHABET;
    private static final AtomicLong NEXT = new AtomicLong(ThreadLocalRandom.current().nextLong(1L << 48));

    private TestCodes() {}

    public static String unique() {
        long value = NEXT.getAndIncrement();
        char[] code = new char[InvitationCodeGenerator.LENGTH];
        for (int i = code.length - 1; i >= 0; i--) {
            code[i] = ALPHABET.charAt((int) (value & 31));
            value >>>= 5;
        }
        return new String(code);
    }
}
