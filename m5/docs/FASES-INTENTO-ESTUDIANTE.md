# Fases académicas del intento: rutas estudiantiles P1–P4 (DC-BE-SIM-B2B-B3-32)

Candidato técnico del Incremento B (B2b y B3). Aplica las resoluciones de Dirección Central D-SIM-0, D-SIM-2, D-SIM-3,
D-SIM-4 y D-SIM-10. **No cambia el comportamiento productivo**: con los valores de `application.yml` las rutas no existen y
M5 no ejecuta ninguna sentencia sobre `"FaseIntento"`.

## Interruptores

| Propiedad | Producción | Efecto |
|---|---|---|
| `prisaly.m5.exploracion.fases.enabled` | `false` | Servicios internos de fases (B1, B2a, B2b). Exige el esquema QA `bd0052-v013-sim-ab` o `bd0052-v013-dd02b-sim-ab` y `prisaly.m3.narrativa.publicacion.enabled=true`. |
| `prisaly.m5.exploracion.fases.http.enabled` | `false` | Registra las cuatro rutas de este documento. Encenderla con la anterior en `false` hace fallar el arranque con `M5-FASES-HTTP-REQUIERE-FASES`; aunque las rutas estuvieran montadas, el Proxy respondería `409 FASES_NO_DISPONIBLES`. |
| `prisaly.m5.inicio-finalizacion.publicacion.enabled` | `false` | Interruptor previo y sin cambios de `POST /sesiones/{id}/intentos` y `POST /intentos/{id}/finalizar` (D-SIM-4 usa este contrato). |

## Reglas comunes

- Prefijo `/api/v1/estudiantes/intentos/{idIntento}`. `idIntento` es un UUID; si no lo es, `400 VALIDATION_ERROR`.
- Autenticación con la sesión estudiantil de M1 (cookie, `credentials: "include"`). Sin sesión, `401 UNAUTHENTICATED`. Un Bearer de personal no sustituye la sesión.
- El POST exige un `Origin` autorizado; sin él o con un origen ajeno, `403 FORBIDDEN` sin escribir.
- La titularidad la verifica el Proxy de M5 antes de cualquier lectura de fase. Intento ajeno: `403 FORBIDDEN`; inexistente: `404 INTENTO_NO_ENCONTRADO`. Nunca se acepta un identificador de estudiante del cliente.
- Todas las respuestas 200 llevan `Cache-Control: no-store`.
- Errores: cuerpo `{"code": "...", "message": "..."}` y nada más.
- Instantes en ISO-8601 UTC. `ahoraServidor` es el reloj de PostgreSQL (`clock_timestamp()`) de la misma lectura.

## P1 — `GET /narrativa`

Guion publicado y fijado en la fase del intento al iniciarlo. Si M3 publica después una versión nueva, este intento no cambia.

```json
{ "idIntento": "uuid", "idGuion": "uuid", "version": 1, "hashDefinicion": "64 hex",
  "definicion": { "formato": "prisaly-guion-1", "inicio": "…", "espacios": [], "personajes": [], "intervenciones": [] } }
```

`definicion` es el documento JSON tal como lo validó y persistió M3: solo texto plano, sin preguntas, opciones ni
respuestas. `recursoVisual` solo admite valores del catálogo de M3, que está vacío mientras D-SIM-7 no se apruebe, así
que hoy ningún guion publicado trae recursos visuales. La presentación visual corresponde al Frontend.

| Situación | Respuesta |
|---|---|
| Intento en curso en `EXPLORACION` | 200 |
| Intento en curso en `CUESTIONARIO` (R4) | `409 EXPLORACION_CERRADA` |
| Intento finalizado, en cualquier fase (D-SIM-10) | `409 EXPLORACION_CERRADA`; no reabre ni modifica la fase |
| Intento `SIN_FASES`, o guion no disponible | `409 NARRATIVA_NO_DISPONIBLE` |

## P2 — `GET /fase`

Fase persistida. **Es la única fuente para recuperar el estado tras recargar Angular** o iniciar otra sesión: el
Frontend no debe deducir la fase de su propio estado local.

```json
{ "idIntento": "uuid", "estadoIntento": "EnCurso|Finalizado", "fase": "SIN_FASES|EXPLORACION|CUESTIONARIO",
  "fechaInicioExploracion": "instante|null", "fechaInicioCuestionario": "instante|null",
  "limiteTemporal": "instante", "ahoraServidor": "instante" }
```

Para un intento `SIN_FASES`, las dos fechas de fase son `null`. P2 nunca escribe ni falla por la fase.

## P3 — `POST /cuestionario`

Entrada explícita al cuestionario, sin cuerpo. Es atómica, se ejecuta con la fila `"Intento"` bloqueada y es
**idempotente**: siempre responde 200, y en un reintento devuelve la misma fecha persistida con `yaIniciado=true`.

```json
{ "idIntento": "uuid", "fase": "CUESTIONARIO", "fechaInicioCuestionario": "instante", "yaIniciado": false }
```

| Situación | Respuesta |
|---|---|
| En curso, en `EXPLORACION`, dentro del límite | 200, `yaIniciado=false`; se escribe la fecha una sola vez |
| Ya en `CUESTIONARIO` (también si el límite venció después) | 200, `yaIniciado=true`, misma fecha; nunca se revierte |
| En `EXPLORACION` con el límite vencido en el instante de la escritura (D-SIM-3) | `409 LIMITE_TEMPORAL_VENCIDO`; no escribe ni cierra el intento |
| Intento finalizado | `409 INTENTO_FINALIZADO` |
| Intento `SIN_FASES` | `409 INTENTO_SIN_FASES` |

El límite lo decide la propia escritura: una sola sentencia usa un único `clock_timestamp()` de PostgreSQL como condición (`instante <= limiteTemporal`) y como `fechaInicioCuestionario`, así que una entrada persistida nunca es posterior al límite, aunque el reloj venza entre la lectura y la escritura. Las peticiones concurrentes producen una sola escritura. Frente a un `PUT /respuestas` simultáneo, la respuesta solo
persiste si su escritura es posterior a la entrada; si no, `409 CUESTIONARIO_NO_INICIADO`.

## P4 — `GET /completitud`

Completitud **solo informativa** (D-SIM-5 sin aprobar): no aprueba, no bloquea, no califica ni revela aciertos.

```json
{ "idIntento": "uuid", "preguntasPresentables": 2, "respondidas": 1, "sinResponder": ["uuid"] }
```

Se calcula en la misma instantánea `REPEATABLE READ` que la titularidad, el contenido autorizado de M3 y las respuestas.
En un intento con fases que nunca entró al cuestionario (en exploración, o finalizado desde ella) responde
`409 CUESTIONARIO_NO_INICIADO` sin leer el contenido (D-SIM-2). En un intento `SIN_FASES` responde como siempre.

## Cambios en rutas existentes (solo con fases encendidas)

- `GET /contenido` (D-SIM-2): en un intento con fases que no entró al cuestionario, `409 CUESTIONARIO_NO_INICIADO` sin
  preguntas. Después de entrar, el contenido es el mismo de siempre, también tras finalizar. Los intentos `SIN_FASES` no
  cambian.
- `PUT /respuestas/{idPregunta}` (R2, B2a): en `EXPLORACION`, `409 CUESTIONARIO_NO_INICIADO`.
- `POST /finalizar` (D-SIM-4): se admite desde `EXPLORACION` con el mismo contrato, titularidad e idempotencia. No hay
  nota automática ni cierre automático por vencimiento. La fase queda como estaba. La confirmación visible es del Frontend
  y el Backend no la usa como control.

## Diferencias entre intentos con fases y `SIN_FASES`

Un intento es `SIN_FASES` si se creó sin guion publicado o antes de activar las fases. Conserva el comportamiento
anterior: contenido, respuestas, completitud y finalización sin restricciones de fase. P1 y P3 responden con su error
tipado y P2 informa `SIN_FASES`.

## Recorrido esperado del Frontend

1. Tras iniciar o recargar, leer `GET /fase`.
2. Con `EXPLORACION` y el intento en curso, mostrar la narrativa (`GET /narrativa`) y no pedir `/contenido` ni `/completitud`.
3. Al confirmar, llamar a `POST /cuestionario`. Se puede repetir sin riesgo. Ante `LIMITE_TEMPORAL_VENCIDO`, no reintentar la entrada.
4. Con `CUESTIONARIO`, usar `/contenido`, `PUT /respuestas` y `/completitud`.
5. Para finalizar (desde cualquier fase, con confirmación visible), llamar a `POST /finalizar`. Después, la narrativa
   queda cerrada y, si no se entró al cuestionario, las preguntas siguen ocultas.
