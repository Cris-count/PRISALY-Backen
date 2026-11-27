#!/bin/sh
set -eu
export DATABASE_PASSWORD="$(cat /run/secrets/postgres_password)"
exec java -jar /app/app.jar
