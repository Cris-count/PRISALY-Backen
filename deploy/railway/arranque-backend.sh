#!/bin/sh
# DC-DEPLOY-01 · arranque del Backend en Railway.
# - Escucha en el PORT que inyecta Railway (server.port; el application.yml usa APP_PORT y no se modifica).
# - Primer administrador (P5 / DM1-01): el procedimiento oficial lee la contraseña inicial UNA vez desde un archivo
#   legible solo por el operador. En Railway no hay archivos del operador, así que, solo si el operador define la
#   variable sellada PRISALY_ADMIN_CREDENCIAL_INICIAL, este script la vuelca a un archivo 0600 temporal, la retira del
#   entorno del proceso Java y borra el archivo tras el arranque. Después el operador elimina la variable en Railway.
#   Nunca se imprime.
set -eu

if [ -n "${PRISALY_ADMIN_CREDENCIAL_INICIAL:-}" ]; then
  umask 077
  archivo="$(mktemp /tmp/prisaly-admin.XXXXXX)"
  printf '%s' "$PRISALY_ADMIN_CREDENCIAL_INICIAL" > "$archivo"
  unset PRISALY_ADMIN_CREDENCIAL_INICIAL
  export PRISALY_INICIALIZACION_ADMIN=true
  export PRISALY_INICIALIZACION_ADMIN_ARCHIVO="$archivo"
  # El inicializador lee el archivo durante el arranque (unos 50 s con 1 vCPU y 512 MB). Se borra en cuanto el Backend
  # responde a /api/health, o como tarde a los PRISALY_ADMIN_BORRADO_SEGUNDOS (600 por defecto).
  (
    limite="${PRISALY_ADMIN_BORRADO_SEGUNDOS:-600}"; t=0
    while [ "$t" -lt "$limite" ]; do
      if wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/api/health" 2>/dev/null; then break; fi
      sleep 5; t=$((t + 5))
    done
    rm -f "$archivo"
  ) &
fi

exec java -Dserver.port="${PORT:-8080}" -jar /app/app.jar
