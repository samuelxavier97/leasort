package com.resort.platform;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Identificadores únicos para dados de teste, derivados de um contador atômico em vez de sorteio: nunca
 * se repetem durante a execução da suíte, que compartilha um único banco. Largura fixa, para que um valor
 * nunca seja prefixo de outro nas buscas por trecho (ex.: "Lead Fictício 00000001" e "...00000012").
 */
public final class TestSequence {

    private static final AtomicLong NEXT = new AtomicLong(1);

    private TestSequence() {}

    /** {@code prefix} seguido de 8 dígitos, único na execução. */
    public static String next(String prefix) {
        return prefix + String.format("%08d", NEXT.getAndIncrement());
    }
}
