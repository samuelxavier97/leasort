package com.resort.platform;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CPFs fictícios gerados na hora, com dígitos verificadores válidos. Nenhum dado real no repositório.
 * Os 9 primeiros dígitos vêm de um contador atômico com início sorteado por execução: nunca se repetem
 * durante a suíte inteira, que compartilha o banco e tem CPF único entre Leads (RN14). O início fica
 * abaixo de 900.000.000, e a suíte usa bem menos de 100 milhões de valores, então nunca passa de 9 dígitos.
 */
public final class FakeCpf {

    private static final AtomicLong NEXT_BASE =
            new AtomicLong(100_000_000L + ThreadLocalRandom.current().nextLong(800_000_000L));

    private FakeCpf() {}

    public static String generate() {
        StringBuilder digits = new StringBuilder();
        do {
            digits.setLength(0);
            digits.append(String.format("%09d", NEXT_BASE.getAndIncrement()));
        } while (digits.chars().distinct().count() == 1);
        digits.append(checkDigit(digits, 9));
        digits.append(checkDigit(digits, 10));
        return digits.toString();
    }

    /** Mesmo CPF com o último dígito trocado: sempre inválido. */
    public static String invalid() {
        String valid = generate();
        char last = valid.charAt(10);
        return valid.substring(0, 10) + (char) ('0' + (last - '0' + 1) % 10);
    }

    public static String formatted(String digits) {
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-"
                + digits.substring(9);
    }

    private static int checkDigit(CharSequence digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
