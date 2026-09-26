package com.resort.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * A aleatoriedade do código de convite em produção (D-082). Os testes de integração trocam o bean
 * por um {@code ScriptedRandom} ({@code @Primary}); aqui o contexto só tem a configuração da
 * aplicação, como em produção.
 */
class CommonConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CommonConfig.class)
            .withPropertyValues("app.timezone=America/Sao_Paulo", "app.max-companions=6", "app.gate-name=PRINCIPAL");

    @Test
    void theInvitationCodeRandomnessIsASecureRandom() {
        context.run(app -> {
            assertThat(app).hasSingleBean(RandomGenerator.class);
            assertThat(app.getBean(RandomGenerator.class)).isInstanceOf(SecureRandom.class);
        });
    }
}
