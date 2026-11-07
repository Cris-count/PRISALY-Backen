# M1 — Cierre técnico DC-M1-CIERRE-24H-01

Rama: `feature/m1-m7-estabilizacion`  
HEAD verificado: `ad24269d5476746f84bb28f05f670d27c2e5cbb5`  
`origin/develop` `3563177fb673ce374197907e199c4817d138d705` es ancestro de HEAD.  
Árbol limpio al iniciar este cierre (salvo este documento y el resultado de pruebas que se anote abajo).

No existe en el repositorio el archivo `DC-AUD-BACKEND-FINAL-01`. Sin esas filas no se inventan hallazgos ni se cambia código productivo. DD-02 no se activa.

Contrato vigente de consumo M5: [`M1-M5-STUDENT-IDENTITY-CONTRACT.md`](M1-M5-STUDENT-IDENTITY-CONTRACT.md).

## Contrato mínimo M5 (cinco capas)

| Capa | Quién | Estado en este SHA |
| --- | --- | --- |
| 1. Identidad autenticada de personal | `IdentityAccessPort` + Bearer provisional | Implementado |
| 2. Estado institucional de la cuenta | `AuthenticatedUser.accountStatus` / usuario activo | Implementado para personal |
| 3. Vínculo identidad ↔ estudiante | `AuthenticatedStudent(userId, studentId)` | Contrato publicado; **sin** mapeo productivo `Usuario`→`Estudiante` |
| 4. Autorización de acceso académico estudiantil | `StudentAccessPort.requireCurrentStudent()` | Productivo: `DenyingStudentAccessService` (`STUDENT_IDENTITY_UNAVAILABLE`). Flag `prisaly.m1.student-access.enabled=false` → `POST /v1/acceso-estudiante/validar` **501** |
| 5. Titularidad de sesión, convocatoria e intento | M4 / M5 | Fuera de M1. M5 no debe usar el Bearer de personal como estudiante |

M5 consume solo `IdentidadEstudianteDesdeM1` → `StudentAccessPort`. Mientras DD-02 no esté ratificada, toda llamada estudiantil se deniega. No hay segundo proveedor de `StudentAccessPort`.

## M1-02 — un solo `CaseAuthorizationPort`

`CaseAuthorizationM1Config` (`caseAuthorizationFailClosed`) fue retirado. El único bean en `app` es `M3CaseAuthorizationAdapter` (`M3CasoCreationConfig`):

- `canCreateCase`: identidad M1 activa, coincidencia de ids y permiso `CREAR_CASO`.
- `canModifyCase` / `canPublishCase`: siempre `false` (fail-closed).
- Errores de identidad no se convierten en autorización.

No se añade otro adaptador.

## M1-04 — rutas no registradas

`HttpApiConfig` exige autenticación en `anyRequest`. Sin Bearer, una ruta desconocida responde **401** `UNAUTHENTICATED` antes del enrutado MVC. Con sesión y sin mapping, el contenedor responde 404 o 405. No se fuerza un 404 global: revelaría existencia de rutas y debilitaría recursos protegidos. Corrección de un caso concreto queda **bloqueada** hasta una fila de `DC-AUD-BACKEND-FINAL-01`.

## M1-05 — secretos

En `m1` y `app` (main) no hay logger que imprima contraseña, token ni cabecera `Authorization`. No se añaden logs de credenciales.

## Matriz

| ID | Estado | Acción en este cierre |
| --- | --- | --- |
| M1-01 | Implementado | Solo regresión unitaria; IT HTTP pendiente de Docker |
| M1-02 | Ya resuelto | Verificado en código; prueba unitaria `M3CaseAuthorizationAdapterTest` |
| M1-03 | Deny-by-default | Documentado aquí; sin activar DD-02 |
| M1-04 | Observado | Sin cambio; bloqueado sin fila AUD |
| M1-05 | Sin hallazgo en código | Sin cambio |
| DD-02 | Bloqueado | Elevación a Dirección si se pide desbloqueo |
| Docker `*IT` | P2 no ejecutado | Docker Desktop no estaba en marcha; no se declaran aprobadas |

## Pruebas

Comando unitario (sin `*IT`):

```text
.\mvnw.cmd -pl m1,app -am test "-Dtest=DenyingStudentAccessServiceTest,EstudianteAccessValidarTest,PersonnelAuthenticationServiceTest,M3CaseAuthorizationAdapterTest,M3CasoCreationTest" -Dsurefire.failIfNoSpecifiedTests=false
```

Resultado: ver sección «Resultado de ejecución» al pie tras correr el comando.

IT con Docker (no ejecutadas en este cierre):

```text
.\mvnw.cmd -pl app -am verify "-Dtest=PersonalAuthSecurityIT,EstudianteIntentosM5SecurityIT,M3CasoCreationIT" -Dsurefire.failIfNoSpecifiedTests=false
```

El BUILD SUCCESS del 6 nov 2026 que excluyó `*IT` y `PostgreSqlDockerSmokeTest` **no** cubre estas tres clases.

## Resultado de ejecución

Fecha: 2026-11-06 17:47 (-05). **BUILD SUCCESS**. 25 pruebas, 0 fallos, 0 errores, 0 omitidas en las clases pedidas.

| Módulo | Clase | Pruebas |
| --- | --- | --- |
| m1 | `DenyingStudentAccessServiceTest` | 1 |
| m1 | `PersonnelAuthenticationServiceTest` | 5 |
| m1 | `EstudianteAccessValidarTest` | 4 |
| app | `M3CaseAuthorizationAdapterTest` | 9 |
| app | `M3CasoCreationTest` | 6 |

m2–m7 no tenían esas clases (`failIfNoSpecifiedTests=false`); no se ejecutó el resto de la suite ni ningún `*IT`.

Docker: el daemon no respondió (`npipe://./pipe/dockerDesktopLinuxEngine` ausente). `PersonalAuthSecurityIT`, `EstudianteIntentosM5SecurityIT` y `M3CasoCreationIT` quedan **P2, no ejecutadas**. No se declaran aprobadas.

`DC-AUD-BACKEND-FINAL-01` sigue ausente. Sin filas M1 no hay corrección de código que mapear.
