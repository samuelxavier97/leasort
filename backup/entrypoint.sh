#!/bin/sh
# Container do backup (D-110): agenda o backup.sh no crond, no horário BACKUP_TIME (HH:MM) do fuso da
# operação (TZ=APP_TIMEZONE no compose). Sem argumentos, fica no crond; com argumentos, executa-os
# (ex.: "backup.sh" para um backup na hora, ou a restauração pelo scripts/restore.sh).
set -eu

if [ "$#" -gt 0 ]; then
  exec "$@"
fi

TIME="${BACKUP_TIME:-03:00}"
HOUR="${TIME%%:*}"
MINUTE="${TIME##*:}"
# O crond não repassa o ambiente: as variáveis do backup vão num arquivo lido pela linha do crontab.
env | grep -E '^(BACKUP_|PG|TZ=)' | sed "s/'/'\\\\''/g; s/=/='/; s/\$/'/; s/^/export /" >/etc/backup.env
chmod 600 /etc/backup.env
echo "$(expr "$MINUTE" + 0) $(expr "$HOUR" + 0) * * * . /etc/backup.env && /usr/local/bin/backup.sh >/proc/1/fd/1 2>/proc/1/fd/2" >/etc/crontabs/root
# Marca da subida: a carência da verificação de saúde (health.sh) conta a partir daqui.
date +%s >/run/backup-started
echo "backup: agendado todos os dias às $TIME ($TZ); retenção de ${BACKUP_RETENTION_DAYS:-14} dias"
exec crond -f -l 8
