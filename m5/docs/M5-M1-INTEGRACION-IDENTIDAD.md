# M5 ← M1 | Integración preparatoria de la identidad estudiantil

Estado al 2026-10-31. Rama `feature/m5-participacion-intentos`. Responsable: Integrante 4.

## 1. Contrato de M1 verificado

Fuente: `ab926ec` (`feature/m1-identidad-acceso`) y `docs/M1-M5-STUDENT-IDENTITY-CONTRACT.md`.

| Elemento | Contrato publicado |
|---|---|
| Puerto | `com.prisaly.m1.application.port.StudentAccessPort` |
| Operación | `AuthenticatedStudent requireCurrentStudent()` |
| Identidad | `AuthenticatedStudent(UUID userId, UUID studentId)`: `Usuario.idUsuario` y `Estudiante.idEstudiante` |
| Denegación | `com.prisaly.m1.domain.rule.IdentityDomainException` (`code()`); hoy `STUDENT_IDENTITY_UNAVAILABLE` |
| Implementación de producción | `DenyingStudentAccessService`: deniega siempre (DD-02 abierta) y no lee `SecurityContext` |
| HTTP | Sin credenciales: 401 (cadena de seguridad). Autenticado sin contexto de estudiante: 403 |

**Disponibilidad:**

- `ab926ec` está en `develop` (`03b0899`, PR #5).
- **No está en esta rama**: el último merge de develop fue `a42c738`.
- Integrar `develop` sería limpio (`git merge-tree` sin conflictos; develop no toca `m5/`), pero **no está autorizado**.

## 2. Diferencias con el puerto provisional de M5

| M5 (`IdentidadEstudianteActual`) | M1 (`StudentAccessPort`) | Resolución en el adaptador |
|---|---|---|
| `Optional<IdentificadorEstudiante> actual()` | `requireCurrentStudent()` lanza excepción si deniega | `IdentityDomainException` (cualquier código) → vacío → `EstudianteNoIdentificadoException` |
| Identificador opaco (`String`) | `UUID studentId` y `UUID userId` | Se usa solo `studentId.toString()`; `userId` nunca autoriza titularidad |
| Sin dependencia de Spring Security | No lee `SecurityContext` | El adaptador no lee contexto ni parámetros del cliente |
| Errores de proveedor no previstos | — | Se propagan: nunca conceden acceso |

Ajuste ya aplicado en esta rama: `EstudianteNoIdentificadoException` se traduce a
**403 `STUDENT_IDENTITY_UNAVAILABLE`**, como indica el contrato de M1 (antes
era 401). El 401 sin credenciales lo sigue respondiendo la cadena compartida.

## 3. Adaptador preparado (parche, sin aplicar)

`m5/docs/parches/0001-feat-m5-adaptar-la-identidad-estudiantil-de-M1.patch` contiene:

- `m5/pom.xml`: dependencia de `com.prisaly:m1`.
- `m5/.../infrastructure/identidad/IdentidadEstudianteDesdeM1.java` (`@Component`).
- `m5/.../infrastructure/identidad/IdentidadEstudianteDesdeM1Test.java`: 9 casos.

No se aplica en esta rama porque no compila sin el código de M1. Se verificó en
una rama local de borrador (M5 + develop `03b0899`, no publicada):

- M5 compila con `-Werror` (el contrato de M1 se compiló aparte: su
  `IdentityDomainException` emite un aviso de `serialVersionUID` que es de M1).
- La suite unitaria pasa 121/121 con JUnit 5.10.2, fuera de Maven.
- `git apply --check` del parche sobre (esta rama + develop `03b0899`) es OK.

**Para aplicarlo, una vez autorizada la integración de develop:**

```powershell
git merge --no-ff origin/develop
git am m5/docs/parches/0001-feat-m5-adaptar-la-identidad-estudiantil-de-M1.patch
.\mvnw.cmd -pl m5 -am test
```

Aun aplicado, **no habilita autenticación estudiantil**: con
`DenyingStudentAccessService`, toda verificación termina en denegación hasta DD-02.

## 4. Titularidad

Cadena en BD-005.2: `Intento."idParticipacion"` → `Participacion."idConvocatoria"`
→ `Convocatoria."idEstudiante"`.

- `Intento` y `Participacion` son tablas de M5, así que M5 resuelve ese tramo.
- `Convocatoria` es de M4. El último paso requiere un **contrato Java de M4**
  (convocatoria → estudiante), no consultas a sus tablas ni a sus repositorios.
- El titular se compara con `studentId` de M1, nunca con un identificador
  enviado por Angular.
- Sin el contrato de M4, `TitularidadIntentos` no tiene implementación y el
  control de acceso no se conecta: **la denegación se mantiene**.

## 5. Escenarios de prueba

| # | Escenario | Dónde | Estado |
|---|---|---|---|
| 1 | Estudiante autenticado y titular: autorizado | `IdentidadEstudianteDesdeM1Test` (parche) y `ControlAccesoIntentosTest` | Ejecutada en el borrador y en la rama |
| 2 | Autenticado, no titular: denegado | Ambas | Ejecutada |
| 3 | Sin identidad: denegado | Ambas | Ejecutada |
| 4 | DD-02 abierta (`DenyingStudentAccessService` real): denegado | Parche | Ejecutada en el borrador |
| 5 | Token de personal en el contexto: sin identidad estudiantil | Parche | Ejecutada en el borrador |
| 6 | Fallo del proveedor: se propaga, sin acceso | Ambas | Ejecutada |
| 7 | Ningún dato del cliente sustituye la identidad | Ambas (firma de la API) | Ejecutada |

Todas usan dobles (titularidad simulada; identidad de M1 simulada salvo en 4
y 5). **No equivalen a una integración real M1–M5.** Esa integración necesita
DD-02, el contrato de M4 y controladores con rutas de ARQ-008.3.

## 6. Bloqueos

1. Autorización para integrar `develop` (`03b0899`) en la rama M5.
2. DD-02: autenticación real del estudiante (M1).
3. Contrato Java de M4 para el titular de la convocatoria.
4. ARQ-008.3: rutas de estudiante (sin ellas no hay controladores).
