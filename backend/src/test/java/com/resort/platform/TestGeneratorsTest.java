package com.resort.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.common.Cpf;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Os geradores de dados de teste nunca repetem valores na execução e só produzem valores válidos. */
class TestGeneratorsTest {

    @Test
    void fakeCpfsAreValidAndNeverRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200_000; i++) {
            String cpf = FakeCpf.generate();
            assertThat(Cpf.isValid(cpf)).as(cpf).isTrue();
            assertThat(seen.add(cpf)).as("repetido: %s", cpf).isTrue();
        }
        assertThat(Cpf.isValid(FakeCpf.invalid())).isFalse();
    }

    @Test
    void invitationCodesAreValidAndNeverRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200_000; i++) {
            String code = TestCodes.unique();
            assertThat(code).matches("^[0-9A-HJKMNP-TV-Z]{10}$");
            assertThat(seen.add(code)).as("repetido: %s", code).isTrue();
        }
    }

    @Test
    void sequenceValuesNeverRepeatNorPrefixEachOther() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String value = TestSequence.next("x");
            assertThat(value).hasSize(9);
            assertThat(seen.add(value)).isTrue();
        }
        assertThat(TestData.uniqueEmail("teste")).matches("^teste-\\d{8}@test\\.local$");
        assertThat(TestData.uniqueCode()).matches("^EMP-\\d{8}$");
    }
}
