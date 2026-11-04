# Matriz de resultados Bruno — Integrante 1

Fuente de esta actualización: revisión de capturas del Integrante 1 (HTTP visible), no una re-ejecución independiente ni el resultado de `bru run`.

- SHA Git HEAD: `b078a0c5c776b2730b561f9554c0b143261089d3`
- Working tree: sucio (Qualifier local, colección Bruno 00–05 sin commit). El JAR en ejecución incluye el Qualifier; el SHA de Git **no** cubre aún esos archivos.
- Fecha de revisión de capturas: 2026-11-03
- Probador: Integrante 1
- Veredicto: **Coincide (captura)** solo si hay HTTP visible alineado al esperado. M7-via-M4-03 no se aprueba: `sesionId` no resuelto.

Convención de captura (el PNG solo si existe evidencia real, token oculto): `{ID}_{Tema}_{HTTP}.png`

| ID | Módulo | Objetivo | Precondiciones | Método y ruta | Datos de entrada | Resultado esperado | Resultado observado | Coincidencia | Veredicto | Fecha | Probador | SHA HEAD | Captura | Observaciones |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PRE-00 | app | Salud | Backend 8080 | GET `/api/health` | — | 200 | | | No ejecutada (fuera de las 12) | | | b078a0c | | Health 200 comprobado en entorno, no cuenta en las 12 |
| M1-POST-01 | M1 | Auth válida | local.secrets, seed | POST `/v1/personal/autenticar` | docente | 200 | 200 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | Ocultar Bearer en evidencia definitiva |
| M1-POST-02 | M1 | Clave incorrecta | Docente existe | POST autenticar | password incorrecto | 401 AUTH_FAILED | 401 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-POST-03 | M1 | Usuario inexistente | — | POST autenticar | unknownEmail | 401 AUTH_FAILED | 401 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-GET-04 | M1 | /me sin token | — | GET `/me` | sin Authorization | 401 UNAUTHENTICATED | 401 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-GET-05 | M1 | Sin CREAR_CASO | noPerm token | GET crear-caso | Bearer noPerm | 403 FORBIDDEN | 403 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-GET-06 | M1 | CREAR_CASO | Token docente | GET crear-caso | Bearer docente | 200 | 200 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-POST-07 | M1 | Validación | — | POST autenticar | body inválido | 400 VALIDATION_ERROR | 400 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-POST-08 | M1 | DD-02 off | Flag false | POST `/validar` | challengeId + 8 dígitos | 501 ESTUDIANTE_ACCESS_PENDING_CONTRACT | 501 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | |
| M1-POST-09 | M1 | Logout | Token docente | POST `/cerrar-sesion` | Bearer | 204 | 204 | Sí | Coincide (captura) | 2026-11-03 | Integrante 1 | b078a0c | | Tras M1; no cierra el flujo M7 |
| M7-PREP-01 | M1 | Auth GESTIONAR_SESION | Seed M7 | POST autenticar | docente | 200 | — | — | No ejecutada (evidencia individual) | | | b078a0c | `M7-PREP-01_AuthDocente_200.png` | Ejecutar 04-01 |
| M7-PREP-02 | M4 | Crear sesión | 04-01 + caso seed | POST `/m4/sesiones` | casoIdBruno | 201 + `sesionId` | — | — | Sin evidencia visible | | | b078a0c | `M7-PREP-02_CrearSesion_201.png` | Guardar sesionId |
| M7-via-M4-01 | M4/M7 | Convocar | sesionId | POST convocatorias | estudianteIdBruno | 201 (no SMTP) | — | — | Sin evidencia visible | | | b078a0c | `M7-via-M4-01_Convocatoria_201.png` | 201 ≠ correo enviado |
| M7-VER-01 | M4 | Listar | 04-03 | GET convocatorias | sesionId | 200 | — | — | No ejecutada (fuera de las 12) | | | b078a0c | | |
| M7-via-M4-02 | M4 | Sin permiso | noPerm token | POST `/m4/sesiones` | caso seed | 403 M4-SIN-PERMISO | 403 | Sí | Coincide (captura); no cuenta en las 9 M1 | 2026-11-03 | Integrante 1 | b078a0c | | No usa sesionId |
| M7-via-M4-03 | M4 | Sin token | sesionId resuelto | POST convocatorias | estudiante, sin Auth | 401 | 401 (URL con `{{sesionId}}` sin resolver) | No demostrada | Preparación incompleta | 2026-11-03 | Integrante 1 | b078a0c | | Repetir tras 04-02 |
| M1-VER-02 | M1 | Token revocado | 05-01 | GET `/me` | mismo Bearer | 401 | — | — | No ejecutada (fuera de las 12) | | | b078a0c | | |

## Resumen de las 12 pruebas

| Grupo | Cantidad |
| --- | --- |
| Coincide HTTP visible (M1) | 9 |
| Coincide HTTP visible M7 (M7-via-M4-02) | 1 (no entra en las 9 M1) |
| Sin captura individual (flujo positivo M7: 04-02 / 04-03) | 2 |
| Preparación incompleta (`sesionId` en 04-06) | 1 |

Causa de M7 incompleto (comprobada en PostgreSQL, 2026-11-03): el docente solo tenía `CREAR_CASO`; no existían `GESTIONAR_SESION`, el caso `33333333-…-3301` ni el estudiante `22222222-…-2201`. Se aplicó **solo** `m7-bruno-seed-local.sql` (no se re-sembró M1). Tras el seed, el docente debe poder ejecutar 04-01 → 04-02 → 04-03 → 04-06 en Bruno Desktop.

## Pendiente en Bruno Desktop (no crear más solicitudes)

1. `04-01` autenticar docente.
2. `04-02` crear sesión → 201 y `sesionId` en variables de ejecución.
3. `04-03` convocar → 201. Captura sin afirmar SMTP.
4. `04-06` convocar **sin** token, misma `sesionId` ya resuelta → 401.
5. Recortar Bearer en todas las evidencias; no reutilizar capturas con token visible.
