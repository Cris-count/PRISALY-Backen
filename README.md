# PRISALY Backend

Modular monolith (M1–M7) on Spring Boot 4.1.2 / Java 21. Shared PostgreSQL 16.15 via Docker Compose.

## Prerequisites

- JDK 21+ (project compiles with `--release 21`)
- Docker Desktop with Linux engine
- Maven Wrapper (`mvnw.cmd` on Windows)

## Database (Integrante 1)

```powershell
copy .env.example .env
# edit POSTGRES_PASSWORD / DATABASE_PASSWORD locally — do not commit .env

docker compose up -d
docker compose ps
docker compose logs -f postgres
```

Wait until health is `healthy` (`pg_isready`). Spring should use:

- `DATABASE_URL=jdbc:postgresql://localhost:5432/prisaly`
- `DATABASE_USER` / `DATABASE_PASSWORD` matching Compose

### Persist after restart

```powershell
docker compose restart postgres
# Do NOT use: docker compose down -v  (that deletes the named volume)
```

### Diagnostics

```powershell
docker compose exec postgres pg_isready -U prisaly -d prisaly
docker volume inspect prisaly_pg_data
```

## Flyway

Canonical path: `app/src/main/resources/db/migration`.

Historical **V001–V009** were not present in this repository at M1 bootstrap. See [docs/M1-FLYWAY-BLOCKER.md](docs/M1-FLYWAY-BLOCKER.md). Do not invent substitute V001–V009 files.

## Run the API

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/prisaly"
$env:DATABASE_USER="prisaly"
$env:DATABASE_PASSWORD="change_me_local_only"
.\mvnw.cmd -B -pl app -am spring-boot:run
```

- Health: `GET http://localhost:8080/api/health`
- Personnel auth (ARQ-008.3): `POST http://localhost:8080/api/v1/personal/autenticar`

## Tests

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B -pl m1 -am -Pdocker-it verify
```

## Bruno

Open the `bruno/` folder in Bruno. Use environment `local` (`baseUrl=http://localhost:8080/api`).

## Technical debt

`TD-M1-AUTH-PROVISIONAL`: opaque in-memory bearer tokens for local integration only. Not an M1 security closure. Exit criteria in `m1/docs/TD-M1-AUTH-PROVISIONAL.md`.
