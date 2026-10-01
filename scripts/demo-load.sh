#!/usr/bin/env bash
# Carga da instalação de demonstração (D-125). Pede o nome do ADMIN da demonstração e as senhas dos quatro
# usuários, estas sem eco (ou usa os que já estiverem no ambiente, como faz o demo-reset.sh), e roda o
# serviço demo-load do compose. Nenhum dos dois tem padrão no repositório.
# A carga recusa, sem gravar nada, sem DEMO_INSTANCE=true e num banco com qualquer dado além do ADMIN
# inicial; a pilha precisa ter subido uma vez, porque o backend cria o ADMIN inicial na subida.
# Uso: scripts/demo-load.sh <arquivo .env.prod>
# COMPOSE_PROJECT_NAME escolhe outro projeto do compose (a verificação local usa um próprio).
set -euo pipefail

ENV_FILE="${1:?uso: scripts/demo-load.sh <arquivo .env.prod>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -r "$ENV_FILE" ] || { echo "demo-load: arquivo não encontrado ou sem leitura: $ENV_FILE" >&2; exit 1; }
ENV_FILE="$(cd "$(dirname "$ENV_FILE")" && pwd)/$(basename "$ENV_FILE")"

value() { # value <nome>: lê a variável do .env.prod, sem executar o arquivo
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -1 | sed -e "s/^'\(.*\)'\$/\1/" -e 's/^"\(.*\)"$/\1/'
}
if [ "$(value DEMO_INSTANCE)" != "true" ]; then
  echo "demo-load: recusado: $ENV_FILE não tem DEMO_INSTANCE=true (D-125). Nada foi alterado." >&2
  exit 2
fi

ask() { # ask <variável> <descrição>: usa o valor do ambiente ou pede sem eco
  local name="$1" what="$2" secret="${!1:-}"
  if [ -z "$secret" ]; then
    [ -t 0 ] || { echo "demo-load: $name não informada e não há terminal para perguntar." >&2; exit 2; }
    read -r -s -p "Senha do usuário $what (mínimo de 10 caracteres): " secret
    echo
  fi
  [ "${#secret}" -ge 10 ] || { echo "demo-load: a senha do usuário $what precisa de no mínimo 10 caracteres. Nada foi alterado." >&2; exit 2; }
  export "$name=$secret"
}
if [ -z "${DEMO_ADMIN_NAME:-}" ]; then
  [ -t 0 ] || { echo "demo-load: DEMO_ADMIN_NAME não informado e não há terminal para perguntar." >&2; exit 2; }
  read -r -p "Nome do ADMIN da demonstração (aparece na saudação do dashboard): " DEMO_ADMIN_NAME
fi
[ "${#DEMO_ADMIN_NAME}" -ge 2 ] || { echo "demo-load: o nome do ADMIN da demonstração precisa de no mínimo 2 caracteres. Nada foi alterado." >&2; exit 2; }
export DEMO_ADMIN_NAME
ask DEMO_ADMIN_PASSWORD "ADMIN da demonstração"
ask DEMO_PROSPECTOR_PASSWORD "Prospector da demonstração"
ask DEMO_GATE_PASSWORD "Portaria da demonstração"
ask DEMO_HOST_PASSWORD "Anfitrião da demonstração"

docker compose -f "$ROOT/docker-compose.prod.yml" --project-directory "$ROOT" --env-file "$ENV_FILE" \
  run --rm -T demo-load </dev/null
