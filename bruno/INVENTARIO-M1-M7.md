# Inventario REST y trazabilidad Bruno — Integrante 1

Rama: `feature/m1-m7-estabilizacion`. SHA base de código: `b078a0c5c776b2730b561f9554c0b143261089d3` (el SHA **probado** se anota tras ejecutar).

M7 no tiene controladores REST. ARQ-008.2/008.3 no están en el repo: no se inventan rutas.

## Inventario HTTP (`/api`)

| Módulo | Método | Ruta | Auth |
| --- | --- | --- | --- |
| app | GET | `/health` | Público |
| M1 | POST | `/v1/personal/autenticar` | Público |
| M1 | POST | `/v1/personal/cerrar-sesion` | Bearer |
| M1 | GET | `/v1/personal/me` | Bearer |
| M1 | GET | `/v1/personal/autorizacion/crear-caso` | Bearer + `CREAR_CASO` |
| M1 | POST | `/v1/acceso-estudiante/validar` | Público; 501 si DD-02 off y body válido |
| M4→M7 | POST | `/m4/sesiones` | Bearer + `GESTIONAR_SESION` |
| M4→M7 | POST | `/m4/sesiones/{sesionId}/convocatorias` | Bearer; encola Aviso/Pendiente |
| M4 | GET | `/m4/sesiones/{sesionId}/convocatorias` | Bearer |

## Tabla de solicitudes (IDs estables PT-DCP-01)

| ID | Nombre Bruno | Módulo | Ruta | Precondiciones | Esperado | Depende de |
| --- | --- | --- | --- | --- | --- | --- |
| PRE-00 | 00-01 Salud | app | GET `/health` | Backend arriba | 200 `ok` | — |
| M1-POST-01 | 01-01 Iniciar sesión | M1 | POST `/v1/personal/autenticar` | `local.secrets`, seed M1 | 200 + token sesión | PRE-00 |
| M1-POST-02 | 01-02 Clave incorrecta | M1 | POST autenticar | Docente existe | 401 `AUTH_FAILED` | PRE-00 |
| M1-POST-03 | 01-03 Usuario inexistente | M1 | POST autenticar | — | 401 `AUTH_FAILED` | PRE-00 |
| M1-NEG-01 | 01-04 Usuario inactivo | M1 | POST autenticar | Seed inactivo | 401 `AUTH_FAILED` | seed M1 |
| M1-POST-07 | 01-05 Datos inválidos | M1 | POST autenticar | — | 400 `VALIDATION_ERROR` | — |
| M1-PERF-01 | 02-01 Consultar identidad | M1 | GET `/me` | Token 01-01 | 200 | M1-POST-01 |
| M1-GET-04 | 02-02 Sin token | M1 | GET `/me` | — | 401 `UNAUTHENTICATED` | — |
| M1-GET-06 | 02-03 CREAR_CASO | M1 | GET autorizacion/crear-caso | Token docente | 200 | M1-POST-01 |
| M1-GET-05 | 02-04-prep + 02-04 | M1 | POST auth + GET crear-caso | Seed sinperm | 200 login; 403 `FORBIDDEN` | seed M1 |
| M1-NEG-02 | 02-05 Token inválido | M1 | GET `/me` | — | 401 | — |
| M1-NEG | 02-06 CREAR_CASO sin token | M1 | GET crear-caso | — | 401 | — |
| M1-POST-08 | 03-01 DD-02 bloqueado | M1 | POST `/validar` | Body DTO válido | 501 | flag false |
| M1-NEG-03 | 03-02 Validar inválido | M1 | POST `/validar` | Body incorrecto | 400 | — |
| M7-PREP-01 | 04-01 Auth GESTIONAR_SESION | M1 | POST autenticar | Seed M7 permiso | 200 | seed M7 |
| M7-PREP-02 | 04-02 Crear sesión | M4 | POST `/m4/sesiones` | Token + caso | 201 + `sesionId` | 04-01 |
| M7-via-M4-01 | 04-03 Convocar | M4/M7 | POST convocatorias | `sesionId` + estudiante | 201 (no SMTP) | 04-02 |
| M7-VER-01 | 04-04 Listar | M4 | GET convocatorias | `sesionId` | 200 | 04-03 |
| M7-via-M4-02 | 04-05-prep + 04-05 | M4 | POST sesiones | noPerm token | 403 `M4-SIN-PERMISO` | seed |
| M7-via-M4-03 | 04-06 Sin token | M4 | POST convocatorias | `sesionId` | 401 | 04-02 |
| M1-POST-09 | 05-01 Cerrar sesión | M1 | POST `/cerrar-sesion` | Token docente | 204 | 01-01 o 04-01 |
| M1-VER-02 | 05-02 Token revocado | M1 | GET `/me` | Tras 05-01 | 401 | 05-01 |

Veredicto de ejecución: [`MATRIZ-RESULTADOS.md`](MATRIZ-RESULTADOS.md) (todas **No ejecutada** hasta Bruno Desktop).
