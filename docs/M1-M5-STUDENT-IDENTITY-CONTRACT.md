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
| `POST /v1/acceso-estudiante/validar` | **501** in every configuration (in-memory harness removed in DC-M1-POSTCIERRE-02) |
| Real student session / cookie / OTP delivery | **Blocked** (sin SMTP, sin V011, denying sigue en `StudentAccessPort`) |
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
| `POST /acceso-estudiante/validar` (any configuration) | **501** (`ESTUDIANTE_ACCESS_PENDING_CONTRACT`) |

Domain code `STUDENT_IDENTITY_UNAVAILABLE` is **not** a global HTTP rule in `IdentityExceptionHandler`.

---

## 2. DD-02 (still required for real student auth)

Productive DD-02 stays off: `prisaly.m1.student-access.enabled=false`, `/validar` returns **501**, `DenyingStudentAccessService` remains `StudentAccessPort`, SMTP worker off. M1 defines `StudentAccessDeliveryPort`; M7 provides an in-memory prep outbox (not `NotificacionPort`); no M1 issuer is wired yet. Alternative A (own account, activation and password) is the proposed design; V011/V012 remain candidates outside productive Flyway and session cookies stay behind the disabled flag.

---

## 3. Security notes for M5

- Do not reuse personal `IdentityAccessPort` / personal Bearer as student identity.
- Do not trust `idEstudiante` from Angular as authority.
- Do not add a second `SecurityFilterChain` or widen `permitAll` for attempt APIs.
- Central rules live in `app` `HttpApiConfig`.

---

## 4. Message to Integrante 4

M1 already exposes compilable `StudentAccessPort` for preparatory integration. Real student identity remains blocked by DD-02; use the port interface and test doubles without building homemade authentication.

## 5. Related

| Documento | Rol |
| --- | --- |
| [`DD02-PLAN-IMPLEMENTACION-M1-M7.md`](DD02-PLAN-IMPLEMENTACION-M1-M7.md) | **Enlace canónico** — Plan DD-02 v1.1 (ratificación técnica; sin activación productiva) |
| [`DD02-V010-AUDITORIA.md`](DD02-V010-AUDITORIA.md) | Gate V010 local; numeración condicionada |
| [`DD02-INVENTARIO-FLYWAY.md`](DD02-INVENTARIO-FLYWAY.md) | Conciliación develop/rama/M4/JAR/historiales |
| [`DD02-DICCIONARIO-DATOS.md`](DD02-DICCIONARIO-DATOS.md) | Cuatro estructuras DD-02 |
| [`DD02-DISENO-PERSISTENCIA.md`](DD02-DISENO-PERSISTENCIA.md) | Modelo ER y ciclo de vida |
| [`DD02-PROPUESTA-MIGRACION.md`](DD02-PROPUESTA-MIGRACION.md) | SQL propuesto (no ejecutar) |
| [`DD02-PLAN-PRUEBAS-POSTGRES.md`](DD02-PLAN-PRUEBAS-POSTGRES.md) | Integridad, concurrencia, rollback Flyway |
| [`M1-STUDENT-ACCESS-TEST-PLAN.md`](M1-STUDENT-ACCESS-TEST-PLAN.md) | Plan de pruebas post-ratificación (deny-safe / S1–S10) |
| [`INTEGRACION-M1-M7.md`](INTEGRACION-M1-M7.md) | Mapa de integración M1–M7 |
| [`M1-M7-ESTABILIZACION.md`](M1-M7-ESTABILIZACION.md) | Matriz de estabilización M1/M7 |
| [`M1-CIERRE-24H.md`](M1-CIERRE-24H.md) | Cierre DC-M1-CIERRE-24H-01: cinco capas, un `CaseAuthorizationPort`, DD-02 bloqueado |
