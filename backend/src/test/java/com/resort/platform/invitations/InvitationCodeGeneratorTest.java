package com.resort.platform.invitations;

import static org.assertj.core.api.Assertions.assertThat;

import com.resort.platform.ScriptedRandom;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** I2: RN07 e D-001. */
class InvitationCodeGeneratorTest {

    @Test
    void codesHaveTenCrockfordCharactersAndUseTheWholeAlphabet() {
        InvitationCodeGenerator generator = new InvitationCodeGenerator(new SecureRandom());
        Set<Character> seen = new HashSet<>();
        Set<String> codes = new HashSet<>();

        IntStream.range(0, 10_000).forEach(i -> {
            String code = generator.next();
            assertThat(code).matches("^[0-9A-HJKMNP-TV-Z]{10}$").doesNotContain("I", "L", "O", "U");
            code.chars().forEach(c -> seen.add((char) c));
            codes.add(code);
        });

        assertThat(seen).hasSize(32);
        assertThat(codes).hasSize(10_000);
    }

    @Test
    void codeFollowsTheRandomSource() {
        ScriptedRandom random = new ScriptedRandom();
        random.enqueueCodes("0123456789", "ABCDEFGHJK");
        InvitationCodeGenerator generator = new InvitationCodeGenerator(random);

        assertThat(generator.next()).isEqualTo("0123456789");
        assertThat(generator.next()).isEqualTo("ABCDEFGHJK");
        assertThat(generator.next()).matches("^[0-9A-HJKMNP-TV-Z]{10}$");
    }

    @Test
    void formatSplitsTheCodeWithAHyphen() {
        assertThat(InvitationCodeGenerator.format("ABCDEFGHJK")).isEqualTo("ABCDE-FGHJK");
    }
}
