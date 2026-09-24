package com.resort.platform.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.TestCodes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** G2: normalização do código (§12.1). Os códigos da tabela são montados para o teste, sem valor real. */
class AccessCodesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "RSV:ABCDEFGHJK      | ABCDEFGHJK",
        "rsv:abcde-fghjk     | ABCDEFGHJK",
        "  RSV: ABCDE FGHJK  | ABCDEFGHJK",
        "ABCDE-FGHJK         | ABCDEFGHJK",
        "abcde fghjk         | ABCDEFGHJK",
        "O0IL1-ABCDE         | 00111ABCDE",
        "oil23-45678         | 01123456 78",
    })
    void normalizesPrefixCaseSeparatorsAndAmbiguousLetters(String raw, String expected) {
        String normalized = AccessCodes.normalize(raw);
        assertThat(normalized).isEqualTo(expected.replace(" ", ""));
        assertThat(AccessCodes.isValid(normalized)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ABCDEFGHJ",        // 9
        "ABCDEFGHJKM",      // 11
        "ABCDEFGHJU",       // U não existe no alfabeto
        "ABCDE_FGHJK",      // símbolo
        "ABCDE.FGHJK",
        "RSV:",             // só o prefixo
        "XRSV:ABCDEFGHJK",  // prefixo fora do início não é removido
        "ABCRSV:DEFGH",
    })
    void rejectsMalformedCodes(String raw) {
        assertThat(AccessCodes.isValid(AccessCodes.normalize(raw))).isFalse();
    }

    @org.junit.jupiter.api.Test
    void generatedCodesRoundTrip() {
        String code = TestCodes.unique();
        assertThat(AccessCodes.normalize("RSV:" + code)).isEqualTo(code);
        assertThat(AccessCodes.normalize(code.substring(0, 5) + "-" + code.substring(5))).isEqualTo(code);
    }
}
