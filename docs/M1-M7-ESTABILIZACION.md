# PRISALY — Incremento de estabilización M1/M7

**Responsable:** Integrante 1  
**Referencia de auditoría:** develop `ee77a579`  
**HEAD de trabajo:** tip de `feature/m1-m7-estabilizacion`  
**Alcance:** M1, M7 y `app` (pruebas/docs). Sin cambios en M2–M6 ni migraciones históricas.

## 1. Fase 1 — Matriz de dependencias (sin cambiar contratos)

| Consumidor | Contrato | Proveedor productivo | Fallback | Estado |
| --- | --- | --- | --- | --- |
| M4 | `IdentityAccessPort` | M1 `IdentityAccessService` | `identidadNoDisponible` → denegación | Operativo |
| M4 | `StudentAccessPort` | M1 `DenyingStudentAccessService` | lambda `STUDENT_IDENTITY_UNAVAILABLE` | Deny-safe (DD-02) |
| M5 | `StudentAccessPort` | mismo denying | — | Deny-safe (DD-02) |
| M3 | — | — | — | **No consume M1** |
| M4 | `NotificacionPort` (contrato M4) | M7 `NotificacionM4Adapter` (`notificacionM7`) | `notificacionesNoDisponibles` | Operativo |

```text
M1 IdentityAccessService ──► M4 IdentidadM1Adapter ──► SesionAcademicaService
M1 DenyingStudentAccess  ──► M4 ProgramacionAutenticadaM1Adapter
M1 DenyingStudentAccess  ──► M5 IdentidadEstudianteDesdeM1
M4 NotificacionPort      ◄── M7 NotificacionM4Adapter
```

## 2. Decisiones académicas / hallazgos

| Id | Estado | Impacto en este incremento |
| --- | --- | --- |
| **DD-02** | Abierta | Acceso estudiantil permanece denegado; `/validar` = 501 |
| **DD-15** | Abierta (rules Cursor; 0 hits Java) | No se implementa `IAutorizacionDocente` formal |
| **DD-16** | Abierta (rules Cursor; 0 hits Java) | Sin conciliación nueva de política docente |
| **H01 / H05 / H12** | Sin trazas en Backend | Cubiertos por refuerzo de authz docente + recorrido M4–M7 + deny estudiantil |

Política `GESTIONAR_SESION` ya usada por M4 vía M1; ratificación formal sigue pendiente en informe BD-005.2 de M4 (no se inventa política nueva aquí).

## 3. Trabajo autorizado ejecutado

### A. M1
- Refuerzo unitario de `IdentityAccessService`: identidad ausente, usuario inexistente, rol, permiso `GESTIONAR_SESION` y denegación sin asignación.
- Deny-safe estudiantil intacto (`DenyingStudentAccessService`).
- Sin activar autenticación estudiantil.

### B. Identidad estudiantil
- Contrato vigente: `docs/M1-M5-STUDENT-IDENTITY-CONTRACT.md`.
- Plan de pruebas post-ratificación: `docs/M1-STUDENT-ACCESS-TEST-PLAN.md`.
- **No** se activa adaptador productivo real.

### C. M7
- Confirmado: monolito registra un solo `NotificacionPort` = `notificacionM7`.
- IT de humo en `app`: docente autenticado M1 + `convocarEstudiante` + filas `Aviso` / `EnvioCorreo` Pendiente.
- Worker SMTP sigue **off** por defecto; sin plantillas ni credenciales inventadas.

## 4. Pruebas obligatorias (cobertura)

| Escenario | Dónde |
| --- | --- |
| Docente autenticado y autorizado | `DocenteConvocatoriaM1M7IT`, M4 ITs existentes |
| Docente sin permiso | `DocenteConvocatoriaM1M7IT`, `IdentityAccessServiceTest` |
| Recurso de otro propietario | M4 `AutorizacionM4ServiceTest` / `M4M1ContratosPostgreSqlIT` |
| Identidad ausente | `IdentityAccessServiceTest`, `DocenteConvocatoriaM1M7IT` |
| Proveedor indisponible | `M4FallbackSinM7Test`, adapters M4 |
| Acceso estudiantil denegado (DD-02) | `DenyingStudentAccessServiceTest`, `PersonalAuthSecurityIT` |
| Acceso estudiantil válido/inválido | **Solo tras ratificación** — ver plan |
| Aviso persistido / TX revertida / M4–M7 | `M7ConvocatoriaPostgreSqlIT`, `DocenteConvocatoriaM1M7IT` |
| Regression wiring | `NotificacionPortWiringIT`, `ParticipacionesPortWiringIT` |

## 5. Restricciones respetadas

- No se modificaron M2, M3, M4, M5, M6 ni Flyway V001–V009.
- No hay accesos de prueba en producción ni políticas estudiantiles inventadas.
- Parte estudiantil real detenida por DD-02; resto del incremento continúa.
