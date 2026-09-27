# syntax=docker/dockerfile:1
# Nginx de borda (Fase 11a, D-105): serve o build do frontend e faz proxy de /api. Contexto: a raiz do
# repositório. Sem root (nginx-unprivileged, uid 101). A imagem não traz o nome do cliente: RESORT_NAME
# é injetada no index.html quando o container sobe (D-087).
ARG NODE_IMAGE=node:22.23.3-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:1.30.5-alpine@sha256:4714e0b1b2577eaa1a6131d07c958b67f0eb68e6d0521e90c6e5287db8cf0bc5
ARG ALPINE_IMAGE=alpine:3.22.6@sha256:5291449c3df73caf6ed85e649dec1b9e818b39a5d8c871e97afc13e9cd5e8fa8

# Certificado provisório (autoassinado), só para o Nginx subir antes do primeiro certificado do Let's
# Encrypt (D-109); nenhum navegador o aceita.
FROM ${ALPINE_IMAGE} AS provisional-cert
RUN (command -v openssl >/dev/null || apk add --no-cache openssl) \
    && openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=provisorio.invalid" \
       -keyout /provisional.key -out /provisional.crt

FROM ${NODE_IMAGE} AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM ${NGINX_IMAGE}
COPY --from=build --chown=101:101 /src/dist/ /usr/share/nginx/html/
USER root
# O index.html vira modelo; o 40-resort-name.sh gera o servido a cada subida.
RUN mv /usr/share/nginx/html/index.html /usr/share/nginx/index.html.template \
    && chown 101:101 /usr/share/nginx/html
COPY --chmod=755 nginx/18-resort.envsh /docker-entrypoint.d/18-resort.envsh
COPY --chmod=755 nginx/40-resort-name.sh /docker-entrypoint.d/40-resort-name.sh
COPY --chmod=755 nginx/45-reload-loop.sh /docker-entrypoint.d/45-reload-loop.sh
COPY nginx/templates/ /etc/nginx/templates/
COPY nginx/snippets/ /etc/nginx/snippets/
COPY --from=provisional-cert --chown=101:101 /provisional.crt /provisional.key /etc/nginx/tls/
RUN mkdir -p /var/www/acme && chmod 600 /etc/nginx/tls/provisional.key
USER 101
# 8080: HTTP (desafio ACME e redirecionamento); 8443: HTTPS. Publicadas no host como 80 e 443.
EXPOSE 8080 8443
ENV TZ=UTC
