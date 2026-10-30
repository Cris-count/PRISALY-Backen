# M1 Fase 1 — identity persistence

## Scope

Consolidate BD-005.2 identity tables without login UI / M7:

- Entities: `Usuario`, `Rol`, `Permiso`, `UsuarioPermiso` (+ composite `UsuarioPermisoId`)
- Repositories: Spring Data JPA for all four
- `IdentityCatalogService`: create role/permission, register user (BCrypt + normalized email), assign/disable permission
- Uniqueness: application checks + `DataIntegrityViolationException` → `IdentityDomainException`
- Tests: `IdentityPersistenceIT` (Testcontainers Postgres 16.15 + Flyway V001–V009)

## Evidence

```powershell
.\mvnw.cmd -pl m1 -am verify -Pdocker-it
```

| Test | Result |
| --- | --- |
| Usuario + Rol + email normalized | PASS |
| BCrypt store / match | PASS |
| Duplicate email (case-insensitive) | PASS |
| Permiso + UsuarioPermiso composite PK + assigner FK | PASS |
| Duplicate assignment | PASS |
| Disable assignment (transactional update) | PASS |

## Shared readiness notes (Nivel 1)

- Branch `feature/m1-identidad-acceso` includes V001–V009 and README clean-start steps
- `.\mvnw.cmd -B -pl app -am package -DskipTests` → **BUILD SUCCESS** (all modules)
- Only M1 defines JPA `@Entity` today; `globally_quoted_identifiers=true` is safe until other modules add entities (then re-validate full boot)
- Local JDBC URL should include `?sslmode=disable` (see `.env.example`) when talking to Compose from the host
- Full jar boot requires `DATABASE_*` credentials matching the running Compose `.env`
