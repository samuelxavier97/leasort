package com.resort.platform.demo;

import com.resort.platform.users.PasswordRules;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;

/**
 * Carga da instalação de demonstração (D-125), no padrão do {@code MigrateMain}: roda e termina, sem
 * subir a aplicação. Uso pelo serviço {@code demo-load} do compose, chamado por {@code scripts/demo-load.sh}
 * e {@code scripts/demo-reset.sh}.
 * <ul>
 *   <li>sem argumento: carrega a demonstração;</li>
 *   <li>{@code check-reset}: só confere se o banco pode ser recriado pela recarga.</li>
 * </ul>
 * Saída 0 quando deu certo, 2 quando uma trava recusou (nada foi gravado) e 1 em qualquer outro erro.
 */
public final class DemoLoadMain {

    static final int OK = 0;
    static final int ERROR = 1;
    static final int REFUSED = 2;

    /** O limite de {@code users.name} (§8.1). */
    static final int MAX_NAME = 120;

    private DemoLoadMain() {}

    static boolean validName(String name) {
        return name.length() >= 2 && name.length() <= MAX_NAME && name.chars().noneMatch(Character::isISOControl);
    }

    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), Clock.systemUTC(), System.out, System.err));
    }

    static int run(String[] args, Map<String, String> env, Clock clock, PrintStream out, PrintStream err) {
        // Trava 1: antes de qualquer conexão com o banco.
        if (!"true".equals(env.get("DEMO_INSTANCE"))) {
            err.println("Recusado: a carga de demonstração só roda com DEMO_INSTANCE=true (D-125). Nada foi alterado.");
            return REFUSED;
        }
        String mode = args.length == 0 ? "load" : args[0];
        if (!mode.equals("load") && !mode.equals("check-reset")) {
            err.println("Modo desconhecido: use nenhum argumento (carga) ou check-reset.");
            return ERROR;
        }
        DemoLoader loader = new DemoLoader();
        DemoLoader.Passwords passwords = null;
        String adminName = null;
        if (mode.equals("load")) {
            // Nome do ADMIN da demonstração, informado como as senhas, sem padrão no repositório (D-125).
            adminName = env.get("DEMO_ADMIN_NAME") == null ? "" : env.get("DEMO_ADMIN_NAME").strip();
            if (!validName(adminName)) {
                err.println("Recusado: DEMO_ADMIN_NAME ausente ou inválido (de 2 a " + MAX_NAME + " caracteres, sem quebra de linha). Nada foi alterado.");
                return REFUSED;
            }
            passwords = new DemoLoader.Passwords(env.get("DEMO_ADMIN_PASSWORD"), env.get("DEMO_PROSPECTOR_PASSWORD"),
                    env.get("DEMO_GATE_PASSWORD"), env.get("DEMO_HOST_PASSWORD"));
            for (String name : new String[] {"DEMO_ADMIN_PASSWORD", "DEMO_PROSPECTOR_PASSWORD", "DEMO_GATE_PASSWORD", "DEMO_HOST_PASSWORD"}) {
                if (!PasswordRules.isValid(env.get(name))) {
                    err.println("Recusado: " + name + " ausente ou inválida (mínimo de 10 caracteres e no máximo 72 bytes). Nada foi alterado.");
                    return REFUSED;
                }
            }
        }
        String url = env.get("DB_URL");
        String user = env.get("DB_USER");
        String password = env.get("DB_PASSWORD");
        if (url == null || user == null || password == null) {
            err.println("Variáveis obrigatórias ausentes: DB_URL, DB_USER e DB_PASSWORD.");
            return ERROR;
        }
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            if (mode.equals("check-reset")) {
                String bootstrapEmail = env.get("APP_BOOTSTRAP_ADMIN_EMAIL");
                if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
                    err.println("Variável obrigatória ausente: APP_BOOTSTRAP_ADMIN_EMAIL.");
                    return ERROR;
                }
                int users = loader.checkReset(connection, bootstrapEmail);
                out.println("Conferido: os " + users + " usuários são da demonstração ou o ADMIN inicial; a recarga pode recriar o banco.");
                return OK;
            }
            ZoneId zone = ZoneId.of(env.getOrDefault("APP_TIMEZONE", "America/Sao_Paulo"));
            String gate = env.getOrDefault("APP_GATE_NAME", "PRINCIPAL");
            Map<String, Integer> totals = loader.load(connection, clock, zone, gate, passwords, adminName);
            out.println("Demonstração carregada:");
            totals.forEach((what, count) -> out.println("  " + what + ": " + count));
            out.println("Entre com os usuários " + DemoDataPlan.ADMIN_EMAIL + ", " + DemoDataPlan.PROSPECTOR_EMAIL + ", "
                    + DemoDataPlan.GATE_EMAIL + " e " + DemoDataPlan.HOST_EMAIL + ", com as senhas informadas.");
            return OK;
        } catch (DemoLoader.Refused refused) {
            err.println("Recusado: " + refused.getMessage());
            return REFUSED;
        } catch (SQLException e) {
            // Só o SQLState: o detalhe do PostgreSQL repete o valor da chave, que pode ser um código de convite (regra 5).
            err.println("Falha na carga de demonstração: erro do banco (SQLState " + e.getSQLState() + "). Nada foi gravado.");
            return ERROR;
        } catch (RuntimeException e) {
            err.println("Falha na carga de demonstração: " + e.getClass().getSimpleName() + ". Nada foi gravado.");
            return ERROR;
        }
    }
}
