#!/usr/bin/env bash
# CI local: build e testes do backend e do frontend (D-035).
# Requer Java 21, Node 22+ e Docker (Testcontainers).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Backend: build e testes"
(cd "$ROOT/backend" && ./mvnw -B verify)

echo "==> Frontend: lint, typecheck, testes e build"
cd "$ROOT/frontend"
npm ci
npm run lint
npm run typecheck
npm test
npm run build

echo "==> OK"
