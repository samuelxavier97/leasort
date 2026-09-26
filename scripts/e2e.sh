#!/usr/bin/env bash
# E2E com Playwright (Fase 10, D-104): PostgreSQL descartável, backend (jar, perfil dev) e o build
# de produção do frontend no `vite preview`. Argumentos extras vão para o `playwright test`
# (ex.: --repeat-each=20 --workers=4).
# Requer Java 21, Node 22+, Docker e as portas 5433, 8080 e 4173 livres.
# E2E_SKIP_BUILD=1 reaproveita o jar e o dist já gerados. E2E_CHROMIUM_PATH usa um Chromium local.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_CONTAINER="leasort-e2e-db"
DB_PORT=5433
LOG_DIR="$ROOT/frontend/test-results/e2e-logs"

port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

busy=()
for port in "$DB_PORT" 8080 4173; do
  if port_busy "$port"; then busy+=("$port"); fi
done
if [ ${#busy[@]} -gt 0 ]; then
  echo "E2E: porta(s) ocupada(s): ${busy[*]}. Pare o backend (8080), o vite preview (4173) ou o banco (5433) antes de rodar." >&2
  exit 1
fi

if grep -rn "waitForTimeout" "$ROOT/frontend/e2e" >/dev/null; then
  echo "E2E: waitForTimeout encontrado em frontend/e2e; espere por uma condição visível (D-104)." >&2
  exit 1
fi

if [ "${E2E_SKIP_BUILD:-}" != "1" ]; then
  echo "==> Backend: jar (sem testes)"
  (cd "$ROOT/backend" && ./mvnw -B -q -DskipTests package)
  echo "==> Frontend: build"
  (cd "$ROOT/frontend" && npm run build >/dev/null)
fi
JAR="$(ls "$ROOT"/backend/target/platform-*.jar | grep -v plain | head -n 1)"

BACKEND_PID=""
PREVIEW_PID=""
cleanup() {
  [ -n "$PREVIEW_PID" ] && kill "$PREVIEW_PID" 2>/dev/null || true
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null || true
  docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT
mkdir -p "$LOG_DIR"

# wait_for <descrição> <segundos> <comando...>: repete o comando até ele passar.
wait_for() {
  local what="$1" limit="$2"
  shift 2
  for _ in $(seq "$limit"); do
    if "$@" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "E2E: $what não ficou pronto em ${limit}s." >&2
  return 1
}

echo "==> Banco limpo (PostgreSQL 16 em memória, porta $DB_PORT)"
docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
docker run -d --rm --name "$DB_CONTAINER" -p "$DB_PORT:5432" --tmpfs /var/lib/postgresql/data \
  -e POSTGRES_DB=resort -e POSTGRES_USER=resort -e POSTGRES_PASSWORD=resort postgres:16-alpine >/dev/null
wait_for "PostgreSQL" 60 docker exec "$DB_CONTAINER" pg_isready -U resort -d resort -h 127.0.0.1

# Credenciais fictícias, novas a cada execução.
export E2E_ADMIN_EMAIL="admin@e2e.local"
E2E_BOOTSTRAP_PASSWORD="bootstrap-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
E2E_ADMIN_PASSWORD="admin-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
export E2E_BOOTSTRAP_PASSWORD E2E_ADMIN_PASSWORD

echo "==> Backend (perfil dev, banco do E2E)"
SPRING_PROFILES_ACTIVE=dev \
  DB_URL="jdbc:postgresql://localhost:$DB_PORT/resort" DB_USER=resort DB_PASSWORD=resort \
  APP_BOOTSTRAP_ADMIN_EMAIL="$E2E_ADMIN_EMAIL" APP_BOOTSTRAP_ADMIN_PASSWORD="$E2E_BOOTSTRAP_PASSWORD" \
  java -jar "$JAR" >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
wait_for "backend" 120 curl -sf http://localhost:8080/actuator/health

echo "==> Frontend (vite preview)"
(cd "$ROOT/frontend" && exec ./node_modules/.bin/vite preview --port 4173 --strictPort >"$LOG_DIR/preview.log" 2>&1) &
PREVIEW_PID=$!
wait_for "vite preview" 60 curl -sf http://localhost:4173/

echo "==> Playwright"
cd "$ROOT/frontend"
npx playwright test "$@"
