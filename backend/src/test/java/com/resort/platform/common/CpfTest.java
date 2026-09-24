package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.FakeCpf;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {

    @RepeatedTest(20)
    void acceptsGeneratedValidCpf() {
        String cpf = FakeCpf.generate();
        assertThat(Cpf.isValid(cpf)).isTrue();
        assertThat(Cpf.isValid(Cpf.normalize(FakeCpf.formatted(cpf)))).isTrue();
    }

    @RepeatedTest(20)
    void rejectsWrongCheckDigit() {
        assertThat(Cpf.isValid(FakeCpf.invalid())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999", "1234567890", "123456789012", "1234567890a", ""})
    void rejectsRepeatedSequencesAndWrongShapes(String value) {
        assertThat(Cpf.isValid(value)).isFalse();
    }

    @Test
    void normalizesFormattedInput() {
        assertThat(Cpf.normalize(" 123.456.789-01 ")).isEqualTo("12345678901");
        assertThat(Cpf.normalize("   ")).isNull();
        assertThat(Cpf.normalize(null)).isNull();
    }

    @Test
    void formatsAndMasks() {
        assertThat(Cpf.format("12345678901")).isEqualTo("123.456.789-01");
        assertThat(Cpf.mask("12345678901")).isEqualTo("***.456.789-**");
        assertThat(Cpf.mask(null)).isNull();
    }
}
