# DC-REV-PR69-01 / DC-PR69-REV-02 — dictamen Nicolás, persistencia

8 de noviembre de 2026. **FAVORABLE CON OBSERVACIÓN NO BLOQUEANTE**, limitado a
PostgreSQL, integridad, transacciones y D-06. Riesgo residual **bajo** dentro de
ese alcance. No se reprodujo un defecto productivo que requiera PR correctivo.
La aceptación global corresponde a Dirección Central tras consolidar los cuatro dictámenes.

## Identificación y archivos

- PR #69: https://github.com/Cris-count/PRISALY-Backen/pull/69
- Base: `3f97e7e22aac18f655461b882dfd9a5f0f2195d6`.
- Entrega PR: `37741db4910d23e38d5eeca67a957f7e2ad6ea9f`.
- **Integración revisada y ejecutada: `b97ba887629b6229fdd9676877e31049be4c3461`.**
- Los árboles Git completos de entrega e integración son idénticos
  (`git diff --quiet 37741db b97ba887`, salida 0). Esto no sustituye la atribución
  de la ejecución al SHA de integración anterior.
- Los 16 paths del diff base→integración coinciden exactamente con la lista de
  GitHub: siete productivos M5 y nueve de pruebas (ocho M5, una M6).
  Inventario y blobs antes/después: `evidencias/DC-REV-PR69-NICOLAS/archivos-revisados.json`.

Archivos productivos inspeccionados, bajo `m5/src/main/java/com/prisaly/m5/`:

| Archivo | Resultado de inspección |
|---|---|
| domain/respuesta/ReferenciaPregunta.java | UUID completo canónico; no altera referencia opaca |
| domain/respuesta/ValidacionRespuesta.java | Patrón de UUID completo y Locale.ROOT; no trim ni coerción de otros textos |
| domain/respuesta/ValorRespuesta.java | UUID de opción consistente con persistencia; equivalencia no fabrica revisión nueva |
| infrastructure/persistencia/MapeoIntento.java | Violaciones de dominio al reconstruir respuestas pasan a fallo técnico de persistencia |
| infrastructure/persistencia/RepositorioIntentosJpa.java | INNER JOIN exige vínculo Participacion existente; no modifica escritura, MANDATORY, FOR UPDATE ni flush |
| presentation/ManejadorErroresM5.java | Amplía captura técnica después de ejecutar el servicio; no absorbe errores dentro de la transacción |
| presentation/TraductorErroresM5.java | Conserva conflicto de integridad y distingue fallo técnico; ver límite sobre OBS-C69-01 abajo |

Pruebas incorporadas e inspeccionadas:

- `m5/.../domain/respuesta/ReferenciasCanonicasTest.java`.
- `m5/.../domain/respuesta/ValorRespuestaContratoTest.java`.
- `m5/.../infrastructure/persistencia/MapeoIntentoFilasCorruptasTest.java`.
- `m5/.../infrastructure/persistencia/FallosTecnicosM5IT.java`.
- `m5/.../infrastructure/persistencia/RobustezPersistenciaM5IT.java`.
- `m5/.../infrastructure/persistencia/RevisionMacro02PersistenciaIT.java`.
- `m5/.../presentation/ErroresTecnicosM5Test.java`.
- `m5/.../presentation/RespuestaHttpErroresTecnicosTest.java`.
- `m6/.../application/integration/ModelosLimitesEInmutabilidadTest.java`.

Contexto adicional inspeccionado: fachada transaccional, coordinación/reserva
M4, titularidad M5 desde M4, lector de evaluación, wiring y fixtures de persistencia.
No se cambió ninguno de esos archivos durante la revisión.

## Pruebas independientes verificadas

Ejecución: https://github.com/Cris-count/PRISALY-Backen/actions/runs/42527556523

El runner hace checkout fijo de **b97ba887**, separado del checkout del ejecutor
`b7b36bbb27f81a00597306731a0f97bea4ef5b68`. Java 21, PostgreSQL **16.15** en
Testcontainers desechables; sin bases compartidas, migraciones candidatas ni cambios de políticas.

| Suite | Tipo | Casos aprobados |
|---|---|---:|
| ReferenciasCanonicasTest | Unitaria | 3 |
| ValorRespuestaContratoTest | Unitaria | 3 |
| MapeoIntentoFilasCorruptasTest | Unitaria | 2 |
| ErroresTecnicosM5Test | Unitaria / MockMvc | 3 |
| RespuestaHttpErroresTecnicosTest | MockMvc | 3 |
| ModelosLimitesEInmutabilidadTest | Unitaria con dobles | 3 |
| M4CoordinacionInicioTest | Unitaria de contrato | 4 |
| **Subtotal unitarias/MVC** | | **21** |
| FallosTecnicosM5IT | PostgreSQL | 1 |
| RevisionMacro02PersistenciaIT | PostgreSQL, con dobles identificados | 5 |
| RobustezPersistenciaM5IT | PostgreSQL | 2 |
| ConcurrenciaLectorM5M6ReservaD06IT | PostgreSQL/concurrencia | 3 |
| ConsultaIntentoEvaluacionM5M6IT | PostgreSQL/lector | 3 |
| HistorialM4M5IT | APP/PostgreSQL/D-06 con políticas fixture | 23 |
| ParticipacionesPortWiringIT | APP/PostgreSQL/wiring | 1 |
| **Subtotal PostgreSQL** | | **38** |

**Cero fallos, errores u omisiones; 14 suites presentes; BUILD SUCCESS.**
Se verificaron SHA del checkout, XML, testcase y ausencia de suites requeridas
faltantes. Artefacto `pr69-m4-42527556523-1`, ID **13635363909**, 58 064 bytes,
descargado e inspeccionado. Copia persistente en `docs/evidencias/DC-REV-PR69-NICOLAS/`.

Las pruebas MVC no equivalen a HTTP de APP real ni prueban autorización productiva.
Las IT usan identidad/elegibilidad/autorización controladas donde lo indican sus
fixtures. La corrupción huérfana se crea con `SET LOCAL session_replication_role`
solo dentro de contenedores efímeros; no se deshabilitó integridad productiva.

## Resultados de persistencia y D-06

- UUID en distinta caja no elude CAS/revisión ni duplica respuestas; valores
  opacos se conservan. No se altera la representación uuid de BD-005.2.
- Corrupción de respuestas reconstruidas produce fallo técnico de persistencia,
  sin convertirla en una operación exitosa o una autorización.
- Se reprodujeron violaciones reales de FK compuesta y trigger de pertenencia
  al Caso. Se conserva el rechazo y no sobreviven escrituras.
- Un INSERT seguido por UPDATE bloqueado falla y revierte la unidad de trabajo
  completa. El timeout permite reintento después de liberar la reserva.
- El lector no bloquea la reserva D-06, conserva su snapshot REPEATABLE_READ y,
  anidado desde escritura, no altera conexión, READ COMMITTED ni read-only de la
  transacción llamadora.
- Historial/start-edición, inicio concurrente, rollback, timeout y ausencia de
  huérfanos por el flujo normal continúan aprobados en las 23 IT de APP.
- Wiring acredita proveedor esperado único. El diff no añade repositorios,
  gestores transaccionales ni proveedores. No cambia locks Sesion→Convocatoria
  antes de Participacion→Intento ni la plantilla de escritura.
- `git diff --quiet base merge -- m4/src app/src/main/resources/db
  m5/src/main/java/com/prisaly/m5/infrastructure/programacion
  m5/src/main/java/com/prisaly/m5/infrastructure/FachadaParticipacionTransaccional.java`
  devuelve 0: no hay efectos laterales de código sobre migraciones o D-06.

## Observación reproducible N69-01 — no bloqueante

**Archivo/método:** `RepositorioIntentosJpa.vinculo` y consumidor protegido.
Un Intento con Participacion inexistente, creado saltando restricciones en el
fixture efímero, se observa como ausencia mediante el INNER JOIN. No repara el
huérfano ni demuestra que los datos corruptos hayan desaparecido.

**Reproducción ejecutada:** `RobustezPersistenciaM5IT.vinculoDeIntentoHuerfanoEsAusenteYNoNullPointer`
y `RevisionMacro02PersistenciaIT.huerfanoConTitularidadRealSeVeComoAusencia`.
Si un doble afirma titularidad propia pese al vínculo ausente, la preparación
rechaza como inconsistencia técnica, comprobado por
`vinculoAusenteTrasPasarTitularidadEsInconsistenciaNoAusencia`.

**Riesgo bajo:** comportamiento intencional, documentado y cerrado al acceso.
En el flujo normal las FK impiden ese huérfano. No se propone cambiar el contrato
ni presentar el JOIN como sustituto de restricciones o de una auditoría de datos
de un entorno compartido. No es un defecto productivo reproducido ni requiere corrección.

## Límite de dictamen y consolidación

Este dictamen no resuelve unilateralmente OBS-C69-01/02 de Cristian. La prueba
SQL de esta campaña demuestra los casos concretos de integridad y rollback;
no certifica que toda DataIntegrityViolationException posible deba ser 409.
La clasificación institucional/global y composición solicitadas siguen a cargo
de Cristian; contratos académicos corresponden a Mariana.

No se revalida aquí el total local de 1168/1150 ni las 18 omisiones reportadas
por Ingrid: son otra campaña. La selección actual tiene cero omisiones; no
incluye las suites especiales M4 ni repite las 114 pruebas históricas.

No hay cambios productivos, académicos, de migraciones o CI estándar. El PR #71
solo permite la ejecución temporal de esta revisión; no es correctivo ni necesita
merge para obtener resultados. No se modificó develop ni se hicieron merges.

La fusión de #69 no se usa como aprobación. Se deja dictamen propio de Nicolás
para que Dirección Central consolide la resolución. No se habilitan DD-02/03/04,
D07, cálculo/persistencia de calificaciones ni proveedores institucionales.
