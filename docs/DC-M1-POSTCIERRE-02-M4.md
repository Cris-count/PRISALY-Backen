# DC-M1-POSTCIERRE-02 — guarda M4 y conciliación Flyway

8 de noviembre de 2026. Nicolás. Base: `cf57327ea4e7d6fde22c716db5b4ea5a34da3546`.
Rama `fix/m4-guarda-esquema-dd02`. PR independiente #66, inicialmente en borrador.

## Contrato de compatibilidad

Se sustituye la aceptación por contador por identidad de las 21 tablas BD-005.2.
Las tablas adicionales o ausentes se rechazan por nombre, incluidas las tablas
paralelas V010. Se comprueba además una referencia estructural versionada que
incluye columnas, tipos, nulabilidad, defaults/identidad, claves y expresiones
CHECK, índices/predicados, triggers habilitados y funciones, secuencias (no su
valor actual) y políticas de fila. No intervienen datos de estudiantes, OIDs,
propietarios ni fechas de instalación.

La huella independiente de historial incluye todas las filas Flyway: versión,
tipo, script, checksum y resultado. Una versión o checksum inesperados bloquean;
no se eliminan filas, no se ejecuta repair ni se modifica una migración histórica.
Las referencias se generan exclusivamente a partir del SQL identificado en
PostgreSQL 16.15 efímero y luego se versionan; el arranque no aprende referencias
del entorno que está validando.

`prisaly.m4.schema-contract` admite únicamente:

- `bd0052-v009`: valor predeterminado, V001–V009 y tablas históricas.
- `bd0052-dd02-v012`: compatibilidad técnica explícita con las mismas tablas más
  ActivacionCuentaEstudiante y SesionEstudiante, V011 y V012 completas.

La segunda selección **no es ratificación ni permiso de despliegue**. Se prueba
solo en bases efímeras mientras V011/V012 continúen candidatas. No se modifica
application.yml ni sus flags. Un operador no debe seleccionar este contrato en
un entorno compartido antes de registrar la aprobación y conciliar su historial.
No existe una propiedad para admitir listas arbitrarias de tablas ni un contador
alternativo. Cambios estructurales posteriores requieren una nueva referencia
revisada; no son aceptados por inferencia.

## Línea de migraciones y decisiones pendientes

| Línea | Estado comprobado en Git | Acción de esta entrega |
|---|---|---|
| BD-005.2 / V001–V009 | Descubrimiento automático vigente | Preservar archivos/checksums; comprobar identidad y estructura |
| V010 M4 paralela | Archivada en recursos m4-propuesta; fuera de descubrimiento automático | Rechazar esquema/historial divergentes, conservar evidencia; no borrar ni repair |
| V011 DD-02 | db/dd02-propuesta/V011__acceso_estudiante_cuenta.sql | No mover ni reescribir; aplicar únicamente en fixtures explícitos |
| V012 | db/dd02-propuesta/V012__dd02_integridad_vinculo.sql | No mover ni reescribir; comprobar integridad estructural del candidato |
| Historiales compartidos | Cristian confirma que no dispone de inventarios actuales/checksums de esas bases | Pendiente exportación por entorno y aprobación; no se inventa conciliación |

Script preparado: `m4/review/dc-m1-postcierre-02/inventario-solo-lectura.sql`.
Solo debe ejecutarse con conexión y autorización del entorno identificado. No
se inspeccionan credenciales locales ni se consultan bases compartidas en esta
entrega. Si allí existe V010, otra V011 o checksums diferentes, primero preservar
el inventario y decidir una migración posterior sobre ese estado real. No es
posible redactar honestamente una corrección de datos sin ese inventario.

La elección técnica A no acredita todavía el acta de ratificación ni autoriza
acceso estudiantil. Se mantiene DD-02 deshabilitado y PATCH bajo sus políticas
vigentes. No se altera D-06 ni M1/M3/M5/M6.

## Pruebas previstas y evidencia

Generación separada de referencias mediante `M4SchemaReferenceIT`; la aplicación
no invoca el generador. Regresión focalizada `M4SchemaContratoPostgreSqlIT`:
histórico, tabla reemplazada conservando cantidad, tabla adicional, V010, DD-02
por defecto/opt-in, V011 incompleta, FK eliminada, trigger deshabilitado, CHECK
y función alterados conservando nombre, checksum de historial alterado y dos
arranques Spring reales con HTTP health/401/501. Los resets se limitan al
PostgreSQLContainer de la prueba; no se usa Flyway target.

**Primera regresión inspeccionada: 14 casos PostgreSQL y 7 unitarios aprobados,
0 fallos, 0 errores, 0 omitidos.** SHA ejecutado:
`e78a9d96c54911f68d12ad6ba61d430578b281c6`.

- Ejecución: https://github.com/Cris-count/PRISALY-Backen/actions/runs/42508822192
- Artefacto `m4-schema-42508822192-1`, ID 13628502868, 25 578 bytes.
- XML y logs saneados descargados e inspeccionados en
  `docs/evidencias/DC-M1-POSTCIERRE-02-M4/`.
- 14 IT incluyen dos arranques de APP real/Tomcat, histórico y candidato DD-02,
  con health HTTP 200, personal sin credencial 401 y validación estudiantil
  deshabilitada 501. No son mocks ni solo comprobación estática de contexto.
- 7 unitarias: contrato 2, recursos/migración V010 1 y coordinación D-06 4.
  No se suman las ejecuciones locales repetidas a estos resultados.
- Referencias canónicas generadas en PostgreSQL 16.15 mediante run 42508629781,
  SHA `db423d7f644cbee01d180874f7670431815ae9ff`. Ese paso preparatorio no se
  suma a los 21 casos finales. SQL fuente V001–V009/V011/V012 intacto frente a cf57327.

La primera generación, run 42508420938, registró un error de SQL por concatenación
ambigua de campos internos PostgreSQL char. Aunque el job figuró verde por la
tubería tee, Maven y XML registraron fallo; **no se cuenta como aprobación**.
Se corrigieron los casts y se añadió pipefail y verificación de errores/omisiones
en XML y presencia de la suite obligatoria. Su evidencia se conserva separada en
primer-intento. La campaña final tiene BUILD SUCCESS y XML sin errores.

Docker local continúa sin motor. Las pruebas PostgreSQL finales se ejecutaron en
runner GitHub efímero. No se modificó backend-ci.yml ni se repitieron las 114 IT
históricas. El CI estándar también pasó sobre e78a9d9 (run 42508822216), pero sus
pruebas no se mezclan con el conteo focalizado anterior.

Cristian confirma nombres y alcance de las candidatas: dos tablas nuevas, V012
sin tablas adicionales, tres funciones/cuatro triggers, índice parcial,
restricciones y fechaUltimaActividad. Confirma asimismo que no son una ampliación
productiva aprobada. Esta entrega resuelve la compatibilidad técnica demostrada
en base efímera; **no cierra la conciliación/despliegue de entornos compartidos**.

## Contrato Cristian recibido y aceptación complementaria

Se contrastó el documento DC-M1-POSTCIERRE-02 de Cristian suministrado directamente
por el usuario, SHA-256 del adjunto:
`3edd94f4f4a95975ebacd2062305300829e97ae3de0b3692213735dcdc40060c`.
Confirma los mismos nombres, scripts, condición de candidatas y falta de
inventarios compartidos ya documentados. No aporta una ratificación productiva
ni exige cambiar la guarda implementada.

Se añadió una prueba de aceptación SQL bajo el contrato DD-02 reconocido:
activaciones y sesiones con Usuario A y Estudiante de Usuario B son rechazadas
con SQLSTATE 23514 y DD02-VINCULO-INCOHERENTE; no queda ninguna fila parcial.
La misma inserción con el propietario coherente es aceptada para ambas tablas,
y la guarda continúa aceptando el esquema. Son fixtures efímeros, no cuentas
productivas ni una prueba de login estudiantil habilitado. No se duplicó lógica M1.

**Regresión actual inspeccionada: 15 PostgreSQL + 7 unitarias, cero fallos,
errores u omisiones**, sobre `ada08fc7c343cdab3f627036d70472b41a5f09f1`:
https://github.com/Cris-count/PRISALY-Backen/actions/runs/42509475636

Artefacto `m4-schema-42509475636-1`, ID 13628387843, descargado y verificado.
XML, log y manifiesto persistidos en
`docs/evidencias/DC-M1-POSTCIERRE-02-M4/contrato-cristian/`.
Conserva los dos arranques Spring y las comprobaciones HTTP 200/401/501 de la
suite. La comparación con 21c08e1 solo añade la prueba; guarda, contratos,
migraciones y código M1 permanecen idénticos. No sumar este conteo al anterior.

RF/CU canónicos pendientes de fuente V27 verificable. No se declara cierre
integral DD-02 mientras falten aprobación, historiales compartidos, identidad
real y pruebas institucionales del SHA finalmente integrado.
