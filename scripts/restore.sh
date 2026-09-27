#!/usr/bin/env bash
# Restauração de um backup cifrado (D-110), na VPS ou numa VPS nova. Passos:
#   1. confere que a chave privada decifra o backup (nada é apagado se não decifrar);
#   2. para o Nginx, o backend e o backup; sobe o PostgreSQL se ele não estiver no ar;
#   3. recria o banco "resort" (dono resort_owner, com as permissões de banco do postgres/initdb);
#   4. restaura o dump como resort_owner (os papéis já existem: criados na criação do volume);
#   5. roda o passo de migração (migrations mais novas e permissões de resort_app, D-057);
#   6. sobe a pilha de novo.
# Uso: scripts/restore.sh <arquivo .env.prod> <backup .dump.age> <chave privada do age>
# A chave privada só deve ficar no servidor durante a restauração (ver docs/DEPLOY.md).
# COMPOSE_PROJECT_NAME escolhe outro projeto do compose (a verificação local usa um próprio).
set -euo pipefail

ENV_FILE="${1:?uso: scripts/restore.sh <arquivo .env.prod> <backup .dump.age> <chave privada do age>}"
BACKUP="${2:?informe o arquivo do backup}"
KEY="${3:?informe a chave privada do age}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

for file in "$ENV_FILE" "$BACKUP" "$KEY"; do
  [ -r "$file" ] || { echo "restore: arquivo não encontrado ou sem leitura: $file" >&2; exit 1; }
done
ENV_FILE="$(cd "$(dirname "$ENV_FILE")" && pwd)/$(basename "$ENV_FILE")"
KEY="$(cd "$(dirname "$KEY")" && pwd)/$(basename "$KEY")"

value() { # value <nome>: lê a variável do .env.prod, sem executar o arquivo
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e "s/^'\(.*\)'\$/\1/" -e 's/^"\(.*\)"$/\1/'
}
OWNER_PASSWORD="$(value DB_OWNER_PASSWORD)"
[ -n "$OWNER_PASSWORD" ] || { echo "restore: DB_OWNER_PASSWORD vazia em $ENV_FILE" >&2; exit 1; }

compose() { docker compose -f "$ROOT/docker-compose.prod.yml" --project-directory "$ROOT" --env-file "$ENV_FILE" "$@"; }
in_backup() { # in_backup <comando...>: roda no container do backup, com a chave montada só para leitura
  compose run --rm -T --no-deps -v "$KEY:/run/age-key:ro" -e PGPASSWORD="$OWNER_PASSWORD" backup "$@"
}

echo "==> 1/6 Conferindo a chave"
if ! in_backup sh -c 'age --decrypt --identity /run/age-key >/dev/null' <"$BACKUP"; then
  echo "restore: a chave informada não decifra este backup; nada foi alterado." >&2
  exit 1
fi

echo "==> 2/6 Parando o Nginx, o backend e o backup"
compose stop nginx backend backup >/dev/null 2>&1 || true
compose up -d postgres >/dev/null
for _ in $(seq 60); do
  if compose exec -T -u postgres postgres pg_isready -d postgres >/dev/null 2>&1; then break; fi
  sleep 1
done

echo "==> 3/6 Recriando o banco"
compose exec -T -u postgres postgres psql -v ON_ERROR_STOP=1 -d postgres >/dev/null <<'SQL'
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'resort' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS resort;
CREATE DATABASE resort OWNER resort_owner;
REVOKE ALL ON DATABASE resort FROM PUBLIC;
GRANT CONNECT ON DATABASE resort TO resort_owner, resort_app, resort_backup;
\connect resort
ALTER SCHEMA public OWNER TO resort_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO resort_app, resort_backup;
SQL

echo "==> 4/6 Restaurando como resort_owner"
in_backup sh -c 'set -o pipefail; age --decrypt --identity /run/age-key \
  | pg_restore -h postgres -U resort_owner -d resort --no-owner --role=resort_owner --exit-on-error' <"$BACKUP"

echo "==> 5/6 Passo de migração (migrations e permissões)"
compose run --rm --no-deps migrate >/dev/null

echo "==> 6/6 Subindo a pilha"
compose up -d >/dev/null
echo "restore: concluído. Confira o login e a auditoria (docs/DEPLOY.md)."
