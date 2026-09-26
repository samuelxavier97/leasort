-- Permissões da aplicação na produção (D-057), aplicadas pelo MigrateMain como resort_owner depois de
-- cada migração. Idempotente. A aplicação faz só DML: não cria, altera nem apaga objetos, e não
-- alcança o trigger nem a função que tornam audit_logs somente de inserção (D-028).
GRANT USAGE ON SCHEMA public TO resort_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO resort_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO resort_app;

-- Tabelas criadas por migrations futuras já nascem com o mesmo DML.
ALTER DEFAULT PRIVILEGES FOR ROLE resort_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO resort_app;
ALTER DEFAULT PRIVILEGES FOR ROLE resort_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO resort_app;

-- Auditoria: só leitura e inserção, além do trigger.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM resort_app;

-- O histórico do Flyway é só do dono.
REVOKE ALL ON flyway_schema_history FROM resort_app;
