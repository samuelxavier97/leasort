#!/bin/sh
# Saúde do container de backup (D-110), usada pelo healthcheck do compose:
#   - o crond precisa estar rodando;
#   - com algum backup no volume, o mais recente (resort-*.dump.age) precisa ter menos de
#     BACKUP_MAX_AGE_HOURS (26 h: o intervalo de um dia mais folga). Um reinício do container não
#     esconde um backup parado;
#   - sem nenhum backup (pilha recém-criada), vale uma carência de BACKUP_MAX_AGE_HOURS desde a subida
#     do container, porque o primeiro backup só sai no próximo BACKUP_TIME.
# A mensagem de falha aparece em "docker inspect" (State.Health.Log) e no runbook.
set -eu

MAX_HOURS="${BACKUP_MAX_AGE_HOURS:-26}"
MAX_MINUTES=$((MAX_HOURS * 60))

if ! pgrep crond >/dev/null; then
  echo "backup: crond parado"
  exit 1
fi

latest="$(ls -1t /backups/resort-*.dump.age 2>/dev/null | head -n 1 || true)"
if [ -n "$latest" ]; then
  if [ -n "$(find "$latest" -mmin "-$MAX_MINUTES")" ]; then
    echo "backup: ok, último $(basename "$latest")"
    exit 0
  fi
  echo "backup: o último backup ($(basename "$latest")) tem mais de $MAX_HOURS h"
  exit 1
fi

started="$(cat /run/backup-started 2>/dev/null || echo 0)"
if [ $(( $(date +%s) - started )) -lt $((MAX_MINUTES * 60)) ]; then
  echo "backup: nenhum backup ainda; carência de $MAX_HOURS h desde a subida"
  exit 0
fi
echo "backup: nenhum backup em /backups após $MAX_HOURS h no ar"
exit 1
