package com.resort.platform.production;

import com.resort.platform.MigrateMain;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL como na produção (D-057): o mesmo script de papéis do container ({@code postgres/initdb})
 * e o mesmo passo de migração ({@link MigrateMain}). Senhas fictícias, só dos testes.
 */
final class ProductionDatabase {

    static final String OWNER = "resort_owner";
    static final String APP = "resort_app";
    static final String BACKUP = "resort_backup";
    static final String OWNER_PASSWORD = "owner-test-password";
    static final String APP_PASSWORD = "app-test-password";
    static final String BACKUP_PASSWORD = "backup-test-password";

    private ProductionDatabase() {}

    static PostgreSQLContainer container() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("resort")
                .withEnv("DB_OWNER_PASSWORD", OWNER_PASSWORD)
                .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
                .withEnv("DB_BACKUP_PASSWORD", BACKUP_PASSWORD)
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../postgres/initdb/01-roles.sh", 0755),
                        "/docker-entrypoint-initdb.d/01-roles.sh");
    }

    static void migrate(PostgreSQLContainer postgres) throws Exception {
        MigrateMain.migrate(postgres.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }

    static JdbcClient as(PostgreSQLContainer postgres, String user, String password) {
        return JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), user, password));
    }
}
