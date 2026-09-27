#!/usr/bin/env bash
# Primeira emissão do certificado do Let's Encrypt na VPS (D-109). O Nginx precisa estar no ar (com o
# certificado provisório) para responder ao desafio HTTP-01 na porta 80. Depois da emissão, o Nginx é
# reiniciado para trocar o provisório pelo real; as renovações seguintes são do serviço certbot.
# Uso: scripts/issue-cert.sh <arquivo .env.prod> [--print]
#   --print mostra o comando do certbot sem executar nada.
# Variáveis do .env.prod: DOMAIN, LETSENCRYPT_EMAIL e LETSENCRYPT_STAGING (true para o ambiente de
# testes do Let's Encrypt, sem limite de emissões e com certificado não confiável; comece por ele).
set -euo pipefail

ENV_FILE="${1:?uso: scripts/issue-cert.sh <arquivo .env.prod> [--print]}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

value() { # value <nome>: lê a variável do .env.prod, sem executar o arquivo
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e "s/^'\(.*\)'\$/\1/" -e 's/^"\(.*\)"$/\1/'
}
DOMAIN="$(value DOMAIN)"
EMAIL="$(value LETSENCRYPT_EMAIL)"
STAGING="$(value LETSENCRYPT_STAGING)"
[ -n "$DOMAIN" ] || { echo "issue-cert: DOMAIN vazio em $ENV_FILE" >&2; exit 1; }
[ -n "$EMAIL" ] || { echo "issue-cert: LETSENCRYPT_EMAIL vazio em $ENV_FILE" >&2; exit 1; }

CERTBOT=(certonly --webroot -w /var/www/acme -d "$DOMAIN" --email "$EMAIL"
  --agree-tos --no-eff-email --non-interactive --keep-until-expiring)
[ "$STAGING" = "true" ] && CERTBOT+=(--staging)

if [ "${2:-}" = "--print" ]; then
  echo "certbot ${CERTBOT[*]}"
  exit 0
fi

compose() { docker compose -f "$ROOT/docker-compose.prod.yml" --project-directory "$ROOT" --env-file "$ENV_FILE" "$@"; }
compose --profile letsencrypt run --rm certbot "${CERTBOT[@]}"
compose restart nginx
echo "issue-cert: certificado emitido para $DOMAIN; o Nginx foi reiniciado."
