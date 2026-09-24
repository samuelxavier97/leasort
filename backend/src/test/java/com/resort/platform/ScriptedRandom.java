package com.resort.platform;

import com.resort.platform.invitations.InvitationCodeGenerator;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/**
 * Aleatoriedade dos testes: segue um SecureRandom até um teste carregar códigos de convite; resetado
 * após cada teste, como o MutableClock. Permite provocar colisão de código de forma determinística.
 */
public class ScriptedRandom implements RandomGenerator {

    private final SecureRandom delegate = new SecureRandom();
    private final Deque<Integer> script = new ArrayDeque<>();

    /** Os próximos códigos gerados serão exatamente estes, na ordem. */
    public synchronized void enqueueCodes(String... codes) {
        for (String code : codes) {
            for (char c : code.toCharArray()) {
                script.add(InvitationCodeGenerator.ALPHABET.indexOf(c));
            }
        }
    }

    public synchronized void reset() {
        script.clear();
    }

    @Override
    public synchronized int nextInt(int bound) {
        Integer next = script.poll();
        return next != null ? next : delegate.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return delegate.nextLong();
    }
}
