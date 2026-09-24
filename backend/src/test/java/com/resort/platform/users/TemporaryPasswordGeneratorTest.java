package com.resort.platform.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generatesTwelveCharactersFromTheUnambiguousAlphabet() {
        String password = generator.generate();

        assertThat(password).hasSize(12);
        assertThat(password.chars()).allMatch(c -> TemporaryPasswordGenerator.ALPHABET.indexOf(c) >= 0);
        assertThat(PasswordRules.isValid(password)).isTrue();
    }

    @Test
    void doesNotRepeatAcrossManySamples() {
        Set<String> samples = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            samples.add(generator.generate());
        }
        assertThat(samples).hasSize(1000);
    }
}
