# Blocker: historical Flyway migrations V001–V009

## Inspection result (2026-10-30)

- Canonical Flyway path: `app/src/main/resources/db/migration` (only `.gitkeep` present).
- `origin/main` and `origin/develop` both at `288f9aa` at inspection time (re-check before any PR).
- Workspace search under `PRISALY`: no `V001*.sql` … `V009*.sql`, no `PRISALY_INCREMENTO_*.zip`, no `PRISALY_CIERRE_TECNICO_*.zip`.
- Remote feature branches: M4 adds a **proposed** `V010` and states that V001–V009 are absent from this repository. That file is not restored into `app` by M1 (avoids duplicate/non-canonical migrations).

## Policy

Per Integrante 1 order: restore V001–V009 **only** from an authorized project package, without rewriting content and without substitute versions named as V001–V009.

## Impact

- Production Flyway folder stays empty until the authorized SQL arrives.
- M1 persistence integration tests use a **test-only** schema fixture derived from BD-005.2 M1 tables (`Usuario`, `Rol`, `Permiso`, `UsuarioPermiso`). That fixture is **not** a historical migration and must be replaced by the official V001–V009 baseline when available.
- `spring.jpa.hibernate.ddl-auto` remains `none` in the default profile. Profile `db-validate` may set `validate` once official migrations and entities are aligned.

## Exit criteria to clear this blocker

1. Authorized zip/source provided by the team.
2. Files copied unchanged into `app/src/main/resources/db/migration/`.
3. `docker compose up` + app start applies V001–V009 in order.
4. Remove or stop using the test-only M1 fixture for baseline schema.
