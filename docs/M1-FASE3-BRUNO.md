# M1 Fase 3 — Bruno HTTP evidence

## Git baseline

| Item | Value |
| --- | --- |
| Branch | `feature/m1-identidad-acceso` |
| Baseline before Fase 3 | `ab926ec` |

## Collection

- [`bruno/m1-identidad/`](../bruno/m1-identidad/)
- [`bruno/health/`](../bruno/health/)
- Env committed: `local.bru` (empty passwords)
- Env local: `local.secrets.bru` (**gitignored**; from `local.secrets.bru.example`)
- Seed: [`bruno/scripts/m1-bruno-seed-local.sql`](../bruno/scripts/m1-bruno-seed-local.sql) + `apply-m1-bruno-seed.ps1`
- Permission code: **`CREAR_CASO`** (`PersonalAuthorizationController.PERMISSION_CREAR_CASO`)
- Tokens: runtime only via `bru.setVar` (not env file persistence)

## Maven regression (executed)

```powershell
.\mvnw.cmd -B -pl m1 test
# Tests run: 8, Failures: 0 — BUILD SUCCESS

.\mvnw.cmd -B -pl app -am -Pdocker-it verify
# PersonalAuthSecurityIT: Tests run: 9, Failures: 0 — BUILD SUCCESS (reactor)
```

## Bruno live HTTP (executed)

Server: packaged jar on Compose network + Postgres 16.15 + local seed.

```powershell
bru run health m1-identidad --env local.secrets
```

| Scenario | HTTP | Status |
| --- | --- | --- |
| health | 200 | **PASS** |
| personal-autenticar | 200 | **PASS** |
| personal-autenticar-bad-password | 401 AUTH_FAILED | **PASS** |
| personal-autenticar-unknown-user | 401 AUTH_FAILED | **PASS** |
| personal-autenticar-inactive | 401 AUTH_FAILED | **PASS** |
| personal-me-unauthorized | 401 UNAUTHENTICATED | **PASS** |
| personal-me | 200 | **PASS** |
| personal-autorizacion-crear-caso | 200 CREAR_CASO | **PASS** |
| personal-autorizacion-crear-caso-forbidden (+ get) | 200 then 403 FORBIDDEN | **PASS** |
| personal-autorizacion-crear-caso-unauthenticated | 401 | **PASS** |
| acceso-estudiante-validar | 501 | **PASS** |

Summary: **12/12 requests, 24/24 assertions PASS**. No positive scenario accepted as 403.

## Revocation

Mid-session revoke evidence remains in `PersonalAuthSecurityIT` (no admin revoke HTTP API).

## Limitations

- `TD-M1-AUTH-PROVISIONAL` in-memory bearer.
- Student auth blocked by DD-02 (501).
- Seed is **local-only**; do not run on shared/prod databases.
- Push/merge pending explicit authorization.
