#!/bin/sh
# Instalação de demonstração (D-125): com DEMO_INSTANCE=true, preenche a meta resort-demo do index.html
# gerado pelo 40-resort-name.sh, e o rodapé de todas as telas mostra "Ambiente de demonstração". Vazio ou
# false deixa a meta vazia. Qualquer outro valor recusa a subida: um "True" ou "1" digitado por engano
# não pode esconder a etiqueta de uma instalação com dados fictícios.
set -eu

TARGET=/usr/share/nginx/html/index.html
MARKER='<meta name="resort-demo" content="" />'

fail() {
  echo "42-resort-demo.sh: $*" >&2
  exit 1
}

grep -qF "$MARKER" "$TARGET" || fail "meta tag resort-demo não encontrada em $TARGET"

# O case compara a variável inteira: uma quebra de linha ou um espaço no valor não passa.
case "${DEMO_INSTANCE:-}" in
  '' | false) ;;
  true) sed -i "s|$MARKER|<meta name=\"resort-demo\" content=\"true\" />|" "$TARGET" ;;
  *) fail "DEMO_INSTANCE inválida: use true numa instalação de demonstração, ou deixe vazia" ;;
esac
