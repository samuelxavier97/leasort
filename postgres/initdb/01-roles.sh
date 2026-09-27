#!/bin/sh
# Papéis do banco de produção (D-057). Roda uma única vez, na criação do volume de dados, como o
# superusuário da imagem oficial. Os nomes são fixos; as senhas vêm do .env e nunca vão para log.
#   resort_owner   dono do banco e do schema; usado só pelo passo de migração (Flyway)
#   resort_app     só DML; usado pela aplicação (as permissões nas tabelas vêm do db/prod/grants.sql)
#   resort_backup  só leitura (pg_read_all_data); usado pelo pg_dump
set -eu

: "${DB_OWNER_PASSWORD:?DB_OWNER_PASSWORD obrigatória}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD obrigatória}"
: "${DB_BACKUP_PASSWORD:?DB_BACKUP_PASSWORD obrigatória}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" \
  -v owner_password="$DB_OWNER_PASSWORD" \
  -v app_password="$DB_APP_PASSWORD" \
  -v backup_password="$DB_BACKUP_PASSWORD" <<'SQL'
CREATE ROLE resort_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE resort_app LOGIN PASSWORD :'app_password';
CREATE ROLE resort_backup LOGIN PASSWORD :'backup_password';

ALTER DATABASE :"db" OWNER TO resort_owner;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO resort_owner, resort_app, resort_backup;

ALTER SCHEMA public OWNER TO resort_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO resort_app, resort_backup;

GRANT pg_read_all_data TO resort_backup;
SQL
