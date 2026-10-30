# Flyway apply evidence (M1) — Fase 0

## Source package

- File: `PRISALY_M1_V001_V009_VERIFICADAS.zip`
- Lineage: V001–V009 from `PRISALY_INCREMENTO_1.zip` (= `PRISALY_CIERRE_TECNICO_FINAL.zip`)
- Content: copied unchanged into `app/src/main/resources/db/migration/`
- Duplicate check: no other `V00*.sql` under Maven resources

## SHA-256 (canonical copies)

| Script | Bytes | SHA-256 |
| --- | ---: | --- |
| V001__bd0052_estructura.sql | 14085 | 896DE22167CCCDA77577649F6FD2B5A90EB53960C26CFAF12E439B8296326974 |
| V002__bd0052_restricciones.sql | 2631 | 6ED5DD7C317F02A1CF3DF7E6A564A0091D1B2D66F2295FCD3D8F87658B8E8C55 |
| V003__bd0052_integridad.sql | 5264 | 59F539968BE4AABD4AC0EA49E3578C2E25F2A1F055E3C19C3ABCA39845FE42F7 |
| V004__bd0052_integridad_intentos.sql | 4138 | EA96BFB7C9A1174DA5E0A576668B793DF0A09276C1801ADA248B781945EAC8B2 |
| V005__bd0052_cierre_integridad.sql | 6471 | CD4BB975ACB16B9FFE6844F51F6308D45246ED882F574B4B6F9E8E7AEAF06274 |
| V006__bd0052_proteccion_relaciones.sql | 2979 | 2C84A279D2355A6282E05A158D9322FC04BCDF220117FA9557B5F5ACFC7FCA6B |
| V007__fase3_politicas_aprobadas.sql | 10743 | 9FABEBB25970339ABC1DE9CBEB90C3CCDA4138008A4EE2E78846781932D80ED2 |
| V008__bloqueo_caso_al_reasignar_sesion.sql | 483 | 53508D12BA652695270FED87E69C7BF7347C30CB8D071735A8372F833667F833 |
| V009__rf09_solo_casos_publicados.sql | 1316 | 5D671ECEA0F2A015A48EAAEFC6A12474FC2D6B75CE79BAACD8657FF03CBC9CE3 |

## Docker Postgres 16.15 apply

Removed temporary `m1_persist_probe` (Fase 0 volume probe only) so Flyway could migrate an empty `public` schema.

```text
Flyway OSS 10.22.0 → jdbc:postgresql://postgres:5432/prisaly (PostgreSQL 16.15)
Successfully applied 9 migrations … now at version v009
```

### `flyway_schema_history`

| rank | version | script | success |
| ---: | --- | --- | --- |
| 1 | 001 | V001__bd0052_estructura.sql | t |
| 2 | 002 | V002__bd0052_restricciones.sql | t |
| 3 | 003 | V003__bd0052_integridad.sql | t |
| 4 | 004 | V004__bd0052_integridad_intentos.sql | t |
| 5 | 005 | V005__bd0052_cierre_integridad.sql | t |
| 6 | 006 | V006__bd0052_proteccion_relaciones.sql | t |
| 7 | 007 | V007__fase3_politicas_aprobadas.sql | t |
| 8 | 008 | V008__bloqueo_caso_al_reasignar_sesion.sql | t |
| 9 | 009 | V009__rf09_solo_casos_publicados.sql | t |

### Schema counts

| Check | Result |
| --- | --- |
| Domain tables (`public`, excl. flyway) | **21** |
| PK constraints | 22 |
| FK constraints | 32 |
| UNIQUE + CHECK | 40 |
| M1 tables | `Usuario`, `Rol`, `Permiso`, `UsuarioPermiso` with PK/UQ/FK as expected |

### SQL persistence on Docker (same volume)

Insert `Rol` + `Usuario` and join-read succeeded (`ana.docente@prisaly.test` / `Docente`).

## JPA persistence IT

`UsuarioPersistenceIT` (profile `docker-it`) against Testcontainers `postgres:16.15`:

- Flyway locations → canonical `app/.../db/migration` (no duplicated V00x in `m1` resources)
- `ddl-auto=validate` + `hibernate.globally_quoted_identifiers=true` (BD-005.2 quoted identifiers)
- Result: **BUILD SUCCESS** — Flyway v001–v009 applied, Hibernate validate OK, Usuario save/load OK

```powershell
.\mvnw.cmd -pl m1 -am verify -Pdocker-it
```

## Fase 0 gate

| Gate | Status |
| --- | --- |
| Postgres 16.15 + volume persistence | PASS (`M1-DOCKER-EVIDENCE.md`) |
| V001–V009 restored unchanged | PASS |
| Flyway apply + 21 tables + constraints | PASS |
| Real M1 user persist/retrieve | PASS (SQL on Docker + JPA IT) |
| V009 owner confirmation (variant note) | Documented — Incremento 1 / cierre final used |
