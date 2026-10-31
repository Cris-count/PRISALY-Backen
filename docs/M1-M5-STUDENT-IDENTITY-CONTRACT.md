# M1 → M5 | Contrato de identidad estudiantil

**From:** Integrante 1 (M1)  
**Branch:** `feature/m1-identidad-acceso`  
**M5 branch (not modified):** `feature/m5-participacion-intentos`

---

## 0. Implemented vs pending

| Item | Status |
| --- | --- |
| `IdentityAccessPort` + personnel auth | **Implemented** |
| `StudentAccessPort` + `AuthenticatedStudent` | **Published (compilable)** |
| `DenyingStudentAccessService` (safe deny) | **Published** |
| `POST /v1/acceso-estudiante/validar` | **501** until DD-02 |
| Real student authentication / token | **Blocked by DD-02** |
| Runtime `Usuario` → `Estudiante` mapping | **Blocked by DD-02** |

---

## 1. Public Java contract (available now)

```java
package com.prisaly.m1.application.port;

public interface StudentAccessPort {
    AuthenticatedStudent requireCurrentStudent();
}

public record AuthenticatedStudent(
    UUID userId,
    UUID studentId
) {}
```

Production bean: `DenyingStudentAccessService` throws domain code `STUDENT_IDENTITY_UNAVAILABLE` on every call. It does **not** read `SecurityContext`, personnel tokens, or client-supplied ids.

### How M5 should consume it

1. Depend on module `m1`.
2. Inject `StudentAccessPort` into application services.
3. Call `requireCurrentStudent()` and use `studentId()` for ownership checks (e.g. attempt belongs to that student).
4. In M5 unit/IT tests, provide a **test double** that returns a fixed `AuthenticatedStudent` — do not invent auth.
5. Handle `IdentityDomainException` with code `STUDENT_IDENTITY_UNAVAILABLE` until DD-02 ships a real adapter.

HTTP status mapping is a **presentation** concern:

| Situation | HTTP |
| --- | --- |
| No authenticated identity | **401** |
| Authenticated (e.g. personnel) but not a student context | **403** (presentation maps domain denial) |
| `POST /acceso-estudiante/validar` while DD-02 open | **501** |

Domain code `STUDENT_IDENTITY_UNAVAILABLE` is **not** a global HTTP rule in `IdentityExceptionHandler`.

---

## 2. DD-02 (still required for real student auth)

Dirección Central must ratify how students authenticate (correo + código + convocatoria/sesión), what credential M1 issues, lifetime/revocation, and required M4 checks. Until then M1 keeps `/validar` at 501 and the denying adapter.

---

## 3. Security notes for M5

- Do not reuse personal `IdentityAccessPort` / personal Bearer as student identity.
- Do not trust `idEstudiante` from Angular as authority.
- Do not add a second `SecurityFilterChain` or widen `permitAll` for attempt APIs.
- Central rules live in `app` `HttpApiConfig`.

---

## 4. Message to Integrante 4

M1 already exposes compilable `StudentAccessPort` for preparatory integration. Real student identity remains blocked by DD-02; use the port interface and test doubles without building homemade authentication.
