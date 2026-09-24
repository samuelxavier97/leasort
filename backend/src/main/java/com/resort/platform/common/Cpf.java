package com.resort.platform.common;

/**
 * CPF só com dígitos, validado pelos dígitos verificadores (RN14). Nunca registrar o valor em log.
 */
public final class Cpf {

    private Cpf() {}

    /** Remove pontuação e espaços; devolve {@code null} para entrada vazia. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[\\s.\\-]", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Valida um CPF já normalizado (11 dígitos, sem sequência repetida, dígitos verificadores). */
    public static boolean isValid(String digits) {
        if (digits == null || !digits.matches("\\d{11}") || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 9) == digits.charAt(9) - '0' && checkDigit(digits, 10) == digits.charAt(10) - '0';
    }

    /** {@code 12345678901} → {@code 123.456.789-01}. */
    public static String format(String digits) {
        if (digits == null) {
            return null;
        }
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-"
                + digits.substring(9);
    }

    /** Máscara da SPEC §15: {@code ***.456.789-**}. */
    public static String mask(String digits) {
        if (digits == null) {
            return null;
        }
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
