package com.resort.platform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;

/**
 * Passo de migração da produção (D-057): aplica as migrations como {@code resort_owner}, o dono do
 * schema, e depois as permissões de {@code resort_app}. Roda e termina, antes da aplicação subir; a
 * senha do dono fica só neste passo, nunca no processo da aplicação, que roda com o Flyway desligado.
 */
public final class MigrateMain {

    static final String GRANTS = "db/prod/grants.sql";

    private MigrateMain() {}

    public static void main(String[] args) throws Exception {
        migrate(required("MIGRATE_DB_URL"), required("MIGRATE_DB_USER"), required("MIGRATE_DB_PASSWORD"));
    }

    public static void migrate(String url, String user, String password) throws SQLException, IOException {
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute(grants());
        }
    }

    private static String grants() throws IOException {
        try (InputStream in = MigrateMain.class.getClassLoader().getResourceAsStream(GRANTS)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + GRANTS);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável obrigatória ausente: " + name);
        }
        return value;
    }
}
