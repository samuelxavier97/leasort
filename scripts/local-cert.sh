#!/usr/bin/env bash
# Certificado local para a verificação da pilha (D-109): uma CA descartável e um certificado para o
# domínio (padrão: localhost), no mesmo layout do Let's Encrypt (live/<domínio>/fullchain.pem e
# privkey.pem), para ser copiado no volume letsencrypt. Nunca usar em produção.
# Uso: scripts/local-cert.sh <pasta-de-saída> [domínio]
set -euo pipefail

OUT="${1:?uso: scripts/local-cert.sh <pasta-de-saída> [domínio]}"
DOMAIN="${2:-localhost}"
LIVE="$OUT/live/$DOMAIN"
mkdir -p "$LIVE"

openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=CA local da verificação" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign" \
  -keyout "$OUT/ca.key" -out "$OUT/ca.crt" 2>/dev/null
openssl req -newkey rsa:2048 -nodes -subj "/CN=$DOMAIN" -keyout "$LIVE/privkey.pem" -out "$OUT/server.csr" 2>/dev/null
printf 'subjectAltName=DNS:%s,IP:127.0.0.1\nbasicConstraints=CA:FALSE\nextendedKeyUsage=serverAuth\n' "$DOMAIN" >"$OUT/server.ext"
openssl x509 -req -in "$OUT/server.csr" -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial -days 2 \
  -extfile "$OUT/server.ext" -out "$LIVE/cert.pem" 2>/dev/null
cat "$LIVE/cert.pem" "$OUT/ca.crt" >"$LIVE/fullchain.pem"
rm -f "$OUT/server.csr" "$OUT/server.ext" "$OUT/ca.key"
echo "$LIVE"
