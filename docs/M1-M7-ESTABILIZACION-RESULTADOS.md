# Resultados Maven — estabilización M1/M7

**Rama:** `feature/m1-m7-estabilizacion`  
**Fecha local:** 2026-11-01

## Unitarias (sin Docker)

```text
mvnw -pl m1,m7 test -Dtest=IdentityAccessServiceTest,DenyingStudentAccessServiceTest,M4FallbackSinM7Test,M7WorkerDisabledTest -Dsurefire.failIfNoSpecifiedTests=false
```

| Módulo | Resultado |
| --- | --- |
| m1 `IdentityAccessServiceTest` | 8 OK |
| m1 `DenyingStudentAccessServiceTest` | 1 OK |
| m7 `M4FallbackSinM7Test` | 1 OK |
| m7 `M7WorkerDisabledTest` | 2 OK |

## Integración app (Testcontainers PostgreSQL 16.15)

```text
mvnw -pl app -am test -Dtest=DocenteConvocatoriaM1M7IT,NotificacionPortWiringIT,ParticipacionesPortWiringIT -Dsurefire.failIfNoSpecifiedTests=false
```

| Suite | Tests | Resultado |
| --- | --- | --- |
| `DocenteConvocatoriaM1M7IT` | 5 | OK |
| `NotificacionPortWiringIT` | 1 | OK |
| `ParticipacionesPortWiringIT` | 1 | OK |
| **Total app filtrado** | **7** | **BUILD SUCCESS** |

## Evidencias de seguridad cubiertas en esta corrida

- Docente con `GESTIONAR_SESION` → convocatoria + `Aviso` + `EnvioCorreo` Pendiente.
- Docente sin permiso → `M4-SIN-PERMISO`; sin sesión/aviso/correo nuevos.
- Identidad ausente → `M4-NO-AUTENTICADO`.
- `StudentAccessPort` productivo sigue siendo denying (`STUDENT_IDENTITY_UNAVAILABLE`).
- Un solo `NotificacionPort` = `notificacionM7`; worker SMTP ausente con default off.
