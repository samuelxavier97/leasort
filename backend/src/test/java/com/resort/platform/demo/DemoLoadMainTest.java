package com.resort.platform.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Trava 1 da D-125, sem banco: a carga e a conferência da recarga só rodam com DEMO_INSTANCE=true, exatamente,
 * e recusam antes de qualquer conexão. O endereço do banco aponta para uma porta fechada: se a carga tentasse
 * conectar, a saída seria 1 (erro), não 2 (recusa).
 */
class DemoLoadMainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(Map<String, String> env, String... args) {
        return DemoLoadMain.run(args, env, Clock.systemUTC(), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static Map<String, String> env(String demoInstance) {
        Map<String, String> env = new HashMap<>();
        if (demoInstance != null) {
            env.put("DEMO_INSTANCE", demoInstance);
        }
        env.put("DB_URL", "jdbc:postgresql://127.0.0.1:1/resort");
        env.put("DB_USER", "resort_app");
        env.put("DB_PASSWORD", "nao-usada");
        env.put("APP_BOOTSTRAP_ADMIN_EMAIL", "admin@producao.local");
        env.put("DEMO_ADMIN_NAME", "Carlos Eduardo Fictício");
        env.put("DEMO_ADMIN_PASSWORD", "senha-admin-demo");
        env.put("DEMO_PROSPECTOR_PASSWORD", "senha-prospector-demo");
        env.put("DEMO_GATE_PASSWORD", "senha-portaria-demo");
        env.put("DEMO_HOST_PASSWORD", "senha-anfitriao-demo");
        return env;
    }

    private String errText() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "false", "TRUE", "True", "1", "yes", " true", "true "})
    void withoutDemoInstanceExactlyTrueTheLoadRefusesBeforeConnecting(String value) {
        assertThat(run(env(value))).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(errText()).contains("DEMO_INSTANCE=true").contains("Nada foi alterado");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"false", "TRUE", "1"})
    void theResetCheckHasTheSameLock(String value) {
        assertThat(run(env(value), "check-reset")).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(errText()).contains("DEMO_INSTANCE=true");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "A", "Nome\nQuebrado", "Nome\tcom tab"})
    void aMissingOrInvalidAdminNameIsRefusedBeforeConnecting(String name) {
        Map<String, String> env = env("true");
        env.put("DEMO_ADMIN_NAME", name);
        if (name == null) {
            env.remove("DEMO_ADMIN_NAME");
        }
        assertThat(run(env)).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(errText()).contains("DEMO_ADMIN_NAME").contains("Nada foi alterado");
    }

    @org.junit.jupiter.api.Test
    void anAdminNameLongerThanTheColumnIsRefused() {
        Map<String, String> env = env("true");
        env.put("DEMO_ADMIN_NAME", "N".repeat(DemoLoadMain.MAX_NAME + 1));
        assertThat(run(env)).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(DemoLoadMain.validName("N".repeat(DemoLoadMain.MAX_NAME))).isTrue();
        assertThat(DemoLoadMain.validName("Ana")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEMO_ADMIN_PASSWORD", "DEMO_PROSPECTOR_PASSWORD", "DEMO_GATE_PASSWORD", "DEMO_HOST_PASSWORD"})
    void aMissingOrShortPasswordIsRefusedBeforeConnecting(String name) {
        Map<String, String> missing = env("true");
        missing.remove(name);
        assertThat(run(missing)).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(errText()).contains(name);

        Map<String, String> tooShort = env("true");
        tooShort.put(name, "curta");
        err.reset();
        assertThat(run(tooShort)).isEqualTo(DemoLoadMain.REFUSED);
        assertThat(errText()).contains(name).doesNotContain("curta");
    }

    @Test
    void withTheLockOpenItTriesTheDatabase() {
        // A porta fechada prova o outro lado: com DEMO_INSTANCE=true e senhas válidas, a carga chega ao banco.
        assertThat(run(env("true"))).isEqualTo(DemoLoadMain.ERROR);
        assertThat(errText()).contains("SQLState").doesNotContain("senha-");
    }

    @Test
    void anUnknownModeIsAnError() {
        assertThat(run(env("true"), "apagar")).isEqualTo(DemoLoadMain.ERROR);
    }
}
