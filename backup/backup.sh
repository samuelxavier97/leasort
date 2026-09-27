#!/bin/sh
# Um backup do banco (D-110): pg_dump como resort_backup (só leitura), cifrado com age para
# BACKUP_AGE_RECIPIENT e gravado em /backups; depois apaga os arquivos com 14 dias ou mais.
# Sem os dados das sessões (spring_session*): sessões não voltam numa restauração.
set -eu
set -o pipefail

: "${BACKUP_AGE_RECIPIENT:?BACKUP_AGE_RECIPIENT obrigatória}"
: "${PGPASSWORD:?PGPASSWORD obrigatória}"
DIR=/backups
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
name="resort-$(date -u +%Y%m%dT%H%M%SZ).dump.age"

pg_dump -h "${PGHOST:-postgres}" -U resort_backup -d resort --format=custom \
  --exclude-table-data='spring_session*' \
  | age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$DIR/.$name.partial"
mv "$DIR/.$name.partial" "$DIR/$name"
echo "backup: $name ($(wc -c <"$DIR/$name") bytes)"

# Retenção: -mtime +N apaga os arquivos com N+1 dias completos ou mais.
find "$DIR" -maxdepth 1 -name 'resort-*.dump.age' -mtime "+$((RETENTION_DAYS - 1))" -print -delete \
  | sed 's/^/backup: apagado por retenção: /'
