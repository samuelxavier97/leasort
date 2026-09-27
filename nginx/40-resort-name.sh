#!/bin/sh
# Preenche a meta tag resort-name do index.html com RESORT_NAME, escapada para HTML (D-087). Roda a
# cada subida a partir do modelo, então trocar a variável e reiniciar basta. Sem a variável, a meta
# fica vazia e o frontend usa "Resort". A CSP proíbe script inline, por isso o valor vai numa meta tag.
set -eu

TEMPLATE=/usr/share/nginx/index.html.template
TARGET=/usr/share/nginx/html/index.html
MARKER='<meta name="resort-name" content="" />'

if ! grep -qF "$MARKER" "$TEMPLATE"; then
  echo "40-resort-name.sh: meta tag resort-name não encontrada em $TEMPLATE" >&2
  exit 1
fi

# Uma linha só, com &, <, >, " e ' trocados por entidades.
escaped=$(printf '%s' "${RESORT_NAME:-}" | tr -d '\r\n' | sed \
  -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g' -e "s/'/\&#39;/g")
# Para o sed a seguir, o valor não pode carregar \, & nem o delimitador |.
replacement=$(printf '%s' "$escaped" | sed -e 's/[\\&|]/\\&/g')

sed "s|<meta name=\"resort-name\" content=\"\" />|<meta name=\"resort-name\" content=\"$replacement\" />|" \
  "$TEMPLATE" > "$TARGET"
