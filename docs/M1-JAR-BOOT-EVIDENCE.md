# Full JAR boot evidence — post Fase 1 gate

## Git baseline checked

| Check | Value |
| --- | --- |
| Branch | `feature/m1-identidad-acceso` |
| HEAD at verification | `207d154` |
| Working tree | clean before boot work |
| Postgres | `prisaly-postgres` image `postgres:16.15`, health `healthy` |

## Result

**PASS** — packaged JAR starts against the shared Compose PostgreSQL 16.15, validates Flyway, initializes Hibernate, serves HTTP.

| Check | Result |
| --- | --- |
| Flyway validate | 9 migrations OK; schema at **v009**; no pending migrate |
| Hibernate | EntityManagerFactory started (`ddl-auto=validate`, quoted identifiers) |
| Tomcat | port **8080**, context `/api` |
| `GET /api/health` | `{"status":"ok","service":"prisaly-backend"}` |
| `flyway_schema_history` success rows | **9** |

## How it was run (no secrets published)

Host JVM → `localhost:5432` failed on this workstation with `SocketException: Connection reset` during JDBC authentication (even with `sslmode=disable` and `.env` loaded). Compose Postgres itself remained healthy.

Verified boot by attaching the JAR to the **Compose Docker network** (same Postgres the team shares):

```powershell
copy .env.example .env   # if needed; never commit .env
docker compose up -d
.\mvnw.cmd -B -pl app -am package -DskipTests

# Load DATABASE_USER / DATABASE_PASSWORD from local .env into the shell (do not echo them)
docker run --rm --name prisaly-app-smoke --network prisaly-backend_default -p 8080:8080 `
  -e "DATABASE_URL=jdbc:postgresql://postgres:5432/prisaly?sslmode=disable" `
  -e "DATABASE_USER=<from .env>" `
  -e "DATABASE_PASSWORD=<from .env>" `
  -e "APP_PORT=8080" `
  -e "CORS_ORIGINS=http://localhost:4200" `
  -v "${PWD}/app/target:/app:ro" `
  -w /app eclipse-temurin:21-jre `
  java -jar app-0.0.1-SNAPSHOT.jar
```

Log excerpt (redacted):

```text
Flyway … Database: jdbc:postgresql://postgres:5432/prisaly?sslmode=disable (PostgreSQL 16.15)
Successfully validated 9 migrations
Schema "public" is up to date. No migration necessary.
Tomcat started on port 8080 (http) with context path '/api'
Started PrisalyApplication in ~16 s
```

## Follow-up for integrators on Windows hosts

If host-side JDBC to published port `5432` resets, prefer the Compose-network recipe above (or add an `app` service to Compose later). Keep `?sslmode=disable` for local Compose.
