# PRISALY Backend

Modular monolith (M1–M7) on Spring Boot 4.1.2 / Java 21. Shared PostgreSQL **16.15** via Docker Compose. Schema BD-005.2 via Flyway **V001–V009**.

## Prerequisites

- JDK 21+ (project compiles with `--release 21`)
- Docker Desktop with Linux engine
- Maven Wrapper (`mvnw.cmd` on Windows / `./mvnw` on Unix)

## Environment

```powershell
copy .env.example .env
# Set POSTGRES_PASSWORD and DATABASE_PASSWORD locally — never commit .env
```

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Compose Postgres |
| `POSTGRES_PORT` | Host port (default `5432`) |
| `DATABASE_URL` | JDBC URL for the Spring process |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Must match Compose credentials |
| `APP_PORT` | API port (default `8080`) |
| `CORS_ORIGINS` | Front origins (default `http://localhost:4200`) |

## Clean start (reproducible)

```powershell
copy .env.example .env
docker compose up -d
docker compose ps
# wait until health = healthy

$env:DATABASE_URL="jdbc:postgresql://localhost:5432/prisaly?sslmode=disable"
$env:DATABASE_USER="prisaly"
$env:DATABASE_PASSWORD="<same as .env>"
.\mvnw.cmd -B -pl app -am package -DskipTests
```

On first boot Flyway applies `app/src/main/resources/db/migration/V001`…`V009` and creates the 21 BD-005.2 tables. JPA uses `ddl-auto=validate` and `hibernate.globally_quoted_identifiers=true` (quoted identifiers in SQL).

### Recommended: run JAR on the Compose network

Host JDBC to `localhost:5432` can fail on some Windows/Docker Desktop setups (`Connection reset`). Prefer attaching the JAR to the Compose network (verified in `docs/M1-JAR-BOOT-EVIDENCE.md`):

```powershell
docker run --rm --name prisaly-app --network prisaly-backend_default -p 8080:8080 `
  -e "DATABASE_URL=jdbc:postgresql://postgres:5432/prisaly?sslmode=disable" `
  -e "DATABASE_USER=<from .env>" `
  -e "DATABASE_PASSWORD=<from .env>" `
  -e "APP_PORT=8080" `
  -e "CORS_ORIGINS=http://localhost:4200" `
  -v "${PWD}/app/target:/app:ro" `
  -w /app eclipse-temurin:21-jre `
  java -jar app-0.0.1-SNAPSHOT.jar
```

> Running `spring-boot:run` on the reactor parent fails (no main class). Use `-pl app` after `install`, or the jar recipe above.

### Persist after restart

```powershell
docker compose restart postgres
# Do NOT use: docker compose down -v  (deletes named volume prisaly_pg_data)
```

### Diagnostics

```powershell
docker compose exec postgres pg_isready -U prisaly -d prisaly
docker compose exec postgres psql -U prisaly -d prisaly -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;"
docker volume inspect prisaly_pg_data
```

Evidence notes: `docs/M1-DOCKER-EVIDENCE.md`, `docs/M1-FLYWAY-APPLY.md`.

## Module responsibilities

| Module | Responsibility |
| --- | --- |
| `app` | Boot, HTTP/Security wiring, Flyway classpath, Compose consumers |
| `m1` | Identity & access (Usuario, Rol, Permiso, UsuarioPermiso, auth contracts) |
| `m2`–`m7` | Domain modules; depend on shared DB + M1 contracts when integrating |

Integration branch for M1 work: `feature/m1-identidad-acceso`. Do not force-push `main`/`develop`. Merge only via reviewed PR.

## Compile

```powershell
.\mvnw.cmd -B -pl app -am package -DskipTests
```

## Tests

```powershell
.\mvnw.cmd -B -pl m1 test
.\mvnw.cmd -B -pl m1 -am -Pdocker-it verify
.\mvnw.cmd -B -pl app -am -Pdocker-it verify
```

`-Pdocker-it` runs Failsafe ITs (Testcontainers Postgres 16.15 + canonical Flyway). App profile covers HTTP security (401/403).

## API smoke

- Health: `GET http://localhost:8080/api/health`
- Personnel auth (ARQ-008.3): `POST http://localhost:8080/api/v1/personal/autenticar`

## Bruno

Open `bruno/` in Bruno. Environment `local` → `baseUrl=http://localhost:8080/api`.

## Technical debt

`TD-M1-AUTH-PROVISIONAL`: opaque in-memory bearer tokens for local integration only. Exit criteria in `m1/docs/TD-M1-AUTH-PROVISIONAL.md`.
