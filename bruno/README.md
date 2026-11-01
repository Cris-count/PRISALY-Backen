# Bruno — PRISALY M1 Identidad

## Setup (local only)

1. `docker compose up -d` and wait until Postgres is healthy.
2. Start the API (Compose-network jar recipe in root README / `docs/M1-JAR-BOOT-EVIDENCE.md`).
3. Apply seed (idempotent, local DB only):

```powershell
powershell -File bruno\scripts\apply-m1-bruno-seed.ps1
```

4. Copy secrets if needed:

```powershell
copy bruno\environments\local.secrets.bru.example bruno\environments\local.secrets.bru
```

5. Open `bruno/` in Bruno Desktop. Select environment **`local.secrets`** (gitignored) so passwords resolve. Do **not** commit `local.secrets.bru`.
6. Run folder `health`, then `m1-identidad` in sequence order.

## Security

- Committed `local.bru` has empty password vars.
- Runtime `accessToken` / `noPermAccessToken` are set with `bru.setVar` (session), not persisted into Git.
- Never paste tokens or real passwords into docs or evidence files.

## Permission code

Authorization probe uses M1 constant `CREAR_CASO` (`PersonalAuthorizationController.PERMISSION_CREAR_CASO`).

## Scenarios

| Request | Expected |
| --- | --- |
| health | 200 |
| personal-autenticar | 200 + runtime token |
| personal-autenticar-bad-password | 401 AUTH_FAILED |
| personal-autenticar-unknown-user | 401 AUTH_FAILED |
| personal-autenticar-inactive | 401 AUTH_FAILED |
| personal-me-unauthorized | 401 UNAUTHENTICATED |
| personal-me | 200 |
| personal-autorizacion-crear-caso | 200 CREAR_CASO |
| personal-autorizacion-crear-caso-forbidden (+ get) | 200 login then 403 FORBIDDEN |
| personal-autorizacion-crear-caso-unauthenticated | 401 |
| acceso-estudiante-validar | 501 |

Permission **revocation mid-session** is covered by `PersonalAuthSecurityIT`, not Bruno (no admin revoke HTTP API).

## Provisional token

`TD-M1-AUTH-PROVISIONAL`: opaque in-memory bearer. Lost on process restart. See `m1/docs/TD-M1-AUTH-PROVISIONAL.md`.
