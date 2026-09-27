#!/bin/sh
# Entrada do container do PostgreSQL de produção (D-057, complemento). Gera o pg_hba.conf a cada
# subida, fora do volume de dados, e passa para o entrypoint oficial:
#   - o superusuário "postgres" só entra pelo socket local, dentro deste container (peer);
#   - resort_owner, resort_app e resort_backup só entram pela rede, vindos da sub-rede interna do
#     compose, com senha (scram-sha-256);
#   - qualquer outra combinação é recusada, inclusive o superusuário pela rede com a senha certa.
set -eu

: "${INTERNAL_SUBNET:?INTERNAL_SUBNET obrigatória}"
HBA_DIR=/etc/postgresql-resort
mkdir -p "$HBA_DIR"
cat >"$HBA_DIR/pg_hba.conf" <<HBA
# Gerado por pg-entrypoint.sh a cada subida; não editar.
# TYPE  DATABASE  USER                                  ADDRESS               METHOD
local   all       postgres                                                    peer
local   all       all                                                         reject
host    resort    resort_owner,resort_app,resort_backup ${INTERNAL_SUBNET}    scram-sha-256
host    all       all                                   0.0.0.0/0             reject
host    all       all                                   ::/0                  reject
HBA
chown -R postgres:postgres "$HBA_DIR"
chmod 600 "$HBA_DIR/pg_hba.conf"

exec docker-entrypoint.sh "$@" -c hba_file="$HBA_DIR/pg_hba.conf"
