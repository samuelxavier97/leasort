#!/usr/bin/env bash
# Verificação da pilha de produção (Fase 11a, D-105): constrói as imagens, sobe o
# docker-compose.prod.yml com um .env descartável (senhas aleatórias, dados fictícios) e um certificado
# de uma CA local descartável, e confere a pilha por fora, como um cliente, por HTTP e HTTPS. Roda
# também a suíte E2E contra a pilha por HTTPS. No fim, derruba tudo e apaga os volumes, em qualquer caso.
# Requer Docker com o plugin compose, curl, openssl, python3 e as portas 18080 e 18443 livres; para o
# E2E, as dependências do frontend e o Chromium do Playwright (ou E2E_CHROMIUM_PATH).
# PROD_CHECK_SKIP_BUILD=1 usa as imagens já existentes (BACKEND_IMAGE e NGINX_IMAGE).
# PROD_CHECK_E2E=0 pula a suíte E2E.
# Não abra a pilha local num navegador de uso pessoal: o HSTS de https://localhost ficaria gravado nele.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT=resort-check
HTTP_PORT=18080
HTTPS_PORT=18443
DOMAIN=localhost
HTTP_BASE="http://$DOMAIN:$HTTP_PORT"
BASE="https://$DOMAIN:$HTTPS_PORT"
NGINX_IP=172.30.250.10
BACKEND_IMAGE="${BACKEND_IMAGE:-resort-backend:check}"
NGINX_IMAGE="${NGINX_IMAGE:-resort-nginx:check}"
# Sem export: o compose lê as imagens do .env.prod, como no servidor (o ensaio do runbook as troca lá).
BACKUP_IMAGE="${BACKUP_IMAGE:-resort-backup:check}"
# Nome fictício com aspas, < e &: precisa chegar intacto ao frontend sem virar marcação (D-087).
RESORT_NAME_CHECK='Resort "Fictício" <b>Águas</b> & Sol'
EXPECTED_CSP="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; font-src 'self'; media-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

for port in "$HTTP_PORT" "$HTTPS_PORT"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
    echo "prod-check: porta $port ocupada." >&2
    exit 1
  fi
done

if [ "${PROD_CHECK_SKIP_BUILD:-}" != "1" ]; then
  echo "==> Imagens"
  docker build -q -t "$BACKEND_IMAGE" "$ROOT/backend" >/dev/null
  docker build -q -t "$NGINX_IMAGE" -f "$ROOT/nginx/Dockerfile" "$ROOT" >/dev/null
  docker build -q -t "$BACKUP_IMAGE" -f "$ROOT/backup/Dockerfile" "$ROOT" >/dev/null
fi

WORK="$(mktemp -d)"
ENV_FILE="$WORK/.env.prod"
secret() { od -An -N18 -tx1 /dev/urandom | tr -d ' \n'; }
ADMIN_EMAIL="admin@prod-check.local"
BOOTSTRAP_PASSWORD="inicial-$(secret)"
ADMIN_PASSWORD="admin-$(secret)"
SUPERUSER_PASSWORD="$(secret)"
# Par de chaves do age descartável: a pública vai para o .env; a privada fica só no WORK (D-110).
docker run --rm "$BACKUP_IMAGE" age-keygen >"$WORK/backup.key" 2>/dev/null
AGE_RECIPIENT="$(sed -n 's/^# public key: //p' "$WORK/backup.key")"
cat >"$ENV_FILE" <<EOF
BACKEND_IMAGE=$BACKEND_IMAGE
NGINX_IMAGE=$NGINX_IMAGE
BACKUP_IMAGE=$BACKUP_IMAGE
BACKUP_AGE_RECIPIENT=$AGE_RECIPIENT
DOMAIN=$DOMAIN
HTTP_PORT=$HTTP_PORT
HTTPS_PORT=$HTTPS_PORT
HTTP_BIND=127.0.0.1
HTTPS_BIND=127.0.0.1
POSTGRES_SUPERUSER_PASSWORD=$SUPERUSER_PASSWORD
DB_OWNER_PASSWORD=$(secret)
DB_APP_PASSWORD=$(secret)
DB_BACKUP_PASSWORD=$(secret)
APP_BOOTSTRAP_ADMIN_EMAIL=$ADMIN_EMAIL
APP_BOOTSTRAP_ADMIN_PASSWORD=$BOOTSTRAP_PASSWORD
INTERNAL_SUBNET=172.30.250.0/24
NGINX_INTERNAL_IP=$NGINX_IP
BACKUP_HEALTH_INTERVAL=5s
EOF
# Valor com aspas no .env do compose: entre aspas simples, sem interpolação.
printf "RESORT_NAME='%s'\n" "$RESORT_NAME_CHECK" >>"$ENV_FILE"
# Tema fictício (D-115): cor em maiúsculas (o Nginx normaliza) e o logotipo fictício do E2E.
BRAND_COLOR_CHECK='#1E3A5F'
BRAND_LOGO_FIXTURE="$ROOT/frontend/e2e/fixtures/brand/logo.png"
mkdir -p "$WORK/brand"
cp "$BRAND_LOGO_FIXTURE" "$WORK/brand/logo.png"
printf "BRAND_COLOR='%s'\nBRAND_DIR=%s\n" "$BRAND_COLOR_CHECK" "$WORK/brand" >>"$ENV_FILE"

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
tls() { curl -s --cacert "$WORK/certs/ca.crt" "$@"; }
header() { # header <arquivo de cabeçalhos> <nome>: valor do cabeçalho (último), sem \r
  grep -i "^$2:" "$1" | tail -1 | cut -d: -f2- | sed -e 's/^ //' -e 's/\r$//'
}
header_count() { grep -ci "^$2:" "$1" || true; }

echo "==> Configuração do compose"
compose config --format json >"$WORK/config.json"
python3 - "$WORK/config.json" <<'PY' || FAILURES=$((FAILURES + 1))
import json, sys
services = json.load(open(sys.argv[1]))["services"]
problems = []
for name, service in services.items():
    ports = service.get("ports", [])
    if name == "nginx":
        if sorted(p.get("target") for p in ports) != [8080, 8443]:
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
if not problems:
    print("  ok   só o Nginx publica portas (80 e 443); rotação de log, restart, healthcheck e memória em todos")
sys.exit(1 if problems else 0)
PY

echo "==> Certificado local (CA descartável) e subida"
"$ROOT/scripts/local-cert.sh" "$WORK/certs" "$DOMAIN" >/dev/null
# O certificado vai para o volume letsencrypt no mesmo layout do Let's Encrypt (D-109).
install_cert() {
  compose up --no-start >/dev/null 2>&1
  docker run --rm --user root --entrypoint sh -v "${PROJECT}_letsencrypt:/le" -v "$WORK/certs:/src:ro" "$NGINX_IMAGE" \
    -c 'mkdir -p /le/live && cp -r /src/live/. /le/live/ && chmod -R a+rX /le'
}
wait_healthy() { # wait_healthy <serviço>: até 180 s
  for _ in $(seq 180); do
    status="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q "$1" 2>/dev/null)" 2>/dev/null || true)"
    if [ "$status" = "healthy" ]; then return 0; fi
    sleep 1
  done
  compose ps -a
  compose logs --tail 80
  echo "prod-check: $1 não ficou saudável em 180 s." >&2
  return 1
}
install_cert
compose up -d >/dev/null 2>&1
wait_healthy nginx || exit 1
pass "pilha saudável (migrate concluído, backend pronto, nginx no ar)"
check "migrate terminou com código 0" \
  test "$(docker inspect -f '{{.State.ExitCode}}' "$(compose ps -a -q migrate)")" = "0"
check "o certificado da CA local vale para $DOMAIN (sem a CA, o curl recusa)" \
  bash -c "curl -s --cacert '$WORK/certs/ca.crt' -o /dev/null '$BASE/' && ! curl -s -o /dev/null '$BASE/'"

if [ "${PROD_CHECK_E2E:-1}" != "0" ]; then
  echo "==> E2E contra a pilha por HTTPS"
  if (cd "$ROOT/frontend" && E2E_BASE_URL="$BASE" E2E_ADMIN_EMAIL="$ADMIN_EMAIL" E2E_SENSITIVE_FILE="$WORK/sensitive.txt" \
      E2E_BOOTSTRAP_PASSWORD="$BOOTSTRAP_PASSWORD" E2E_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
      npx playwright test --reporter=line) >"$WORK/e2e.log" 2>&1; then
    pass "suíte E2E inteira por HTTPS, sem violação de CSP ($(grep -Eo '[0-9]+ passed' "$WORK/e2e.log" | tail -1))"
  else
    tail -60 "$WORK/e2e.log"
    fail "suíte E2E por HTTPS"
  fi
  CURRENT_PASSWORD="$ADMIN_PASSWORD"
else
  CURRENT_PASSWORD="$BOOTSTRAP_PASSWORD"
fi

echo "==> Login, cookies Secure e resort_app (D-054, D-057, D-059)"
JAR="$WORK/cookies.txt"
tls -c "$JAR" -o /dev/null "$BASE/api/auth/me"
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" | tail -1; }
LOGIN_STATUS="$(tls -b "$JAR" -c "$JAR" -D "$WORK/login.headers" -o "$WORK/login.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$CURRENT_PASSWORD\"}" "$BASE/api/auth/login")"
check "login do ADMIN por HTTPS" test "$LOGIN_STATUS" = "200"
check "cookie SESSION com Secure, HttpOnly e SameSite=Lax" \
  bash -c "grep -i '^set-cookie: SESSION=' '$WORK/login.headers' | grep -i 'Secure' | grep -i 'HttpOnly' | grep -qi 'SameSite=Lax'"
check "cookie XSRF-TOKEN com Secure e SameSite=Lax, sem HttpOnly" \
  bash -c "grep -i '^set-cookie: XSRF-TOKEN=' '$WORK/login.headers' | grep -i 'Secure' | grep -i 'SameSite=Lax' | grep -viq 'HttpOnly'"
if grep -q '"mustChangePassword":true' "$WORK/login.json"; then
  check "troca obrigatória de senha" test "$(tls -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
    --data "{\"currentPassword\":\"$CURRENT_PASSWORD\",\"newPassword\":\"$ADMIN_PASSWORD\"}" \
    "$BASE/api/auth/change-password")" = "204"
fi
check "criação de Lead fictício" test "$(tls -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data '{"name":"Lead Fictício da Verificação"}' "$BASE/api/leads")" = "201"
psql_q() { compose exec -T -u postgres postgres psql -d resort -tAc "$1"; }
APP_SESSIONS="$(psql_q "SELECT string_agg(DISTINCT usename || '@' || host(client_addr), ',') FROM pg_stat_activity WHERE datname = 'resort' AND application_name LIKE 'PostgreSQL JDBC%'")"
BACKEND_IP="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$(compose ps -q backend)")"
check "as conexões da aplicação são de resort_app, vindas do backend (encontrado: $APP_SESSIONS)" \
  test "$APP_SESSIONS" = "resort_app@$BACKEND_IP"
check "resort_app não é dono de nenhuma tabela" \
  test "$(psql_q "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner <> 'resort_owner'")" = "0"

echo "==> pg_hba: superusuário só pelo socket local; papéis só pela sub-rede interna (D-057)"
PG_IMAGE="$(docker inspect -f '{{.Config.Image}}' "$(compose ps -q postgres)")"
if docker run --rm --network "${PROJECT}_internal" -e PGPASSWORD="$SUPERUSER_PASSWORD" --entrypoint psql "$PG_IMAGE" \
    -h postgres -U postgres -d resort -tAc 'SELECT 1' >"$WORK/super.log" 2>&1; then
  fail "o superusuário conectou pela rede"
else
  check "o superusuário pela rede é recusado mesmo com a senha certa" grep -q 'pg_hba.conf rejects connection' "$WORK/super.log"
fi
check "o superusuário entra pelo socket local do container" test "$(psql_q 'SELECT current_user')" = "postgres"
if compose exec -T -u postgres postgres psql -U resort_app -d resort -tAc 'SELECT 1' >"$WORK/local-app.log" 2>&1; then
  fail "resort_app conectou pelo socket local"
else
  check "resort_app pelo socket local é recusado" grep -q 'pg_hba.conf rejects connection' "$WORK/local-app.log"
fi

echo "==> Redirecionamento e ACME (D-107, D-109)"
check "HTTP redireciona com 301 para HTTPS, mantendo caminho e query" \
  test "$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$HTTP_BASE/leads/123?pagina=2")" = "301 $BASE/leads/123?pagina=2"
docker run --rm --user root --entrypoint sh -v "${PROJECT}_acme-webroot:/acme" "$NGINX_IMAGE" \
  -c 'mkdir -p /acme/.well-known/acme-challenge && echo token-da-verificacao > /acme/.well-known/acme-challenge/teste && chmod -R a+rX /acme'
check "/.well-known/acme-challenge/ responde por HTTP, sem redirecionar" \
  test "$(curl -s "$HTTP_BASE/.well-known/acme-challenge/teste")" = "token-da-verificacao"

echo "==> TLS"
handshake() { echo | openssl s_client -connect "127.0.0.1:$HTTPS_PORT" -servername "$DOMAIN" "$@" >/dev/null 2>&1; }
check "TLS 1.3 aceito" handshake -tls1_3
check "TLS 1.2 aceito" handshake -tls1_2
check "TLS 1.1 recusado (com o cliente liberando protocolos antigos)" bash -c "! echo | openssl s_client -connect 127.0.0.1:$HTTPS_PORT -tls1_1 -cipher 'DEFAULT:@SECLEVEL=0' >/dev/null 2>&1"
check "TLS 1.0 recusado" bash -c "! echo | openssl s_client -connect 127.0.0.1:$HTTPS_PORT -tls1 -cipher 'DEFAULT:@SECLEVEL=0' >/dev/null 2>&1"
check "cifra fraca sem ECDHE (AES128-SHA) recusada" bash -c "! echo | openssl s_client -connect 127.0.0.1:$HTTPS_PORT -tls1_2 -cipher AES128-SHA >/dev/null 2>&1"

echo "==> Cabeçalhos de segurança em todo tipo de resposta (D-107)"
tls -o "$WORK/index.html" "$BASE/"
ASSET="$(grep -o '/assets/[^"]*\.js' "$WORK/index.html" | head -1)"
tls -D "$WORK/h-html" -o /dev/null "$BASE/"
tls -D "$WORK/h-deep" -o "$WORK/deep.html" "$BASE/leads/123"
tls -D "$WORK/h-asset" -o /dev/null "$BASE$ASSET"
tls -D "$WORK/h-missing" -o /dev/null "$BASE/assets/nao-existe.js"
tls -D "$WORK/h-api" -o "$WORK/api-404.json" "$BASE/api/nao-existe"
tls -D "$WORK/h-actuator" -o /dev/null "$BASE/actuator/env"
tls -D "$WORK/h-health" -o "$WORK/health.json" "$BASE/actuator/health"
for response in html deep asset missing api actuator health; do
  file="$WORK/h-$response"
  check "[$response] HSTS max-age=31536000, uma vez" \
    test "$(header "$file" Strict-Transport-Security)|$(header_count "$file" Strict-Transport-Security)" = "max-age=31536000|1"
  check "[$response] CSP exata" test "$(header "$file" Content-Security-Policy)" = "$EXPECTED_CSP"
  check "[$response] Permissions-Policy com camera=(self)" \
    test "$(header "$file" Permissions-Policy)" = "camera=(self), microphone=(), geolocation=(), payment=(), usb=()"
  check "[$response] X-Content-Type-Options, Referrer-Policy e X-Frame-Options, uma vez cada" test \
    "$(header "$file" X-Content-Type-Options)|$(header_count "$file" X-Content-Type-Options)|$(header "$file" Referrer-Policy)|$(header "$file" X-Frame-Options)|$(header_count "$file" X-Frame-Options)" \
    = "nosniff|1|strict-origin-when-cross-origin|DENY|1"
done

echo "==> SPA, cache e rotas bloqueadas"
check "link direto /leads/123 devolve o index.html (200)" \
  bash -c "head -1 '$WORK/h-deep' | grep -q ' 200' && grep -q 'name=\"resort-name\"' '$WORK/deep.html'"
check "/api/nao-existe é respondido pelo backend (Problem Details), não pelo index.html" \
  bash -c "grep -qi '^content-type: application/problem+json' '$WORK/h-api' && grep -q '\"code\"' '$WORK/api-404.json'"
check "/assets/nao-existe.js é 404, não o index.html" bash -c "head -1 '$WORK/h-missing' | grep -q ' 404'"
check "asset com hash: cache de um ano, immutable" \
  test "$(header "$WORK/h-asset" Cache-Control)" = "public, max-age=31536000, immutable"
check "index.html e rotas da SPA: no-cache" \
  test "$(header "$WORK/h-html" Cache-Control)|$(header "$WORK/h-deep" Cache-Control)" = "no-cache|no-cache"
check "/actuator/health por HTTPS responde exatamente {\"status\":\"UP\"}" test "$(cat "$WORK/health.json")" = '{"status":"UP"}'
for path in /actuator/env /actuator/health/readiness /actuator/health/liveness /v3/api-docs; do
  check "$path é 404" test "$(tls -o /dev/null -w '%{http_code}' "$BASE$path")" = "404"
done
check "a porta interna 8081 do backend não é alcançável do host" \
  bash -c "! curl -s -m 3 -o /dev/null http://127.0.0.1:8081/actuator/health/readiness"

echo "==> Tamanho do corpo e exportações"
python3 -c "import sys; sys.stdout.write('x' * (int(4.9 * 1024 * 1024)))" >"$WORK/grande.csv"
python3 -c "import sys; sys.stdout.write('x' * (7 * 1024 * 1024))" >"$WORK/enorme.csv"
IMPORT_OK="$(tls -b "$JAR" -c "$JAR" -D "$WORK/h-import" -o "$WORK/import.json" -w '%{http_code}' \
  -H "X-XSRF-TOKEN: $(xsrf)" -F "file=@$WORK/grande.csv;type=text/csv" "$BASE/api/leads/import")"
check "importação de 4,9 MB passa pelo Nginx e é respondida pelo backend ($IMPORT_OK)" \
  bash -c "[ '$IMPORT_OK' != 413 ] && grep -q '\"code\"' '$WORK/import.json'"
IMPORT_BIG="$(tls -b "$JAR" -c "$JAR" -o "$WORK/import-big.html" -w '%{http_code}' \
  -H "X-XSRF-TOKEN: $(xsrf)" -F "file=@$WORK/enorme.csv;type=text/csv" "$BASE/api/leads/import")"
check "corpo de 7 MB recebe 413 do próprio Nginx" \
  bash -c "[ '$IMPORT_BIG' = 413 ] && grep -q '<center>nginx</center>' '$WORK/import-big.html'"
compose exec -T nginx nginx -T 2>/dev/null >"$WORK/nginx-T.conf"
check "/api/exports/ sem buffer e com tempo-limite acima de 30 min" python3 - "$WORK/nginx-T.conf" <<'PY'
import re, sys
conf = open(sys.argv[1]).read()
block = re.search(r"location /api/exports/ \{(.*?)\n    \}", conf, re.S).group(1)
assert "proxy_buffering off;" in block, block
timeout = int(re.search(r"proxy_read_timeout (\d+)s;", block).group(1))
assert timeout >= 1800, timeout
PY

echo "==> IP real do cliente (D-108)"
login_fail() { # login_fail <e-mail> <X-Forwarded-For forjado>: status do login com senha errada
  tls -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "X-XSRF-TOKEN: $(xsrf)" -H "X-Forwarded-For: $2" \
    --data "{\"email\":\"$1\",\"password\":\"senha-errada\"}" "$BASE/api/auth/login"
}
login_fail "ip-$(secret)@prod-check.local" 203.0.113.9 >/dev/null
AUDITED_IP="$(psql_q "SELECT ip_address FROM audit_logs WHERE action = 'LOGIN_FAILED' ORDER BY created_at DESC LIMIT 1")"
SEEN_IP="$(compose logs --no-log-prefix nginx 2>/dev/null | grep '"POST /api/auth/login ' | tail -1 | awk '{ print $1 }')"
check "a auditoria grava o IP que o Nginx viu, não o forjado nem o do Nginx (gravado: $AUDITED_IP; visto pelo Nginx: $SEEN_IP; Nginx: $NGINX_IP)" \
  bash -c "[ -n '$SEEN_IP' ] && [ '$AUDITED_IP' = '$SEEN_IP' ] && [ '$AUDITED_IP' != 203.0.113.9 ] && [ '$AUDITED_IP' != '$NGINX_IP' ]"
LIMITED_EMAIL="limite-$(secret)@prod-check.local"
STATUSES=""
for i in 1 2 3 4 5 6; do
  STATUSES="$STATUSES $(login_fail "$LIMITED_EMAIL" "198.51.100.$i")"
done
check "5 falhas trocando o IP forjado: a 6ª recebe 429 (o limite usa o IP real):$STATUSES" \
  test "$STATUSES" = " 401 401 401 401 401 429"

echo "==> Certificados (D-109)"
printf 'DOMAIN=resort.example.invalid\nLETSENCRYPT_EMAIL=ops@example.invalid\nLETSENCRYPT_STAGING=true\n' >"$WORK/le.env"
check "issue-cert monta o certbot com domínio, e-mail e --staging" test \
  "$("$ROOT/scripts/issue-cert.sh" "$WORK/le.env" --print)" = \
  "certbot certonly --webroot -w /var/www/acme -d resort.example.invalid --email ops@example.invalid --agree-tos --no-eff-email --non-interactive --keep-until-expiring --staging"
sed -i 's/LETSENCRYPT_STAGING=true/LETSENCRYPT_STAGING=false/' "$WORK/le.env"
check "sem staging, o certbot vai para o ambiente real" \
  bash -c "! '$ROOT/scripts/issue-cert.sh' '$WORK/le.env' --print | grep -q -- --staging"
check "certbot fora da pilha padrão (só com COMPOSE_PROFILES=letsencrypt)" python3 - "$WORK/config.json" <<'PY'
import json, sys
assert "certbot" not in json.load(open(sys.argv[1]))["services"]
PY
check "certbot declarado no perfil letsencrypt com renovação a cada 12 h" \
  bash -c "COMPOSE_PROFILES=letsencrypt docker compose -p $PROJECT -f '$ROOT/docker-compose.prod.yml' --project-directory '$ROOT' --env-file '$ENV_FILE' config --format json | python3 -c 'import json,sys; c=json.load(sys.stdin)[\"services\"][\"certbot\"]; e=\" \".join(c[\"entrypoint\"]); assert \"certbot renew --webroot\" in e and \"sleep 12h\" in e, e'"
check "o Nginx recarrega a cada 6 h para pegar o certificado renovado" \
  bash -c "docker exec '$(compose ps -q nginx)' ps | grep -q 'sleep 21600'"

echo "==> Imagens sem root"
check "backend roda como uid 10001" test "$(compose exec -T backend id -u)" = "10001"
check "nginx roda como uid 101" test "$(compose exec -T nginx id -u)" = "101"

echo "==> Nome do Resort injetado na subida (D-087)"
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
check "sem RESORT_NAME a meta fica vazia (o frontend mostra \"Resortric\", D-117)" \
  test "$EMPTY_META" = '<meta name="resort-name" content="" />'

echo "==> Tema do cliente injetado na subida (D-115, D-119)"
brand_meta() { # brand_meta <arquivo html> <nome da meta>
  python3 - "$1" "$2" <<'PY'
import sys
from html.parser import HTMLParser
class Page(HTMLParser):
    value = None
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "meta" and attrs.get("name") == sys.argv[2]:
            Page.value = attrs.get("content")
Page().feed(open(sys.argv[1]).read())
print(Page.value)
PY
}
check "meta da cor com BRAND_COLOR normalizada para minúsculas" test "$(brand_meta "$WORK/index.html" resort-brand-color)" = "#1e3a5f"
check "meta do logotipo com /brand/logo.png" test "$(brand_meta "$WORK/index.html" resort-brand-logo)" = "/brand/logo.png"
tls -D "$WORK/h-brand" -o "$WORK/logo.served" "$BASE/brand/logo.png"
check "/brand/logo.png é o PNG do diretório, como image/png" bash -c \
  "head -1 '$WORK/h-brand' | grep -q ' 200' && cmp -s '$WORK/logo.served' '$BRAND_LOGO_FIXTURE' && grep -qi '^content-type: image/png' '$WORK/h-brand'"
check "/brand/: CSP geral e CSP própria com sandbox (D-119)" test \
  "$(grep -i '^content-security-policy:' "$WORK/h-brand" | cut -d: -f2- | sed -e 's/^ //' -e 's/\r$//' | sort | tr '\n' '|')" \
  = "$(printf '%s\n' "default-src 'none'; sandbox" "$EXPECTED_CSP" | sort | tr '\n' '|')"
check "/brand/: nosniff, HSTS e no-cache" test \
  "$(header "$WORK/h-brand" X-Content-Type-Options)|$(header_count "$WORK/h-brand" Strict-Transport-Security)|$(header "$WORK/h-brand" Cache-Control)" \
  = "nosniff|1|no-cache"
check "/brand/nao-existe.png é 404" test "$(tls -o /dev/null -w '%{http_code}' "$BASE/brand/nao-existe.png")" = "404"

make_png() { # make_png <arquivo> <largura> <altura> [tamanho exato em bytes]
  python3 - "$@" <<'PY'
import struct, sys, zlib
path, width, height = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
size = int(sys.argv[4]) if len(sys.argv) > 4 else 0
def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
rows = b"".join(b"\0" + bytes((30, 58, 95)) * width for _ in range(height))
body = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
body += chunk(b"IDAT", zlib.compress(rows, 9))
end = chunk(b"IEND", b"")
if size:  # bloco auxiliar privado até o tamanho pedido
    body += chunk(b"prVt", b"\0" * (size - len(body) - len(end) - 12))
open(path, "wb").write(body + end)
PY
}
brand_run() { # brand_run <diretório> [BRAND_COLOR]: os dois scripts da subida, fora da pilha; saída e erro
  docker run --rm -e "BRAND_COLOR=${2:-}" -v "$1:/etc/resort/brand:ro" --entrypoint sh "$NGINX_IMAGE" -c \
    '/docker-entrypoint.d/40-resort-name.sh && /docker-entrypoint.d/41-resort-brand.sh && grep -o "<meta name=\"resort-brand[^>]*>" /usr/share/nginx/html/index.html | tr "\n" " " && ls /usr/share/nginx/html/brand 2>/dev/null' 2>&1
}
brand_case() { # brand_case <descrição> <conteúdo do diretório: vazio|svg|texto|assinatura|WxH[:bytes]> [BRAND_COLOR] <esperado na saída>
  local what="$1" content="$2" color="$3" expected="$4" dir
  dir="$(mktemp -d "$WORK/brand-case.XXXX")"
  chmod 755 "$dir"
  case "$content" in
    vazio) ;;
    svg) echo '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"/>' >"$dir/logo.svg" ;;
    texto) echo 'isto não é um PNG' >"$dir/logo.png" ;;
    assinatura) printf '\x89PNG\r\n\x1a\n' >"$dir/logo.png" ;;
    sem-leitura) make_png "$dir/logo.png" 64 64 ;;
    *) local size="${content#*:}"; [ "$size" = "$content" ] && size=""
       make_png "$dir/logo.png" "${content%%x*}" "$(echo "${content%%:*}" | cut -dx -f2)" $size ;;
  esac
  # Legível pelo uid 101 do Nginx, a não ser no caso que testa justamente o contrário.
  if [ -e "$dir/logo.png" ]; then
    if [ "$content" = sem-leitura ]; then chmod 600 "$dir/logo.png"; else chmod 644 "$dir/logo.png"; fi
  fi
  local output
  output="$(brand_run "$dir" "$color")" && status=0 || status=$?
  if [ "$expected" = "ok" ]; then
    check "$what" bash -c "[ $status = 0 ] && grep -q 'content=\"/brand/logo.png\"' <<<'$output'"
  else
    check "$what (\"$expected\")" bash -c "[ $status != 0 ] && grep -qF -- '$expected' <<<'$output'"
  fi
}
# O ls final falha sem /brand/, de propósito: aqui só a saída importa.
EMPTY_BRAND="$(brand_run "$(mktemp -d "$WORK/brand-empty.XXXX")" || true)"
check "sem tema, as metas ficam vazias e não há /brand/" \
  test "$EMPTY_BRAND" = '<meta name="resort-brand-color" content="" /> <meta name="resort-brand-logo" content="" /> '
for color in '#fff' 'red' '#12345g' '#1e3a5f"><script>' $'#1e3a5f\nx' ' #1e3a5f'; do
  brand_case "cor $(printf '%q' "$color") recusada" vazio "$color" "BRAND_COLOR inválida"
done
brand_case "logotipo SVG recusado (só PNG)" svg "" "só PNG"
brand_case "arquivo de texto com nome logo.png recusado" texto "" "não é um PNG válido"
brand_case "PNG só com a assinatura, sem IHDR, recusado" assinatura "" "não é um PNG válido"
brand_case "PNG válido com modo 600 (ilegível pelo Nginx) recusado com mensagem clara" sem-leitura "" \
  "logo.png sem permissão de leitura: use chmod 644"
brand_case "PNG com 256 KB + 1 byte recusado" "64x64:262145" "" "o máximo é 262144"
brand_case "PNG com exatamente 256 KB aceito" "64x64:262144" "" ok
brand_case "PNG com 2049 px de largura recusado" "2049x1" "" "largura e altura vão de 1 a 2048"
brand_case "PNG com 2049 px de altura recusado" "1x2049" "" "largura e altura vão de 1 a 2048"
brand_case "PNG com 2048 × 2048 px aceito" "2048x2048" "" ok
FULL_START="$(timeout 60 docker run --rm -e BRAND_COLOR=red -e DOMAIN=localhost "$NGINX_IMAGE" 2>&1)" && FULL_STATUS=0 || FULL_STATUS=$?
check "a subida completa do container para com cor inválida, com o motivo no log" \
  bash -c "[ $FULL_STATUS != 0 ] && [ $FULL_STATUS != 124 ] && grep -q 'BRAND_COLOR inválida' <<<'$FULL_START'"

# Troca do logotipo sem rebuild: o arquivo muda, a imagem do container continua a mesma.
IMAGE_BEFORE="$(docker inspect -f '{{.Image}}' "$(compose ps -q nginx)")"
make_png "$WORK/brand/logo.png" 300 80
chmod 644 "$WORK/brand/logo.png"
compose restart nginx >/dev/null 2>&1
wait_healthy nginx || exit 1
tls -o "$WORK/logo.swapped" "$BASE/brand/logo.png"
check "trocar o logo.png e reiniciar serve o arquivo novo, com a mesma imagem" bash -c \
  "cmp -s '$WORK/logo.swapped' '$WORK/brand/logo.png' && [ '$(docker inspect -f '{{.Image}}' "$(compose ps -q nginx)")' = '$IMAGE_BEFORE' ]"
rm "$WORK/brand/logo.png"
compose restart nginx >/dev/null 2>&1
wait_healthy nginx || exit 1
tls -o "$WORK/index-nologo.html" "$BASE/"
check "sem o logo.png e reiniciado: /brand/logo.png é 404 e a meta fica vazia (a cópia anterior sai)" bash -c \
  "[ '$(tls -o /dev/null -w '%{http_code}' "$BASE/brand/logo.png")' = 404 ] && [ '$(brand_meta "$WORK/index-nologo.html" resort-brand-logo)' = '' ]"
cp "$BRAND_LOGO_FIXTURE" "$WORK/brand/logo.png"
compose restart nginx >/dev/null 2>&1
wait_healthy nginx || exit 1

echo "==> Memória"
HEAP_MB="$(compose exec -T backend java -XX:+PrintFlagsFinal -version 2>/dev/null | awk '$2 == "MaxHeapSize" { printf "%d", $4 / 1048576 }')"
check "heap máximo da JVM em 60% do limite de 768 MB (${HEAP_MB} MB)" test "$HEAP_MB" -ge 440 -a "$HEAP_MB" -le 470
check "shared_buffers do PostgreSQL em 128MB" test "$(psql_q 'SHOW shared_buffers')" = "128MB"
docker stats --no-stream --format '  uso  {{.Name}}: {{.MemUsage}}' $(compose ps -q)

echo "==> Exportação chega aos poucos pelo Nginx (D-101, D-107)"
# 100 mil Leads fictícios direto no banco (só na pilha descartável): a exportação passa de 20 MB.
psql_q "INSERT INTO leads (id, name, phone, email, status)
  SELECT gen_random_uuid(), 'Lead Volume ' || lpad(g::text, 6, '0') || ' ' || repeat('x', 90),
         '(11) 90000-0000', 'volume' || g || '@e2e.local', 'NEW'
  FROM generate_series(1, 100000) g" >/dev/null
EXPORT_FILE="$WORK/export.csv"
# Cliente lento (1 MB/s): com o Nginx sem buffer, o backend acompanha o ritmo do cliente.
tls -b "$JAR" -c "$JAR" --limit-rate 1M -o "$EXPORT_FILE" \
  -w '%{http_code} %{time_starttransfer} %{time_total} %{size_download}' "$BASE/api/exports/leads" >"$WORK/export.timing" &
EXPORT_PID=$!
sleep 6
PARTIAL_BYTES="$(stat -c %s "$EXPORT_FILE" 2>/dev/null || echo 0)"
STREAMING_TX="$(psql_q "SELECT count(*) FROM pg_stat_activity WHERE usename = 'resort_app'
  AND state IN ('active', 'idle in transaction') AND xact_start IS NOT NULL AND query ILIKE '%FROM leads%'")"
EXPORT_RUNNING=0
kill -0 "$EXPORT_PID" 2>/dev/null && EXPORT_RUNNING=1
wait "$EXPORT_PID" || true
read -r EXPORT_STATUS FIRST_BYTE TOTAL_TIME TOTAL_BYTES <"$WORK/export.timing" || true  # a saída do -w não termina em \n
check "aos 6 s o download ainda corre, com $PARTIAL_BYTES de $TOTAL_BYTES bytes recebidos" \
  bash -c "[ '$EXPORT_RUNNING' = 1 ] && [ '$PARTIAL_BYTES' -gt 0 ] && [ '$PARTIAL_BYTES' -lt '$TOTAL_BYTES' ]"
check "aos 6 s o backend ainda lê a exportação no banco (transação aberta: $STREAMING_TX)" test "$STREAMING_TX" -ge 1
check "primeiro byte em ${FIRST_BYTE}s, fim em ${TOTAL_TIME}s ($EXPORT_STATUS, $TOTAL_BYTES bytes)" python3 -c "
import sys
status, first, total, size = '$EXPORT_STATUS', float('$FIRST_BYTE'), float('$TOTAL_TIME'), int('$TOTAL_BYTES')
assert status == '200' and size > 15_000_000 and first < 3 and total > 12, (status, first, total, size)"
psql_q "DELETE FROM leads WHERE email LIKE 'volume%@e2e.local'" >/dev/null

echo "==> Logs sem CPF, código de convite nem senha (D-111)"
CPF_CHECK="$(python3 -c "
import random
while True:
    base = [random.randint(0, 9) for _ in range(9)]
    if len(set(base)) > 1: break
def digit(d):
    r = sum(v * (len(d) + 1 - i) for i, v in enumerate(d)) * 10 % 11
    return 0 if r == 10 else r
base.append(digit(base)); base.append(digit(base))
print(''.join(map(str, base)))")"
echo "$CPF_CHECK" >>"$WORK/sensitive.txt"
# CPF na query de propósito: o formato de log não grava a query (D-107).
tls -b "$JAR" -c "$JAR" -o /dev/null "$BASE/api/leads?cpf=$CPF_CHECK&q=$CPF_CHECK"
# Backend fora do ar: o log de erro do Nginx não pode gravar a linha do pedido com a query.
compose stop backend >/dev/null 2>&1
DOWN_STATUS="$(tls -m 90 -o /dev/null -w '%{http_code}' "$BASE/api/leads?cpf=$CPF_CHECK" || true)"
check "com o backend parado, a API responde 502 ou 504 (recebido: $DOWN_STATUS)" bash -c "[ '$DOWN_STATUS' = 502 ] || [ '$DOWN_STATUS' = 504 ]"
compose start backend >/dev/null 2>&1
wait_healthy backend || exit 1
compose logs --no-color --no-log-prefix >"$WORK/all.log" 2>&1
printf '%s\n%s\n%s\n' "$BOOTSTRAP_PASSWORD" "$ADMIN_PASSWORD" "$SUPERUSER_PASSWORD" >>"$WORK/sensitive.txt"
LEAKS="$(python3 - "$WORK/sensitive.txt" "$WORK/all.log" <<'PY'
import sys
values = {line.strip() for line in open(sys.argv[1]) if line.strip()}
log = open(sys.argv[2], errors="replace").read()
found = []
for value in values:
    variants = {value}
    if len(value) == 11 and value.isdigit():
        variants.add(f"{value[:3]}.{value[3:6]}.{value[6:9]}-{value[9:]}")
    if len(value) == 10 and value.isalnum() and not value.isdigit():
        variants.add(f"{value[:5]}-{value[5:]}")
    found += [v for v in variants if v in log]
print(len(values), " ".join(found))
PY
)"
FOUND="$(echo "$LEAKS" | cut -s -d' ' -f2-)"
check "nenhum dos $(echo "$LEAKS" | cut -d' ' -f1) valores sensíveis do teste (CPFs, códigos e senhas) aparece nos logs${FOUND:+ (encontrados: $FOUND)}" \
  test -z "$FOUND"
check "o log de acesso grava /api/leads sem a query" \
  bash -c "grep -q '\"GET /api/leads HTTP' '$WORK/all.log' && ! grep -q 'GET /api/leads?' '$WORK/all.log'"

echo "==> Backup cifrado e retenção (D-110)"
LEAD_CPF="$(python3 -c "
import random
while True:
    base = [random.randint(0, 9) for _ in range(9)]
    if len(set(base)) > 1: break
def digit(d):
    r = sum(v * (len(d) + 1 - i) for i, v in enumerate(d)) * 10 % 11
    return 0 if r == 10 else r
base.append(digit(base)); base.append(digit(base))
print(''.join(map(str, base)))")"
check "Lead com CPF fictício para o backup" test "$(tls -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data "{\"name\":\"Lead do Backup\",\"cpf\":\"$LEAD_CPF\"}" "$BASE/api/leads")" = "201"
CRON_LINE="$(compose exec -T backup cut -d' ' -f1-5 /etc/crontabs/root)"
TZ_LINE="$(compose exec -T backup grep '^export TZ=' /etc/backup.env)"
check "o backup roda às 03:00 no fuso da operação ($CRON_LINE; $TZ_LINE)" \
  test "$CRON_LINE|$TZ_LINE" = "0 3 * * *|export TZ='America/Sao_Paulo'"
# Saúde do backup pelo healthcheck do próprio Docker (intervalo de 5 s só nesta verificação).
health_output() { docker inspect -f '{{range .State.Health.Log}}{{.Output}}{{end}}' "$(compose ps -q backup)" | grep . | tail -1; }
# wait_health <healthy|unhealthy> <trecho da mensagem>: até 60 s, até o status do Docker e a mensagem da
# checagem mais recente baterem (o status sozinho pode vir de uma checagem anterior à mudança).
wait_health() {
  for _ in $(seq 60); do
    if [ "$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q backup)")" = "$1" ] \
      && health_output | grep -qF "$2"; then
      return 0
    fi
    sleep 1
  done
  return 1
}
check "pilha recém-criada, sem backup: saudável pela carência" wait_health healthy "nenhum backup ainda; carência"
# Subida há 27 h e nenhum backup: a carência acabou.
compose exec -T backup sh -c 'echo $(( $(date +%s) - 27 * 3600 )) >/run/backup-started'
check "sem backup 27 h depois da subida: unhealthy" wait_health unhealthy "nenhum backup em /backups após 26 h"
compose exec -T backup sh -c 'date +%s >/run/backup-started'
check "de volta à carência: saudável" wait_health healthy "nenhum backup ainda; carência"

OLD="$(date -u -d '15 days ago' +%Y%m%d%H%M)"
RECENT="$(date -u -d '13 days ago' +%Y%m%d%H%M)"
compose exec -T backup sh -c "touch -t $OLD /backups/resort-19990101T000000Z.dump.age && touch -t $RECENT /backups/resort-19990102T000000Z.dump.age"
# Estado do banco logo antes do backup, para comparar depois da restauração.
db_state() {
  psql_q "SELECT (SELECT count(*) FROM users) || '/' || (SELECT count(*) FROM leads) || '/' || (SELECT count(*) FROM visits)
    || '/' || (SELECT count(*) FROM invitations) || '/' || (SELECT count(*) FROM access_records)
    || '/' || (SELECT count(*) FROM audit_logs) || '/' || (SELECT md5(string_agg(id::text || action || coalesce(metadata::text, ''), ',' ORDER BY id)) FROM audit_logs)"
}
STATE_BEFORE="$(db_state)"
BACKUP_LOG="$(compose exec -T backup backup.sh 2>&1)"
BACKUP_NAME="$(echo "$BACKUP_LOG" | sed -n 's/^backup: \(resort-[0-9TZ]*\.dump\.age\).*/\1/p')"
check "backup gerado ($BACKUP_NAME)" test -n "$BACKUP_NAME"
check "retenção: arquivo com 15 dias apagado, com 13 dias mantido" bash -c \
  "echo '$BACKUP_LOG' | grep -q 'apagado por retenção: /backups/resort-19990101T000000Z.dump.age' &&
   ! echo '$BACKUP_LOG' | grep -q 'resort-19990102T000000Z'"
docker cp "$(compose ps -q backup):/backups/$BACKUP_NAME" "$WORK/$BACKUP_NAME"
check "com um backup recente: saudável" wait_health healthy "ok, último $BACKUP_NAME"
# O último backup passa a ter 30 h: o backup parou de rodar.
compose exec -T backup sh -c "touch -t $(date -u -d '30 hours ago' +%Y%m%d%H%M) /backups/$BACKUP_NAME"
check "com o último backup de 30 h atrás: unhealthy" wait_health unhealthy "($BACKUP_NAME) tem mais de 26 h"
# Um reinício do container não esconde o backup parado (a carência só vale sem nenhum backup).
compose restart backup >/dev/null 2>&1
check "depois de reiniciar o container, continua unhealthy" wait_health unhealthy "($BACKUP_NAME) tem mais de 26 h"
compose exec -T backup sh -c "touch /backups/$BACKUP_NAME"
check "com o backup em dia de novo: saudável" wait_health healthy "ok, último $BACKUP_NAME"
check "o arquivo é cifrado com age" test "$(head -1 "$WORK/$BACKUP_NAME")" = "age-encryption.org/v1"
check "sem a chave, o arquivo não é um dump legível (o pg_restore recusa)" bash -c \
  "! docker run --rm -i '$BACKUP_IMAGE' pg_restore --list <'$WORK/$BACKUP_NAME' >/dev/null 2>&1"
# O formato custom do pg_dump já comprime os dados; esta checagem sozinha não provaria a criptografia.
check "o CPF do Lead não aparece no arquivo cifrado" bash -c "! grep -aq '$LEAD_CPF' '$WORK/$BACKUP_NAME'"
check "decifrado com a chave, o dump traz o CPF (o teste anterior vale)" bash -c \
  "docker run --rm -i -v '$WORK/backup.key:/k:ro' '$BACKUP_IMAGE' sh -c 'age -d -i /k | pg_restore -f -' <'$WORK/$BACKUP_NAME' 2>/dev/null | grep -c '$LEAD_CPF' >/dev/null"

echo "==> Restauração (D-110)"
docker run --rm "$BACKUP_IMAGE" age-keygen >"$WORK/other.key" 2>/dev/null
if COMPOSE_PROJECT_NAME="$PROJECT" "$ROOT/scripts/restore.sh" "$ENV_FILE" "$WORK/$BACKUP_NAME" "$WORK/other.key" >"$WORK/restore-wrong.log" 2>&1; then
  fail "restauração com a chave errada terminou com sucesso"
else
  check "com a chave errada, a restauração para com mensagem clara e não mexe no banco" bash -c \
    "grep -q 'a chave informada não decifra este backup; nada foi alterado.' '$WORK/restore-wrong.log' &&
     [ \"\$(docker inspect -f '{{.State.Health.Status}}' '$(compose ps -q backend)')\" = healthy ]"
fi
# Perda total da VPS: tudo é apagado; sobra só a cópia do backup fora do servidor (aqui, no WORK).
compose down -v >/dev/null 2>&1
install_cert
compose up -d postgres >/dev/null 2>&1
wait_healthy postgres || exit 1
if COMPOSE_PROJECT_NAME="$PROJECT" "$ROOT/scripts/restore.sh" "$ENV_FILE" "$WORK/$BACKUP_NAME" "$WORK/backup.key" >"$WORK/restore.log" 2>&1; then
  pass "restauração numa pilha nova, a partir da cópia do backup"
else
  cat "$WORK/restore.log"
  fail "restauração numa pilha nova"
fi
wait_healthy nginx || exit 1
STATE_RESTORED="$(db_state)"
check "contagens por tabela e conteúdo de audit_logs iguais aos de antes do backup" test "$STATE_RESTORED" = "$STATE_BEFORE"
JAR="$WORK/cookies-restored.txt"
tls -c "$JAR" -o /dev/null "$BASE/api/auth/me"
check "o ADMIN entra depois da restauração" test "$(tls -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(xsrf)" \
  --data "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" "$BASE/api/auth/login")" = "200"
check "depois da restauração, resort_app continua sem ownership" \
  test "$(psql_q "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner <> 'resort_owner'")" = "0"
APP_PASSWORD_VALUE="$(sed -n 's/^DB_APP_PASSWORD=//p' "$ENV_FILE")"
if docker run --rm --network "${PROJECT}_internal" -e PGPASSWORD="$APP_PASSWORD_VALUE" --entrypoint psql "$PG_IMAGE" \
    -h postgres -U resort_app -d resort -tAc 'DELETE FROM audit_logs' >"$WORK/app-delete.log" 2>&1; then
  fail "resort_app apagou audit_logs depois da restauração"
else
  check "depois da restauração, resort_app não apaga audit_logs" grep -q 'permission denied' "$WORK/app-delete.log"
fi
check "os triggers de audit_logs voltaram com o dump" \
  test "$(psql_q "SELECT count(*) FROM pg_trigger WHERE tgrelid = 'audit_logs'::regclass AND NOT tgisinternal AND tgenabled = 'O'")" = "2"

echo "==> Ensaio do runbook: atualização de versão e rollback (docs/DEPLOY.md)"
STATE_BEFORE_UPDATE="$(db_state)"
docker tag "$BACKEND_IMAGE" resort-backend:ensaio-nova
docker tag "$NGINX_IMAGE" resort-nginx:ensaio-nova
sed -i -e 's|^BACKEND_IMAGE=.*|BACKEND_IMAGE=resort-backend:ensaio-nova|' -e 's|^NGINX_IMAGE=.*|NGINX_IMAGE=resort-nginx:ensaio-nova|' "$ENV_FILE"
compose up -d >/dev/null 2>&1
wait_healthy nginx || exit 1
MIGRATE_IMAGE="$(docker inspect -f '{{.Config.Image}}' $(compose ps -a -q migrate) | sort -u | tr '\n' ' ')"
BACKEND_RUNNING_IMAGE="$(docker inspect -f '{{.Config.Image}}' "$(compose ps -q backend)")"
check "atualização: migrate e backend recriados com a versão nova (migrate: $MIGRATE_IMAGE; backend: $BACKEND_RUNNING_IMAGE)" \
  test "$MIGRATE_IMAGE|$BACKEND_RUNNING_IMAGE" = "resort-backend:ensaio-nova |resort-backend:ensaio-nova"
check "atualização: dados iguais aos de antes" test "$(db_state)" = "$STATE_BEFORE_UPDATE"
sed -i -e "s|^BACKEND_IMAGE=.*|BACKEND_IMAGE=$BACKEND_IMAGE|" -e "s|^NGINX_IMAGE=.*|NGINX_IMAGE=$NGINX_IMAGE|" "$ENV_FILE"
compose up -d >/dev/null 2>&1
wait_healthy nginx || exit 1
check "rollback: a versão anterior volta a rodar, com o health público no ar" bash -c \
  "[ \"\$(docker inspect -f '{{.Config.Image}}' '$(compose ps -q backend)')\" = '$BACKEND_IMAGE' ] &&
   [ \"\$(curl -s --cacert '$WORK/certs/ca.crt' '$BASE/actuator/health')\" = '{\"status\":\"UP\"}' ]"
docker rmi resort-backend:ensaio-nova resort-nginx:ensaio-nova >/dev/null 2>&1 || true

if [ "$FAILURES" -gt 0 ]; then
  echo "==> $FAILURES verificação(ões) falharam"
  exit 1
fi
echo "==> OK"
