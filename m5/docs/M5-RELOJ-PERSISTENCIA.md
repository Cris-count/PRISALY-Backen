# M5 | Reloj de las marcas temporales persistidas

Estado al 2026-10-31. Rama `integracion/m5-develop`. Responsable: Integrante 4.

## Fallo

`RepositorioIntentosJpaIT.rechazaRespuestaEnIntentoFinalizado` falló con
`-Pdocker-it`: `DataIntegrityViolationException` por `ck_intento_tiempos`
(V002: `"fin" IS NULL OR "fin" >= "inicio"`). La fila tenía `fin` unos 4 ms
anterior a `inicio`.

## Causa raíz (código productivo)

Una misma fila de `"Intento"` mezclaba dos relojes:

- `inicio` lo fija PostgreSQL. El fixture usa `now()`, igual que los valores
  por defecto y los triggers de BD-005.2 (`now()`, `clock_timestamp()`).
- `fin` lo fijaba `RepositorioIntentosJpa` con `OffsetDateTime.now(Clock.systemUTC())`,
  es decir, el reloj de la JVM.

En la ejecución reportada, la JVM corre en Windows y PostgreSQL en el
contenedor de Docker Desktop. Si el reloj del contenedor va unos milisegundos
por delante, finalizar un intento recién creado produce `fin < inicio` y la
base lo rechaza.

El fixture no es la causa: reproduce cómo la base fija sus propias marcas. El
mismo fallo ocurriría en producción siempre que el servidor de base de datos
vaya por delante del servidor de aplicación. El estudiante no podría finalizar
(409 `CONFLICTO_DATOS`).

La prueba pasaba antes por azar del desfase. No había un `sleep` que la
ocultara.

## Corrección

`RepositorioIntentosJpa.guardar` obtiene la hora con `SELECT clock_timestamp()`
a través de `JdbcTemplate`, en la conexión de la transacción en curso y después
de bloquear la fila (`FOR UPDATE`). La usa para `"Intento"."fin"` y
`"Respuesta"."fechaActualizacion"`.

No cambian:

- V001–V009 y BD-005.2;
- las restricciones;
- el dominio;
- la política HTTP.

## Pruebas

- Se conservan las 19 pruebas de `RepositorioIntentosJpaIT`, incluidas las
  tres M4–M5, y se añade una (20 en total).
- Regresión nueva: `marcasTemporalesUsanElRelojDeLaBase`. Comprueba que `fin` y
  `fechaActualizacion`:
  - caen entre dos lecturas de `clock_timestamp()` hechas antes y después del
    caso de uso;
  - cumplen `fin >= inicio`.

  Fallaría si se volviera a usar el reloj de la JVM con un desfase mayor que la
  duración del caso de uso.

## Alcance y límites

- Esto fija el reloj de las **marcas de persistencia** de M5, en línea con la
  base. **No** decide el "reloj académico" para el límite temporal, la
  elegibilidad ni `TIEMPO_VENCIDO`. M4 indica que adoptar el reloj de
  PostgreSQL con ese fin requiere ratificación de Dirección Central.
- Cuando se implemente el inicio de intentos, `inicio` y `limiteTemporal`
  deberían usar el mismo reloj.
- Cada guardado hace una consulta adicional a la base.
