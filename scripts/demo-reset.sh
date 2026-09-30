#!/usr/bin/env bash
# Recarga da instalação de demonstração (D-125): apaga o banco inteiro e carrega a demonstração de novo.
# É destrutivo; por isso, antes de tocar em qualquer coisa:
#   1. confere DEMO_INSTANCE=true no .env.prod;
#   2. confere pelo conteúdo que todos os usuários do banco são os da demonstração ou o ADMIN inicial
#      (APP_BOOTSTRAP_ADMIN_EMAIL). Com qualquer outro usuário, recusa: se DEMO_INSTANCE=true fosse ligado por
#      engano numa produção, digitar o domínio não protegeria, porque o domínio é o da produção;
#   3. pede as senhas da demonstração e o domínio digitado, para confirmar.
# Depois: para o Nginx, o backend e o backup, confere o conteúdo de novo, recria o banco (como o
# restore.sh), sobe a pilha (migrations e ADMIN inicial) e roda o demo-load.sh. O volume de backups não é
# tocado. Faça a recarga no dia da apresentação: as visitas de hoje só valem no dia da carga.
# Uso: scripts/demo-reset.sh <arquivo .env.prod>
set -euo pipefail

ENV_FILE="${1:?uso: scripts/demo-reset.sh <arquivo .env.prod>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -r "$ENV_FILE" ] || { echo "demo-reset: arquivo não encontrado ou sem leitura: $ENV_FILE" >&2; exit 1; }
ENV_FILE="$(cd "$(dirname "$ENV_FILE")" && pwd)/$(basename "$ENV_FILE")"

value() { # value <nome>: lê a variável do .env.prod, sem executar o arquivo
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e "s/^'\(.*\)'\$/\1/" -e 's/^"\(.*\)"$/\1/'
}
# Nenhum comando do compose lê a entrada padrão: ela é só das perguntas (o exec e o run a consumiriam).
compose() { docker compose -f "$ROOT/docker-compose.prod.yml" --project-directory "$ROOT" --env-file "$ENV_FILE" "$@" </dev/null; }
compose_sql() { docker compose -f "$ROOT/docker-compose.prod.yml" --project-directory "$ROOT" --env-file "$ENV_FILE" \
  exec -T -u postgres postgres psql -v ON_ERROR_STOP=1 -d postgres; }
refuse() { echo "demo-reset: recusado: $*" >&2; exit 2; }

echo "==> 1/6 Conferindo DEMO_INSTANCE"
[ "$(value DEMO_INSTANCE)" = "true" ] || refuse "$ENV_FILE não tem DEMO_INSTANCE=true (D-125). Nada foi apagado."
DOMAIN="$(value DOMAIN)"

echo "==> 2/6 Conferindo os usuários do banco"
compose up -d postgres >/dev/null
for _ in $(seq 60); do
  if compose exec -T -u postgres postgres pg_isready -d postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
compose run --rm -T demo-load check-reset || refuse "o banco tem usuários que não são da demonstração. Nada foi apagado."

echo "==> 3/6 Senhas e confirmação"
# As senhas antes de apagar: desistir aqui não perde nada. O demo-load.sh usa as exportadas.
ask() { # ask <variável> <descrição>: usa o valor do ambiente ou pede sem eco
  local name="$1" what="$2" secret="${!1:-}"
  if [ -z "$secret" ]; then
    read -r -s -p "Senha do usuário $what (mínimo de 10 caracteres): " secret
    echo
  fi
  [ "${#secret}" -ge 10 ] || refuse "a senha do usuário $what precisa de no mínimo 10 caracteres. Nada foi apagado."
  export "$name=$secret"
}
ask DEMO_ADMIN_PASSWORD "ADMIN da demonstração"
ask DEMO_PROSPECTOR_PASSWORD "Prospector da demonstração"
ask DEMO_GATE_PASSWORD "Portaria da demonstração"
ask DEMO_HOST_PASSWORD "Anfitrião da demonstração"
read -r -p "Isto apaga TODOS os dados de $DOMAIN e carrega a demonstração de novo. Digite o domínio para confirmar: " typed
[ "$typed" = "$DOMAIN" ] || refuse "o domínio digitado não confere. Nada foi apagado."

echo "==> 4/6 Parando o Nginx, o backend e o backup, e conferindo de novo"
compose stop nginx backend backup >/dev/null 2>&1 || true
if ! compose run --rm -T demo-load check-reset >/dev/null; then
  compose up -d >/dev/null
  refuse "um usuário de fora da demonstração apareceu durante a recarga. A pilha voltou a subir; nada foi apagado."
fi

echo "==> 5/6 Recriando o banco"
compose_sql >/dev/null <<'SQL'
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'resort' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS resort;
CREATE DATABASE resort OWNER resort_owner;
REVOKE ALL ON DATABASE resort FROM PUBLIC;
GRANT CONNECT ON DATABASE resort TO resort_owner, resort_app, resort_backup;
\connect resort
ALTER SCHEMA public OWNER TO resort_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO resort_app, resort_backup;
SQL

echo "==> 6/6 Subindo a pilha e carregando a demonstração"
compose up -d >/dev/null
for _ in $(seq 180); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -q backend)" 2>/dev/null || true)"
  [ "$status" = healthy ] && break
  sleep 1
done
[ "${status:-}" = healthy ] || { echo "demo-reset: o backend não ficou saudável em 3 minutos; veja compose logs backend." >&2; exit 1; }
"$ROOT/scripts/demo-load.sh" "$ENV_FILE"
echo "demo-reset: concluído."
