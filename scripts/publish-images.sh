#!/usr/bin/env bash
# Gera e publica as imagens de produção no registro (D-112): backend, nginx e backup, cada uma com a
# tag da versão e a do commit (sha-<commit>). Chamado pelo workflow de release depois dos checks; o
# login no registro é feito antes.
# Uso: scripts/publish-images.sh <prefixo, ex.: ghcr.io/dono/repositorio> <versão, ex.: v1.2.3> <commit>
set -euo pipefail

PREFIX="$(echo "${1:?informe o prefixo das imagens}" | tr '[:upper:]' '[:lower:]')"
VERSION="${2:?informe a versão}"
COMMIT="${3:?informe o commit}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "publish-images: versão inválida: $VERSION" >&2; exit 1; }
[[ "$COMMIT" =~ ^[0-9a-f]{40}$ ]] || { echo "publish-images: commit inválido: $COMMIT" >&2; exit 1; }

build() { # build <nome> <Dockerfile> <contexto>
  local image="$PREFIX/$1"
  docker build \
    --label "org.opencontainers.image.source=https://github.com/${GITHUB_REPOSITORY:-}" \
    --label "org.opencontainers.image.version=$VERSION" \
    --label "org.opencontainers.image.revision=$COMMIT" \
    -t "$image:$VERSION" -t "$image:sha-$COMMIT" -f "$2" "$3"
  docker push "$image:$VERSION"
  docker push "$image:sha-$COMMIT"
  echo "publicada: $image:$VERSION e $image:sha-$COMMIT"
}

build backend "$ROOT/backend/Dockerfile" "$ROOT/backend"
build nginx "$ROOT/nginx/Dockerfile" "$ROOT"
build backup "$ROOT/backup/Dockerfile" "$ROOT"
