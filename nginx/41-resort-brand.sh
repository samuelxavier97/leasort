#!/bin/sh
# Tema do cliente (D-115): valida BRAND_COLOR e o logotipo do diretório montado em /etc/resort/brand,
# copia o PNG validado para /brand/logo.png e preenche as metas do index.html gerado pelo
# 40-resort-name.sh. Roda a cada subida: trocar a cor ou o arquivo e reiniciar basta, sem rebuild.
# Qualquer valor inválido recusa a subida, para a marca errada não ir ao ar sem ninguém perceber.
set -eu

TARGET=/usr/share/nginx/html/index.html
SOURCE_DIR=/etc/resort/brand
OUT_DIR=/usr/share/nginx/html/brand
MAX_BYTES=262144
MAX_SIDE=2048

fail() {
  echo "41-resort-brand.sh: $*" >&2
  exit 1
}

for name in resort-brand-color resort-brand-logo; do
  grep -qF "<meta name=\"$name\" content=\"\" />" "$TARGET" || fail "meta tag $name não encontrada em $TARGET"
done

# Cor: só #RRGGBB (o case compara a variável inteira; uma quebra de linha no valor não passa).
color="${BRAND_COLOR:-}"
if [ -n "$color" ]; then
  case "$color" in
    \#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]) ;;
    *) fail "BRAND_COLOR inválida: use o formato #RRGGBB, como #1f4e79" ;;
  esac
  color=$(printf '%s' "$color" | tr 'A-F' 'a-f')
fi

# Logotipo: só PNG (o SVG foi descartado na D-115). A cópia anterior sai antes: sem o arquivo, não há
# logotipo, mesmo num container reiniciado.
rm -rf "$OUT_DIR"
logo=""
for other in logo.svg logo.jpg logo.jpeg logo.webp logo.gif; do
  if [ -e "$SOURCE_DIR/$other" ]; then
    fail "$other não é aceito: o logotipo é só PNG, em logo.png"
  fi
done
file="$SOURCE_DIR/logo.png"
if [ -e "$file" ]; then
  [ -f "$file" ] || fail "logo.png não é um arquivo"
  size=$(wc -c <"$file" | tr -d ' ')
  [ "$size" -le "$MAX_BYTES" ] || fail "logo.png tem $size bytes; o máximo é $MAX_BYTES (256 KB)"
  # Assinatura PNG (8 bytes), tamanho do bloco IHDR (13), "IHDR", largura e altura (4 bytes cada).
  header=$(od -An -tx1 -N24 "$file" | tr -d ' \n')
  case "$header" in
    89504e470d0a1a0a0000000d49484452????????????????) ;;
    *) fail "logo.png não é um PNG válido (assinatura ou bloco IHDR ausente)" ;;
  esac
  width=$((0x$(printf '%s' "$header" | cut -c33-40)))
  height=$((0x$(printf '%s' "$header" | cut -c41-48)))
  if [ "$width" -lt 1 ] || [ "$width" -gt "$MAX_SIDE" ] || [ "$height" -lt 1 ] || [ "$height" -gt "$MAX_SIDE" ]; then
    fail "logo.png tem ${width} × ${height} px; largura e altura vão de 1 a $MAX_SIDE px"
  fi
  mkdir -p "$OUT_DIR"
  cp "$file" "$OUT_DIR/logo.png"
  chmod 644 "$OUT_DIR/logo.png"
  logo=/brand/logo.png
fi

# Valores já validados (hexadecimal e caminho fixo): nada a escapar.
sed -i \
  -e "s|<meta name=\"resort-brand-color\" content=\"\" />|<meta name=\"resort-brand-color\" content=\"$color\" />|" \
  -e "s|<meta name=\"resort-brand-logo\" content=\"\" />|<meta name=\"resort-brand-logo\" content=\"$logo\" />|" \
  "$TARGET"
