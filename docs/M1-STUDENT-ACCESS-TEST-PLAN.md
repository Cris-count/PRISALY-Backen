# Plan de pruebas — acceso estudiantil desde convocatoria

**Estado:** preparado; **no ejecutado** con adaptador productivo real.  
**Bloqueo:** DD-02 (ratificación académica de identidad estudiantil).  
**Contrato publicado:** [`M1-M5-STUDENT-IDENTITY-CONTRACT.md`](M1-M5-STUDENT-IDENTITY-CONTRACT.md).

## 1. Premisas (no negociables hasta ratificación)

1. No confiar en UUID de estudiante enviados libremente por el cliente.
2. No reutilizar Bearer de personal (`IdentityAccessPort`) como identidad estudiantil.
3. Validar, cuando exista política aprobada: identidad, convocatoria, vigencia y titularidad.
4. Mientras DD-02 esté abierta: `DenyingStudentAccessService` + `POST /v1/acceso-estudiante/validar` → **501**.

## 2. Contrato objetivo (ya compilable)

```text
StudentAccessPort.requireCurrentStudent() → AuthenticatedStudent(userId, studentId)
```

Tras ratificación, el adaptador productivo debe:

1. Resolver identidad del request autenticado (mecanismo que fije DD-02).
2. Comprobar vínculo `Usuario` ↔ `Estudiante` en persistencia M1/catálogo aprobado.
3. Exponer solo `userId` + `studentId`; nunca aceptar `studentId` del body como autoridad.
4. Fallar con códigos de dominio explícitos (no fallbacks permisivos).

## 3. Casos de prueba (activar solo tras ratificación)

| # | Caso | Esperado |
| --- | --- | --- |
| S1 | Credencial/convocatoria válida y vigente | `AuthenticatedStudent` correcto |
| S2 | Credencial inválida / ausente | denegación (`UNAUTHENTICATED` o código DD-02) |
| S3 | Identidad sin vínculo Estudiante | denegación |
| S4 | Convocatoria inexistente o de otra sesión | denegación |
| S5 | Convocatoria fuera de vigencia | denegación |
| S6 | Titularidad: intento/recurso de otro estudiante | denegación (cadena M4/M5) |
| S7 | UUID declarado por cliente distinto al autenticado | denegación (no suplanta) |
| S8 | Proveedor M1 indisponible | código de indisponibilidad; sin acceso |
| S9 | Regresión: personal Bearer no abre student port | denegación |
| S10 | `/validar` deja de ser 501 solo con política ratificada | contrato HTTP acordado |

## 4. Infraestructura de prueba prevista

- JUnit 5 + Mockito (unitario del adaptador).
- PostgreSQL Testcontainers (vínculos Usuario/Estudiante/Convocatoria reales).
- IT en `m1` y/o `app`; **no** inventar datos académicos (plantillas, códigos, mensajes).

## 5. Criterio de salida de este plan

Este documento **no** autoriza implementación. La salida es:

1. Acta/decisión DD-02 ratificada.
2. Sustitución controlada de `DenyingStudentAccessService` por adaptador productivo.
3. Ejecución verde de S1–S10 + regresión M1/M4/M5 deny paths retirados solo donde corresponda.
