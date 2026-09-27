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
export BACKEND_IMAGE="${BACKEND_IMAGE:-resort-backend:check}"
export NGINX_IMAGE="${NGINX_IMAGE:-resort-nginx:check}"
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
fi

WORK="$(mktemp -d)"
ENV_FILE="$WORK/.env.prod"
secret() { od -An -N18 -tx1 /dev/urandom | tr -d ' \n'; }
ADMIN_EMAIL="admin@prod-check.local"
BOOTSTRAP_PASSWORD="inicial-$(secret)"
ADMIN_PASSWORD="admin-$(secret)"
SUPERUSER_PASSWORD="$(secret)"
cat >"$ENV_FILE" <<EOF
BACKEND_IMAGE=$BACKEND_IMAGE
NGINX_IMAGE=$NGINX_IMAGE
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
compose up --no-start >/dev/null 2>&1
# O certificado vai para o volume letsencrypt no mesmo layout do Let's Encrypt (D-109).
docker run --rm --user root --entrypoint sh -v "${PROJECT}_letsencrypt:/le" -v "$WORK/certs:/src:ro" "$NGINX_IMAGE" \
  -c 'mkdir -p /le/live && cp -r /src/live/. /le/live/ && chmod -R a+rX /le'
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
check "o certificado da CA local vale para $DOMAIN (sem a CA, o curl recusa)" \
  bash -c "curl -s --cacert '$WORK/certs/ca.crt' -o /dev/null '$BASE/' && ! curl -s -o /dev/null '$BASE/'"

if [ "${PROD_CHECK_E2E:-1}" != "0" ]; then
  echo "==> E2E contra a pilha por HTTPS"
  if (cd "$ROOT/frontend" && E2E_BASE_URL="$BASE" E2E_ADMIN_EMAIL="$ADMIN_EMAIL" \
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
check "a auditoria grava o IP que o Nginx viu ($SEEN_IP), não o forjado nem o do Nginx ($AUDITED_IP)" \
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
