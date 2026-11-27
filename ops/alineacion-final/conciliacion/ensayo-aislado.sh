# uso: run-backend.sh <nombre> <locations> <contrato> [mount-candidato]
S="$1"; N="$2"; LOC="$3"; CON="$4"; EXTRA="$5"
docker rm -f "$N" >/dev/null 2>&1
docker run -d --name "$N" --network alin02-net --label com.prisaly.instance=alin02-desechable \
  -v "$S/pgpass:/run/secrets/postgres_password:ro" $EXTRA \
  -e DATABASE_URL=jdbc:postgresql://alin02-pg:5432/prisaly_ops70 -e DATABASE_USER=prisaly_ops70 \
  -e SPRING_FLYWAY_LOCATIONS="$LOC" -e SPRING_FLYWAY_OUT_OF_ORDER=false \
  -e SPRING_APPLICATION_JSON="{\"prisaly.m4.schema-contract\":\"$CON\"}" \
  -e PRISALY_INICIALIZACION_ADMIN=false -e CORS_ORIGINS=http://localhost:9 \
  prisaly-cierre-backend:local >/dev/null
for i in $(seq 1 40); do sleep 3; st=$(docker inspect -f '{{.State.Status}}' "$N"); docker logs "$N" 2>&1 | grep -q -E "Started PrisalyApplication|APPLICATION FAILED|Exception" && break; [ "$st" != running ] && break; done
docker logs "$N" 2>&1 | grep -E "Successfully applied|Migrating schema|Started PrisalyApplication|Validate failed|Detected|M4-SCHEMA|incompatible|Caused by|Error" | sed -E 's/^.{0,60}(INFO|WARN|ERROR)/\1/' | cut -c1-260 | grep -v "already a transaction" | head -12
