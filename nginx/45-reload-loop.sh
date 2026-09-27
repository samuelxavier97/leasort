#!/bin/sh
# Recarrega o Nginx a cada 6 h para pegar o certificado renovado pelo certbot (D-109). Um certificado
# emitido pela primeira vez, quando ainda se usa o provisório, exige reiniciar o container.
(
  while sleep 21600; do
    nginx -s reload
  done
) &
