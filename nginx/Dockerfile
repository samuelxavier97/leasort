# syntax=docker/dockerfile:1
# Nginx de borda (Fase 11a, D-105): serve o build do frontend e faz proxy de /api. Contexto: a raiz do
# repositório. Sem root (nginx-unprivileged, uid 101). A imagem não traz o nome do cliente: RESORT_NAME
# é injetada no index.html quando o container sobe (D-087).
ARG NODE_IMAGE=node:22.23.3-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:1.30.5-alpine@sha256:4714e0b1b2577eaa1a6131d07c958b67f0eb68e6d0521e90c6e5287db8cf0bc5

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
COPY --chmod=755 nginx/40-resort-name.sh /docker-entrypoint.d/40-resort-name.sh
COPY nginx/templates/ /etc/nginx/templates/
USER 101
ENV TZ=UTC
