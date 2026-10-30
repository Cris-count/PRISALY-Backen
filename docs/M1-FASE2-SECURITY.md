# M1 Fase 2 — authentication & shared contracts

## Scope

- Spring Security filter chain (ARQ-008.3 personal vs student routes kept separate)
- `IdentityAccessPort` remains the shared contract (no JPA/Security types exported)
- Provisional opaque bearer tokens (`TD-M1-AUTH-PROVISIONAL`)
- HTTP status contract: **401** unauthenticated / bad credentials; **403** missing permission / inactive identity when enforced by domain

## Evidence commands

```powershell
.\mvnw.cmd -B -pl m1 test
.\mvnw.cmd -B -pl app -am -Pdocker-it verify
```

| Scenario | Expected |
| --- | --- |
| Valid personal login + `/me` | 200 + token + `TD-M1-AUTH-PROVISIONAL` |
| Wrong password | 401 `AUTH_FAILED` |
| Unknown user | 401 `AUTH_FAILED` |
| Inactive account login | 401 `AUTH_FAILED` |
| `/me` without token | 401 `UNAUTHENTICATED` |
| Permission revoked then probe | 403 `FORBIDDEN` |

Student route `POST /v1/acceso-estudiante/validar` stays `501` pending M4/M5 (DD-02).
