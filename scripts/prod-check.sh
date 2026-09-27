#!/usr/bin/env bash
# Verificação da pilha de produção (Fase 11a, D-105): constrói as imagens, sobe o
# docker-compose.prod.yml com um .env descartável (senhas aleatórias, dados fictícios) e confere a
# pilha por fora, como um cliente. No fim, derruba tudo e apaga os volumes, em qualquer caso.
# Requer Docker com o plugin compose, curl e python3; porta PROD_CHECK_HTTP_PORT (18080) livre.
# PROD_CHECK_SKIP_BUILD=1 usa as imagens já existentes (BACKEND_IMAGE e NGINX_IMAGE).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT=resort-check
HTTP_PORT="${PROD_CHECK_HTTP_PORT:-18080}"
BASE="http://localhost:$HTTP_PORT"
export BACKEND_IMAGE="${BACKEND_IMAGE:-resort-backend:check}"
export NGINX_IMAGE="${NGINX_IMAGE:-resort-nginx:check}"
# Nome fictício com aspas, < e &: precisa chegar intacto ao frontend sem virar marcação (D-087).
RESORT_NAME_CHECK='Resort "Fictício" <b>Águas</b> & Sol'

if (exec 3<>"/dev/tcp/127.0.0.1/$HTTP_PORT") 2>/dev/null; then
  echo "prod-check: porta $HTTP_PORT ocupada." >&2
  exit 1
fi

if [ "${PROD_CHECK_SKIP_BUILD:-}" != "1" ]; then
  echo "==> Imagens"
  docker build -q -t "$BACKEND_IMAGE" "$ROOT/backend" >/dev/null
  docker build -q -t "$NGINX_IMAGE" -f "$ROOT/nginx/Dockerfile" "$ROOT" >/dev/null
fi

WORK="$(mktemp -d)"
ENV_FILE="$WORK/.env.prod"
secret() { od -An -N18 -tx1 /dev/urandom | tr -d ' \n'; }
ADMIN_EMAIL="admin@prod-check.local"
ADMIN_PASSWORD="inicial-$(secret)"
cat >"$ENV_FILE" <<EOF
BACKEND_IMAGE=$BACKEND_IMAGE
NGINX_IMAGE=$NGINX_IMAGE
HTTP_PORT=$HTTP_PORT
POSTGRES_SUPERUSER_PASSWORD=$(secret)
DB_OWNER_PASSWORD=$(secret)
DB_APP_PASSWORD=$(secret)
DB_BACKUP_PASSWORD=$(secret)
APP_BOOTSTRAP_ADMIN_EMAIL=$ADMIN_EMAIL
APP_BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASSWORD
INTERNAL_SUBNET=172.30.250.0/24
EOF
# Valor com aspas no .env do compose: entre aspas simples, sem interpolação.
printf "RESORT_NAME='%s'\n" "$RESORT_NAME_CHECK" >>"$ENV_FILE"

compose() { docker compose -p "$PROJECT" -f "$ROOT/docker-compose.prod.yml" --project-directory "$ROOT" --env-file "$ENV_FILE" "$@"; }

cleanup() {
  trap - INT TERM
  compose down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

FAILURES=0
pass() { echo "  ok   $*"; }
fail() { echo "  FALHOU $*"; FAILURES=$((FAILURES + 1)); }
check() { # check <descrição> <comando...>
  local what="$1"
  shift
  if "$@"; then pass "$what"; else fail "$what"; fi
}

echo "==> Configuração do compose"
compose config --format json >"$WORK/config.json"
python3 - "$WORK/config.json" <<'PY' || FAILURES=$((FAILURES + 1))
import json, sys
services = json.load(open(sys.argv[1]))["services"]
problems = []
for name, service in services.items():
    ports = service.get("ports", [])
    if name == "nginx":
        if [p.get("target") for p in ports] != [8080]:
            problems.append(f"nginx publica {ports}")
    elif ports:
        problems.append(f"{name} publica portas: {ports}")
    logging = service.get("logging", {})
    if logging.get("driver") != "json-file" or "max-size" not in logging.get("options", {}):
        problems.append(f"{name} sem rotação de log")
    if name != "migrate":
        if service.get("restart") != "unless-stopped":
            problems.append(f"{name} sem restart unless-stopped")
        if "healthcheck" not in service:
            problems.append(f"{name} sem healthcheck")
    if "mem_limit" not in service:
        problems.append(f"{name} sem limite de memória")
for problem in problems:
    print(f"  FALHOU {problem}")
print("  ok   só o Nginx publica porta; rotação de log, restart, healthcheck e memória em todos" if not problems else "", end="")
print()
sys.exit(1 if problems else 0)
PY

echo "==> Subida"
compose up -d >/dev/null 2>&1
healthy=""
for _ in $(seq 180); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q nginx 2>/dev/null)" 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then healthy=1; break; fi
  sleep 1
done
if [ -z "$healthy" ]; then
  compose ps -a
  compose logs --tail 80
  echo "prod-check: a pilha não ficou saudável em 180 s." >&2
  exit 1
fi
pass "pilha saudável (migrate concluído, backend pronto, nginx no ar)"
check "migrate terminou com código 0" \
  test "$(docker inspect -f '{{.State.ExitCode}}' "$(compose ps -a -q migrate)")" = "0"

echo "==> Login logo depois do readiness (D-059)"
JAR="$WORK/cookies.txt"
curl -s -c "$JAR" -o /dev/null "$BASE/api/auth/me"
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" | tail -1; }
LOGIN_STATUS="$(curl -s -b "$JAR" -c "$JAR" -D "$WORK/login.headers" -o "$WORK/login.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" "$BASE/api/auth/login")"
check "login do ADMIN inicial assim que a pilha fica saudável" test "$LOGIN_STATUS" = "200"
check "cookie SESSION com Secure e HttpOnly" grep -qiE '^set-cookie: SESSION=.*; Secure.*HttpOnly|^set-cookie: SESSION=.*HttpOnly.*Secure' "$WORK/login.headers"
check "cookie XSRF-TOKEN com Secure" grep -qiE '^set-cookie: XSRF-TOKEN=.*Secure' "$WORK/login.headers"

echo "==> A aplicação funciona conectada como resort_app (D-057)"
NEW_PASSWORD="nova-$(secret)"
check "troca obrigatória de senha" test "$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data "{\"currentPassword\":\"$ADMIN_PASSWORD\",\"newPassword\":\"$NEW_PASSWORD\"}" \
  "$BASE/api/auth/change-password")" = "204"
check "criação de Lead fictício" test "$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data '{"name":"Lead Fictício da Verificação"}' "$BASE/api/leads")" = "201"
psql_q() { compose exec -T postgres psql -U postgres -d resort -tAc "$1"; }
APP_USERS="$(psql_q "SELECT string_agg(DISTINCT usename, ',') FROM pg_stat_activity WHERE datname = 'resort' AND application_name LIKE 'PostgreSQL JDBC%'")"
check "as conexões da aplicação são só de resort_app (encontrado: $APP_USERS)" test "$APP_USERS" = "resort_app"
check "resort_app não é dono de nenhuma tabela" \
  test "$(psql_q "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner <> 'resort_owner'")" = "0"

echo "==> Health público e actuator"
check "/actuator/health responde exatamente {\"status\":\"UP\"}" \
  test "$(curl -s "$BASE/actuator/health")" = '{"status":"UP"}'
for path in /actuator/env /actuator/health/readiness /actuator/health/liveness /v3/api-docs; do
  check "$path não é público (404)" test "$(curl -s -o /dev/null -w '%{http_code}' "$BASE$path")" = "404"
done

echo "==> Imagens sem root"
check "backend roda como uid 10001" test "$(compose exec -T backend id -u)" = "10001"
check "nginx roda como uid 101" test "$(compose exec -T nginx id -u)" = "101"

echo "==> Nome do Resort injetado na subida (D-087)"
curl -s "$BASE/" >"$WORK/index.html"
python3 - "$WORK/index.html" "$RESORT_NAME_CHECK" <<'PY' && pass "nome com aspas, < e & chega intacto e não vira marcação" || fail "nome do Resort no index.html"
import sys
from html.parser import HTMLParser
html, expected = open(sys.argv[1]).read(), sys.argv[2]
class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.meta, self.tags = None, []
    def handle_starttag(self, tag, attrs):
        self.tags.append(tag)
        attrs = dict(attrs)
        if tag == "meta" and attrs.get("name") == "resort-name":
            self.meta = attrs.get("content")
page = Page()
page.feed(html)
assert page.meta == expected, page.meta
assert page.tags.count("script") == 1, page.tags  # só o módulo do build
assert "b" not in page.tags, page.tags  # o <b> do nome não virou elemento
PY
EMPTY_META="$(docker run --rm --entrypoint sh "$NGINX_IMAGE" -c \
  '/docker-entrypoint.d/40-resort-name.sh && grep -o "<meta name=\"resort-name\"[^>]*>" /usr/share/nginx/html/index.html')"
check "sem RESORT_NAME a meta fica vazia (o frontend mostra \"Resort\")" \
  test "$EMPTY_META" = '<meta name="resort-name" content="" />'

echo "==> Memória"
HEAP_MB="$(compose exec -T backend java -XX:+PrintFlagsFinal -version 2>/dev/null | awk '$2 == "MaxHeapSize" { printf "%d", $4 / 1048576 }')"
check "heap máximo da JVM em 60% do limite de 768 MB (${HEAP_MB} MB)" test "$HEAP_MB" -ge 440 -a "$HEAP_MB" -le 470
check "shared_buffers do PostgreSQL em 128MB" test "$(psql_q 'SHOW shared_buffers')" = "128MB"
docker stats --no-stream --format '  uso  {{.Name}}: {{.MemUsage}}' $(compose ps -q)

if [ "$FAILURES" -gt 0 ]; then
  echo "==> $FAILURES verificação(ões) falharam"
  exit 1
fi
echo "==> OK"
