# DC-REV-69-M3 — Dictamen contractual y académico postmerge

**Dictamen: FAVORABLE CON OBSERVACIONES.**

Revisión de Mariana del 2026-11-08 (America/Bogota), limitada a los 16 archivos de PR #69. No se reprodujo un fallo funcional con datos válidos ni una regresión de evaluación académica. Sí se reprodujo una pérdida de distinción entre intento huérfano e inexistente bajo corrupción forzada, que requiere seguimiento contractual y no debe confundirse con ausencia real. La aprobación no se deduce de que el PR esté fusionado.

## 1. SHA y aislamiento

Repositorio: Cris-count/PRISALY-Backen. PR: https://github.com/Cris-count/PRISALY-Backen/pull/69.

SHA analizado y ejecutado: **b97ba887629b6229fdd9676877e31049be4c3461**. Se revisó el diff de ese merge contra su primer padre (3f97e7e), no el develop posterior ni el incremento RES-04 de Mariana.

Se utilizó el worktree existente audit-develop-20261105: estaba limpio, detached en a7d37ec, y se seleccionó el SHA exacto con git switch --detach. Permanece limpio tras las pruebas. El worktree activo m3-dc-ejec-03 se conserva en faade398bbff127e505b85953c356cfb3d53fccd; sus tres documentos históricos locales no se alteraron. Este dictamen es el único archivo de entrega creado. La compilación solo genera artefactos ignorados y el log se guarda en TEMP. No se ejecutaron reset, clean, commit, push, PR ni merge.

## 2. Los 16 archivos revisados

| # | Archivo relativo al repositorio | Cambio contrastado |
| --- | --- | --- |
| 1 | m5/src/main/java/com/prisaly/m5/domain/respuesta/ReferenciaPregunta.java | Canonicalización de UUID completos |
| 2 | m5/src/main/java/com/prisaly/m5/domain/respuesta/ValidacionRespuesta.java | Patrón UUID y Locale.ROOT, sin trim |
| 3 | m5/src/main/java/com/prisaly/m5/domain/respuesta/ValorRespuesta.java | Canonicalización de referencia de opción |
| 4 | m5/src/main/java/com/prisaly/m5/infrastructure/persistencia/MapeoIntento.java | Filas de respuesta corruptas se envuelven como persistencia |
| 5 | m5/src/main/java/com/prisaly/m5/infrastructure/persistencia/RepositorioIntentosJpa.java | LEFT JOIN a JOIN en vinculo |
| 6 | m5/src/main/java/com/prisaly/m5/presentation/ManejadorErroresM5.java | Manejo de DataAccessException y TransactionException |
| 7 | m5/src/main/java/com/prisaly/m5/presentation/TraductorErroresM5.java | Integridad 409; fallos técnicos 500 genérico |
| 8 | m5/src/test/java/com/prisaly/m5/domain/respuesta/ReferenciasCanonicasTest.java | Igualdad UUID y duplicados |
| 9 | m5/src/test/java/com/prisaly/m5/domain/respuesta/ValorRespuestaContratoTest.java | Valores opacos y revisiones |
| 10 | m5/src/test/java/com/prisaly/m5/infrastructure/persistencia/FallosTecnicosM5IT.java | Lock timeout real y reintento |
| 11 | m5/src/test/java/com/prisaly/m5/infrastructure/persistencia/MapeoIntentoFilasCorruptasTest.java | Duplicados y revisión no positiva |
| 12 | m5/src/test/java/com/prisaly/m5/infrastructure/persistencia/RevisionMacro02PersistenciaIT.java | Rollback, integridad y distinción de vínculos |
| 13 | m5/src/test/java/com/prisaly/m5/infrastructure/persistencia/RobustezPersistenciaM5IT.java | Revisión UUID y vínculo huérfano |
| 14 | m5/src/test/java/com/prisaly/m5/presentation/ErroresTecnicosM5Test.java | Traducción y despacho MVC |
| 15 | m5/src/test/java/com/prisaly/m5/presentation/RespuestaHttpErroresTecnicosTest.java | Cuerpo HTTP completo sin datos sensibles |
| 16 | m6/src/test/java/com/prisaly/m6/application/integration/ModelosLimitesEInmutabilidadTest.java | NUMERIC, copias y referencias sin opciones |

Diff exacto: 652 inserciones y 8 eliminaciones. Siete archivos productivos M5 y nueve archivos de pruebas; ningún archivo productivo M6, M3, APP o migración cambia en este PR.

## 3. Contratos contrastados

### A. Identificadores M3 de preguntas y opciones

La expresión de ValidacionRespuesta.canonica solo reconoce el UUID completo 8-4-4-4-12 hexadecimal. Lo convierte a minúsculas con Locale.ROOT: el UUID representado no cambia. ReferenciaPregunta y ValorRespuesta usan esa forma para igualdad, búsqueda de respuesta, duplicados y revisión. MapeoIntento convierte las referencias persistibles a UUID; PostgreSQL almacena UUID, no su caja textual.

ReferenciasCanonicasTest y ValorRespuestaContratoTest prueban igualdad de mayúsculas/minúsculas, detección de duplicado y ausencia de revisión artificial al seleccionar la misma opción. Los textos opacos de las pruebas se conservan exactamente, incluidos texto libre, ÑANDÚ, sufijos/prefijos, llaves, espacios y UUID incompletos. No se introduce trim ni conversión general de texto. RobustezPersistenciaM5IT comprueba que una pregunta con UUID en mayúsculas no elude la revisión obsoleta y conserva opción/revisión en PostgreSQL.

No se reproduce incompatibilidad con los UUID de Pregunta/OpcionRespuesta de M3. Un eventual uso de ValorRespuesta como texto opaco que tenga exactamente forma UUID no puede distinguirse solo por esta clase: en el contrato persistido revisado el valor es una referencia de opción; no se demuestra ese uso alternativo ni se lo declara defecto.

### B. Contexto académico M3–M6 y rúbricas

Se contrastaron ContextoAcademicoPersistido de M3, RubricaPregunta y RubricaStructureValidation, MapeoContextoAcademicoM3 de APP, ContextoAcademico/IntentoEvaluacion de M6, PrepararContextoEvaluacionService y PrepararEvaluacionProtegida. Son fuentes contractuales de apoyo sin cambios en esta revisión.

| Correspondencia | Garantía y límite |
| --- | --- |
| Caso → Escenario → Pregunta → Opción | UUID explícitos; el preparador comprueba padres, unicidad y que la opción seleccionada pertenezca a la pregunta |
| Intento → Participación → Convocatoria → Caso | M5 conserva participación y obtiene el caso por el puerto M4; no inventa caso desde el payload |
| Caso → Rúbrica → RubricaPregunta → Pregunta | El preparador comprueba asociaciones y preguntas del contexto; PrepararEvaluacionProtegida comprueba rúbrica solicitada y pertenencia al caso |
| M3 → APP → M6 | Mapper estructural copia referencias y atributos por nombre; conserva el orden correcto de fechas de creación/actualización/bloqueo, sin inferir datos |
| RubricaPregunta.valorPregunta | BigDecimal positivo exactamente representable en NUMERIC(8,2); máximo 999999.99, sin redondeo ni double; criterio nullable |

M3 y M6 coinciden en la validación decimal: 0.01, 999999.99 y 12.500 son representables; 1.001, cero, negativos o desbordamiento no lo son. Se conserva el objeto/escala suministrado; el valor no se convierte a CriterioRubrica.puntaje ni a entero. Las asociaciones no constituyen una fórmula, ponderación ni cobertura aprobada. JSON de definición permanece opaco y las colecciones son defensivas.

Las pruebas de contrato y preparación rechazan caso/pregunta/opción cruzados, asociación de rúbrica equivocada y duplicidades; una respuesta a pregunta sin opciones se rechaza. No se rellena una rúbrica ausente ni se obliga una colección académica a ser no vacía por inferencia. Estas verificaciones no calculan notas ni presuponen una fórmula DD-03.

### C. Estados y condiciones académicas

No cambian estados de caso Borrador/Publicado/Archivado, actividad, visibilidad, estados de edición de preguntas ni el bloqueo académico M3/M4. Los snapshots de prueba con Borrador son datos estructurales; no acreditan permiso de evaluación o publicación. La lectura M5 conserva EN_CURSO/FINALIZADO y M6 rechaza EN_CURSO antes de consultar el contexto académico.

FINALIZADO no equivale a autorizado, evaluable, evaluado o publicado. El PR no incorpora una Strategy productiva ni escritor de calificaciones y no modifica los guards de M3. No se ejecutó una campaña general de publicación/bloqueo M3: su preservación en este dictamen se apoya en la ausencia de cambios y en los contratos consumidores relacionados, no en afirmar pruebas ajenas ejecutadas.

### D. Intento–Participación: JOIN y distinciones

RepositorioIntentosJpa.vinculo, línea 82, cambia LEFT JOIN por JOIN con Participacion. Con la FK fk_intento_idparticipacion de V001 activa, toda fila válida de Intento exige una Participación y ambas consultas coinciden. La pérdida aparece al materializar datos corruptos deshabilitando FK/triggers únicamente con SET LOCAL session_replication_role=replica dentro de la transacción del contenedor de prueba.

| Escenario ejecutado | Resultado comprobado |
| --- | --- |
| Intento realmente inexistente | Optional.empty; preparador M6 emite INTENTO_INEXISTENTE |
| Intento ajeno | AccesoEvaluacionDenegadoException; no se entrega el intento aunque el autorizador controlado permita la solicitud |
| Intento huérfano con titularidad real | Optional.empty; M6 emite INTENTO_INEXISTENTE: se pierde la distinción con ausencia real |
| Vínculo ausente después de acreditar titularidad | IllegalStateException de relaciones inconsistentes; M6 emite FALLO_CONSULTA_INTENTO |
| Fallo técnico al recuperar vínculo | Se propaga como fallo; no se convierte en Optional.empty y se solicita rollback |

La consulta de TitularidadIntentosDesdeM4 ya tenía JOIN antes de PR #69. Por ello el enmascaramiento del huérfano en el recorrido protegido real es preexistente; el PR extiende la semántica de ausencia al método vinculo. No se atribuye al PR una nueva evasión de autorización ni una regresión demostrada sobre datos válidos.

### E. Errores SQL, inconsistencias y HTTP

MapeoIntento.reconstruir envuelve ParticipacionDominioException de respuestas persistidas en PersistenciaIntentoException. Dos filas para una pregunta y revisión no positiva se clasifican como corrupción técnica, en lugar de un rechazo de entrada del cliente. No cambia la regla de dominio que rechaza esos datos.

TraductorErroresM5 mantiene la rama DataIntegrityViolationException antes de DataAccessException: integridad y DuplicateKeyException siguen en 409/CONFLICTO_DATOS. PersistenciaIntentoException, otros DataAccessException y TransactionException se traducen a 500/ERROR_INTERNO. ManejadorErroresM5 registra las clases ampliadas y las pruebas MVC comprueban cuerpos genéricos sin SQL, URL JDBC, contraseña ni nombres de excepciones.

En PostgreSQL se ejecutó lock_timeout real (55P03), sin escritura y con reintento posterior exitoso; también fallo a mitad de unidad de trabajo, con rollback de una inserción ya ejecutada. Las violaciones reales de opción de otra pregunta y pregunta de otro caso conservan 409 y no cambian las filas previas. No se declara probado un deadlock real ni todos los posibles estados SQL: no fueron escenarios de esta ejecución.

### F. DD-04, DD-03 y D07

El diff no modifica servicios productivos M6, selección de intentos/calificaciones, Strategy, restricciones de Calificacion ni operaciones de idempotencia D07. Las pruebas nuevas tampoco seleccionan una última nota ni confunden finalización con evaluación.

M6-INC-07, líneas 100–103, y DC-MACRO-02-M5M6 documentan que DD-03/DD-04/D07 siguen pendientes; la preparación existente recibe un idIntento, no implementa un selector del último autorizado y evaluado. Por tanto se confirma que PR #69 no altera ese criterio ni lo reemplaza por máximo UUID, última fecha o simple FINALIZADO. No se certifica la ejecución positiva de una selección DD-04 que estas fuentes todavía no implementan, ni se ratifica una decisión académica por ausencia de cambios.

## 4. Pruebas ejecutadas y resultado real

Ejecución nueva sobre el SHA exacto indicado: **74 pruebas aprobadas, cero fallos, errores u omisiones; BUILD SUCCESS y salida 0**. Java 21, PostgreSQL 16.15 en contenedores aislados, migraciones canónicas sin edición. Terminó el 2026-11-08 a las 17:11:09 -05:00.

| Clase | Pruebas | Relación con la revisión |
| --- | ---: | --- |
| ReferenciasCanonicasTest | 3 | Nueva PR #69 |
| ValorRespuestaContratoTest | 3 | Nueva PR #69 |
| MapeoIntentoFilasCorruptasTest | 2 | Nueva PR #69 |
| ErroresTecnicosM5Test | 3 | Nueva PR #69 |
| RespuestaHttpErroresTecnicosTest | 3 | Nueva PR #69 |
| ModelosLimitesEInmutabilidadTest | 3 | Nueva PR #69 |
| ContratosConsumidoresTest | 6 | Contratos M6 existentes |
| PrepararContextoEvaluacionServiceTest | 10 | Referencias y restricciones existentes |
| ConsultaIntentoEvaluacionDesdeM5Test | 11 | Consumidor del vínculo modificado |
| TitularidadIntentosDesdeM4Test | 8 | Filtro previo y fallos de relación |
| ControlAccesoIntentosTest | 11 | Ausencia frente a ajeno |
| FallosTecnicosM5IT | 1 | Nueva PR #69, PostgreSQL |
| RevisionMacro02PersistenciaIT | 5 | Nueva PR #69, PostgreSQL |
| RobustezPersistenciaM5IT | 2 | Nueva PR #69, PostgreSQL |
| ConsultaIntentoEvaluacionM5M6IT | 3 | Integración consumidora existente, PostgreSQL |
| **Total** | **74** | **63 unitarias/MVC + 11 PostgreSQL** |

Se ejecutaron las nueve clases de prueba incorporadas por el PR y seis clases existentes directamente relacionadas; no se duplicaron pruebas. Las 25 pruebas nuevas del PR están incluidas en las 74, no sumadas otra vez. Los otros XML históricos del target no se cuentan: se contrastó el log de esta invocación con las clases seleccionadas.

```powershell
.\mvnw.cmd -B -o -pl m5,m6 -am -Pdocker-it '-Dtest=ReferenciasCanonicasTest,ValorRespuestaContratoTest,MapeoIntentoFilasCorruptasTest,ErroresTecnicosM5Test,RespuestaHttpErroresTecnicosTest,ModelosLimitesEInmutabilidadTest,ContratosConsumidoresTest,PrepararContextoEvaluacionServiceTest,ConsultaIntentoEvaluacionDesdeM5Test,TitularidadIntentosDesdeM4Test,ControlAccesoIntentosTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=FallosTecnicosM5IT,RevisionMacro02PersistenciaIT,RobustezPersistenciaM5IT,ConsultaIntentoEvaluacionM5M6IT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Log local: C:/Users/57316/AppData/Local/Temp/dc-rev69-m3-tests.log. XML de las clases seleccionadas en m5/target/{surefire-reports,failsafe-reports} y m6/target/surefire-reports del worktree de auditoría. Son pruebas controladas y de persistencia aislada; no certifican permisos institucionales positivos ni la BD consolidada.

## 5. Observación reproducible y propuesta a Ingrid

**R69-01 — La relación huérfana puede confundirse con ausencia.**

Evidencia ejecutada: RobustezPersistenciaM5IT.vinculoDeIntentoHuerfanoEsAusenteYNoNullPointer, RevisionMacro02PersistenciaIT.huerfanoConTitularidadRealSeVeComoAusencia y vinculoAusenteTrasPasarTitularidadEsInconsistenciaNoAusencia. Las tres pruebas aprobaron y caracterizan comportamientos diferentes; no son pruebas fallidas ni una demostración de corrección contractual del enmascaramiento.

Archivo/método del PR: RepositorioIntentosJpa.vinculo, líneas 82–92. Filtro previo relevante: TitularidadIntentosDesdeM4.de/convocatoriaDe (preexistente, fuera de los 16 archivos). Consumidor: ConsultaIntentoEvaluacionDesdeM5.consultar; contrato de M6 ConsultaIntentoEvaluacionPort.findById distingue ausencia y fallo técnico.

Impacto: con filas huérfanas, M6 puede informar INTENTO_INEXISTENTE cuando el intento existe pero perdió una relación obligatoria. No concede acceso ni crea una calificación; dificulta diagnóstico y tratamiento de corrupción. La FK impide ese estado en la operación normal comprobada; no se ha demostrado que exista en la BD institucional.

Propuesta mínima, sin implementar: Ingrid debe conciliar el resultado vacío de vinculo con la presencia real del Intento. Si se requiere distinguir corrupción de ausencia, conservar una lectura que detecte la participación faltante (por ejemplo LEFT JOIN y validación explícita), lanzar PersistenciaIntentoException genérica para relación rota y reservar Optional.empty para intento realmente ausente. La titularidad previa debe preservar esa misma distinción; corregir solo vinculo no cambia el caso enmascarado antes de llegar a él. Reutilizar las pruebas existentes y ajustar la expectativa de caracterización tras la decisión contractual, sin nuevas equivalencias académicas ni migraciones.

## 6. Riesgos no demostrados y límites

- No se probó que existan intentos huérfanos reales, FK deshabilitadas o corrupción de este tipo en la BD institucional. Las pruebas lo fabrican explícitamente en contenedores desechables.
- No se ejecutó composición institucional positiva M3–APP–M6 ni política de evaluación ratificada; se contrastaron sus contratos y se ejecutó el consumidor M5–M6 con autorización controlada. No se presenta como certificación end-to-end académica.
- No se presupone que un texto libre con apariencia UUID sea un caso de uso productivo de ValorRespuesta; es una ambigüedad potencial de tipado, no una pérdida de texto demostrada para el contrato de opción UUID.
- DD-04 positivo y fórmula DD-03 no están implementados/ratificados por este incremento; su ausencia de modificación no equivale a acreditar evaluación o selección productiva.
- No se generalizan las pruebas SQL a todos los motores, errores o transacciones; la evidencia real corresponde a PostgreSQL 16.15 y a los escenarios listados.

## 7. Impacto y fundamento del dictamen

M3 conserva identificadores, estructura de rúbrica, decimal exacto, estados y restricciones. M5 evita que la caja de UUID eluda duplicados/revisión y sanea fallos técnicos sin volverlos éxito. M6 recibe las referencias explícitas y mantiene comprobaciones estructurales y bloqueo de preparación para EN_CURSO, sin introducir evaluación automática.

**FAVORABLE CON OBSERVACIONES** para la compatibilidad de los cambios de PR #69 en el alcance asignado: todas las pruebas relacionadas aprobaron y no se reprodujo una regresión sobre datos válidos. Se requiere seguimiento de R69-01 con Ingrid porque la ausencia aparente bajo corrupción es reproducible y pierde información contractual. No se emite una aprobación institucional de DD-03, DD-04, D07, políticas de acceso o evaluación académica. No se implementó ninguna corrección ni se publicó este informe.
