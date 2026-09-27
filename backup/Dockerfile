# syntax=docker/dockerfile:1
# Backup do banco (Fase 11a, D-110): pg_dump diário cifrado com age para uma chave pública; a chave
# privada nunca fica no servidor. Base: a mesma imagem do PostgreSQL da pilha (pg_dump e pg_restore da
# mesma versão). O age vem do release oficial, conferido por checksum.
ARG POSTGRES_IMAGE=postgres:16.15-alpine@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea

FROM ${POSTGRES_IMAGE}
ARG TARGETARCH=amd64
ARG AGE_VERSION=1.2.1
ARG AGE_SHA256_AMD64=7df45a6cc87d4da11cc03a539a7470c15b1041ab2b396af088fe9990f7c79d50
ARG AGE_SHA256_ARM64=57fd79a7ece5fe501f351b9dd51a82fbee1ea8db65a8839db17f5c080245e99f
ADD https://github.com/FiloSottile/age/releases/download/v${AGE_VERSION}/age-v${AGE_VERSION}-linux-${TARGETARCH}.tar.gz /tmp/age.tgz
RUN set -eu; \
    case "$TARGETARCH" in amd64) sum="$AGE_SHA256_AMD64" ;; arm64) sum="$AGE_SHA256_ARM64" ;; *) exit 1 ;; esac; \
    echo "$sum  /tmp/age.tgz" | sha256sum -c -; \
    tar -xzf /tmp/age.tgz -C /tmp; \
    install -m 0755 /tmp/age/age /tmp/age/age-keygen /usr/local/bin/; \
    rm -rf /tmp/age /tmp/age.tgz
COPY --chmod=755 backup/backup.sh /usr/local/bin/backup.sh
COPY --chmod=755 backup/entrypoint.sh /usr/local/bin/backup-entrypoint.sh
COPY --chmod=755 backup/health.sh /usr/local/bin/backup-health.sh
ENTRYPOINT ["/usr/local/bin/backup-entrypoint.sh"]
