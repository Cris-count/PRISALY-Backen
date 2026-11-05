# M3-INC-13B — Infraestructura de publicación persistente

Estado: implementación parcial autorizada. Validación manual reportada por el
Integrante 2 el 4 de noviembre de 2026: 132 unitarias y 71 integraciones,
sin fallos ni errores, BUILD SUCCESS (detalle al final). La ratificación académica
de rúbricas sigue pendiente. No constituye publicación funcional completa.

## Resolución y arquitectura

Dirección Técnica autoriza puerto local, adaptador específico, orquestación M3,
SQL exclusivo de estado y propagación REQUIRED. Prohíbe alterar completitud,
equivalencias de rúbricas, DD-03, migraciones y contratos externos.

`CasoPublicationPort.publish(String userId, UUID caseId, String expectedFingerprint)`
devuelve el `CasoUpdateResult` existente. `CasoPersistentPublicationService` ofrece
la orquestación separada sin cambiar el constructor ni el publish transitorio de
CasoApplicationService. Se construye inyectando el puerto, conforme a los servicios
actuales; no incorpora endpoint ni configuración de app.

CasoPublicationAdapter requiere CaseAuthorizationPort y JpaTransactionManager del
mismo EntityManagerFactory. No implementa permisos M1 ni los presume disponibles:
su registro Spring requiere un bean real del contrato de autorización.

## Transacción y prioridad de errores

TransactionTemplate aplica REQUIRED realmente, incluso con invocación directa sin
proxy. Configura READ COMMITTED al abrir transacción; al unirse comprueba el
aislamiento JDBC efectivo y luego PostgreSQL mediante el guard existente. No cambia
el aislamiento de una transacción externa incompatible: la rechaza. Transacciones
read-only no son válidas. No utiliza el lector REQUIRES_NEW en la publicación.

Orden: parámetros y formato/versionado; contexto transaccional compatible; bloqueo
de Caso y existencia; autorización; guards; huella; ARCHIVADO; bloqueo F3-03;
ALREADY_PUBLISHED; validaciones vigentes de preguntas y rúbrica. En particular,
FORBIDDEN precede STALE_CASE; STALE_CASE precede estado y contenido; ARCHIVADO
precede bloqueo; bloqueo precede ALREADY_PUBLISHED. La inexistencia precede
autorización conforme al protocolo autorizado. Un UUID es un UUID Java tipado;
no se genera ni sustituye ningún identificador recibido.

Se lee el agregado completo por CasoTransactionalSnapshotReader en la conexión
transaccional Hibernate. Se usa CasoPersistenceMapper y CasoPublication sin
duplicar reglas académicas. No se invoca validateInsert para publicar filas existentes.

```sql
UPDATE public."Caso" SET "estado" = ?
WHERE "idCaso" = ? AND "estado" = ? AND "fechaBloqueoAcademico" IS NULL
```

Parámetros: Publicado, UUID, Borrador. Exactamente una fila. Se relee en la misma
conexión y se compara el dominio resultante con la transición esperada, verificando
PUBLICADO y huella m3-v2 distinta. No se actualizan fechas, actividad ni hijos.
El resultado de una transacción externa es provisional hasta su commit. Los errores
se propagan; el rechazo participante marca rollback-only. No hay commit independiente.

## Reutilización y diff de código

Archivos nuevos:

- `src/main/java/com/prisaly/m3/application/port/CasoPublicationPort.java`.
- `src/main/java/com/prisaly/m3/application/service/CasoPersistentPublicationService.java`.
- `src/main/java/com/prisaly/m3/infrastructure/persistence/jpa/CasoPublicationAdapter.java`.
- `src/test/java/com/prisaly/m3/infrastructure/persistence/jpa/CasoPublicationContractTest.java`.
- `docs/M3-INC-13B.md`.

Archivos modificados:

- CasoCommandUpdateAdapter: exclusivamente `private static` a `static` en
  assertOfficialContentGuards. Justificación previa: compartir exactamente la
  comprobación de seguridad y contexto existente sin duplicarla. Acceso interno
  de paquete, sin cambio de SQL, excepciones, contrato o protocolo del actualizador.
- CasoPersistenceAdapterIT: siete pruebas nuevas y helpers, reutilizando el arnés
  PostgreSQL 16.15 y V001–V009 existentes. No se eliminan pruebas anteriores.

```diff
- private static void assertOfficialContentGuards(Connection connection) throws SQLException {
+ static void assertOfficialContentGuards(Connection connection) throws SQLException {
```

Los archivos nuevos contienen el resto del código añadido. No se modifica dominio,
mapper, lector, entidades, huella, puertos anteriores, POM ni otros módulos.

## F3-03, concurrencia y límites

El guard compartido verifica los seis triggers de contenido activos, modo origin,
READ COMMITTED y escritura habilitada. La publicación comprueba además los tres
triggers de Sesion de V007, V008 y V009. Comprueba presencia/activación, no certifica
el cuerpo de las funciones ni sustituye permisos operativos o migraciones oficiales.
V007 coordina hijos y raíz; V008 cubre reasignación de sesiones; V009 exige estado
Publicado y activo. El trigger de Caso no impide por sí mismo cambiar solo estado
cuando existe bloqueo: el adaptador rechaza expresamente esa situación.

El FOR UPDATE serializa publicaciones y escritores coordinados. READ COMMITTED
permite ver los cambios confirmados tras esperar el lock. No se afirma que el lock
de raíz bloquee físicamente todas las filas hijas. La garantía depende de los triggers
oficiales y de no eludirlos. Una actualización directa de hijos puede producir un
deadlock con otros órdenes de bloqueo: PostgreSQL aborta una transacción; no se
reintenta silenciosamente ni se declara ausencia de deadlocks.

REQUIRED no consume una conexión adicional al participar en la transacción externa.
El caller conserva locks hasta terminar. En producción no se inventan timeouts:
debe definirse operativamente un límite de espera. La prueba concurrente fija
lock_timeout 10s, statement_timeout 12s y límites de coordinación y Future.

## Matriz de rúbricas

R = Rubrica, RP = RubricaPregunta, CR = CriterioRubrica. Las columnas SQL proceden
de V001; CHECK de V002 e integridad transversal de V003. La obligatoriedad SQL no
equivale a completitud académica. Todos los campos SQL indicados se conservan.

| Modelo/atributo | Java | SQL | Nulidad/restricción oficial | Mapper / dominio | Clase / DD-03 / decisión |
|---|---|---|---|---|---|
| R.id | String UUID | Rubrica.idRubrica uuid | PK NOT NULL | UUID a String; UUID estructural válido | Estructural; no necesita DD-03 |
| R.idCaso | String UUID | Rubrica.idCaso uuid | NOT NULL, UNIQUE, FK Caso RESTRICT | Conservado; pertenencia validada | Estructural; no necesita DD-03 |
| R.nombre | String | Rubrica.nombre varchar(150) | NOT NULL | Conservado; máximo 150 puntos de código | Estructural; no inventar nombre |
| R.descripcion | String | Rubrica.descripcion text | Nullable | Conservada, incluido null | Estructural; sin equivalencia con CR |
| R.puntajeTotal | BigDecimal | Rubrica.puntajeTotal numeric(8,2) | Nullable, positivo si existe | Conservado exacto; dominio exige positivo representable | Estructural; fórmula y significado académico pendientes DD-03 |
| R.definicionEvaluacion | String JSON opaco | Rubrica.definicionEvaluacion jsonb | Nullable | Texto JSONB preservado semánticamente, sin extraer criterios | Estructural; contenido académico pendiente DD-03 |
| R.fechaCreacion | Instant | Rubrica.fechaCreacion timestamptz | NOT NULL DEFAULT now() | Conservada; input estructural puede omitir fecha antes de INSERT | Estructural; no generar fecha de publicación |
| R.criterios | List<CR> | Sin columna/equivalencia aprobada | No aplica SQL | Lectura List.of(); lista no null; isComplete exige no vacía | Académica; conciliación pendiente DD-03 |
| R.preguntas | Set<RP> | Filas RubricaPregunta | Sin orden SQL | Set inmutable; UUID de asociación/pregunta únicos | Estructural; cobertura/completitud pendientes DD-03 |
| RP.idRubricaPregunta | String UUID | RubricaPregunta.idRubricaPregunta uuid | PK NOT NULL | Conservado, UUID validado | Estructural; no equivale automáticamente a CR.id |
| RP.idRubrica | String UUID | RubricaPregunta.idRubrica uuid | NOT NULL, FK Rubrica RESTRICT | Conservado; coincide con rúbrica contenedora | Estructural; no necesita DD-03 |
| RP.idPregunta | String UUID | RubricaPregunta.idPregunta uuid | NOT NULL, UNIQUE; UNIQUE con idRubrica; FK Pregunta RESTRICT | Conservado; pertenencia al caso | Estructural; cobertura académica pendiente DD-03 |
| RP.valorPregunta | BigDecimal | RubricaPregunta.valorPregunta numeric(8,2) | NOT NULL, > 0 | Exacto, positivo representable, sin redondear | Estructural; no convertir a CR.puntaje; DD-03 pendiente |
| RP.criterio | String | RubricaPregunta.criterio text | Nullable | Conservado, incluido null | Estructural; equivalencia académica pendiente |
| CR.id | String | Sin equivalencia aprobada | No aplica SQL | Dominio exige no vacío; no reconstruido | Académica; correspondencia pendiente |
| CR.descripcion | String | Sin equivalencia aprobada | No aplica SQL | Obligatoria no vacía; RP.criterio puede ser null | Académica; correspondencia pendiente |
| CR.puntaje | int | Sin equivalencia aprobada | No aplica SQL | > 0; no convertir NUMERIC a entero | Académica; DD-03 pendiente |

## Pruebas añadidas y pendientes

Seis pruebas unitarias de contrato: parámetros/formato/versionado, inexistencia y
autorización, prioridad de huella, estados y F3-03, validaciones académicas intactas
con rechazo INCOMPLETE_RUBRIC, y delegación de aplicación sin publicación transitoria.
Las pruebas de helpers no reemplazan evidencia JDBC: se complementan con integración.

Siete pruebas PostgreSQL en CasoPersistenceAdapterIT:

1. Rúbrica estructural rechazada sin cambios de las seis tablas/huella.
2. Autorización denegada antes de huella obsoleta, sin cambios.
3. Inexistencia, huella obsoleta y los tres estados físicos admitidos por el esquema.
4. Caso publicado fixture programado legítimamente por Sesion: bloqueo oficial,
   prioridad F3-03 y conservación de disponibilidad RF-09.
5. REQUIRED ve la historia pendiente del caller, usa el mismo PID y aislamiento;
   rechazo marca rollback-only y revierte esa escritura externa.
6. Transacción externa REPEATABLE_READ incompatible rechazada antes de autorización.
7. Publicación espera un lock real de Caso observado con pg_blocking_pids; el primer
   escritor confirma un cambio de Pregunta y el lector rechaza la huella obsoleta.

Los estados PUBLICADO/ARCHIVADO son fixtures físicas mediante el contrato existente
de inserción, no evidencia de una transición académicamente válida ni un borrador
programado artificial. No se deshabilitan triggers ni se crean criterios artificiales.

Pendientes por ratificación: publicación exitosa, dos publicaciones con un ganador,
rollback tras UPDATE de publicación, readback y nueva huella de éxito, y programación
posterior a una publicación válida. No se omiten mediante skip ni se simulan validaciones.
La prueba de rollback incorporada cubre una escritura externa alcanzable, no afirma
haber ejecutado el UPDATE de publicación.

La imposibilidad actual es demostrable por inspección: validateInsert rechaza criterios
legados; toDomain los reconstruye vacíos; isComplete exige no vacíos. No se pierden
campos SQL, pero no existe una regla aprobada para considerar completa la rúbrica SQL.

Decisión requerida: ratificar una condición de completitud para publicación sobre
la representación persistida y su conciliación con CriterioRubrica. No basta aprobar
una cantidad de asociaciones por inferencia del esquema. No se modifica ninguna regla.

## Evidencia y validación manual

No se ejecutaron Maven, Docker, pruebas ni Git. No se afirma compilación ni aprobación.
Desde PRISALY-Backen:

```powershell
mvn -pl m3 -am test
mvn -pl m3 -am -Pdocker-it "-Dit.test=CasoPersistenceAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
mvn -pl m3 -am -Pdocker-it verify
```

La última ejecución incluye regresión INC-10D, 11, 12 y 13A. Registrar salidas,
conteos, entorno y errores reales. Preservar evidencia anterior separada. INC-13B
no es completamente funcional y M3 no se declara terminado.

## Continuación autorizada — regresiones técnicas sin ratificación académica

Esta revisión no encontró una definición posterior que autorice cambiar la
completitud. V003 conserva pendiente DD-03 (fórmula y cobertura exacta al publicar)
y la resolución de este incremento prohíbe inventar equivalencias. No se modifica
Rubrica.isComplete(), CasoValidation, el mapper ni el adaptador productivo.

Se añadieron, pendientes de ejecución manual:

- CasoPublicationContractTest.opaqueJsonAndFractionalAssociationsCannotManufactureLegacyCompleteness:
  un documento opaco que contiene una propiedad llamada criterios no se interpreta
  como criterios legados. Se conservan JSON, null, UUID de asociaciones y 12.50
  como BigDecimal. El modelo reconstruido es estructuralmente insertable, pero
  la publicación sigue rechazando INCOMPLETE_RUBRIC sin cambiar la huella.
  Ese JSON es un dato de prueba, no una estructura académica aprobada.
- CasoPersistenceAdapterIT.incompleteRubricRollsBackJoinedWorkWithoutChangingCommittedAggregates:
  una escritura previa sobre otro borrador dentro de la transacción externa se
  revierte cuando publicar el caso objetivo alcanza INCOMPLETE_RUBRIC. Comprueba
  rollback-only, conservación de ambos agregados y huellas y conteos de seis tablas.
  A diferencia de la regresión anterior de huella obsoleta, esta alcanza la
  validación académica con una precondición vigente. No alcanza ni acredita el
  UPDATE de publicación; su rollback posterior sigue pendiente de ratificación.

Corrección documental: el ejemplo de DC-M3-REST-03 ahora utiliza
`POST {{baseUrl}}/v1/casos`, porque baseUrl ya incluye /api. No cambia el endpoint,
las colecciones ni sus resultados históricos.

### Cobertura conservada y estado de entrega

| Área | Evidencia de código conservada | Estado de esta intervención |
| --- | --- | --- |
| Creación y lectura | CasoApplicationServiceTest y CasoPersistenceAdapterIT | Sin cambios productivos; regresión manual pendiente |
| Rúbrica y asociaciones | RubricaStructureTest, RubricaJpaReadIT y nueva regresión de contrato | Metadatos persistidos separados de criterios; no se presume completitud |
| Omisiones, null y eliminación explícita | CasoCommandUpdateIT | Se reutiliza cobertura existente; no se amplían comandos |
| Concurrencia, huella y F3-03 | CasoCommandConcurrencyIT y CasoCommandAddContentIT | Se conservan garantías y triggers; no se duplica cobertura |
| Rollback por validación académica | Nueva prueba de transacción REQUIRED | Preparada, no ejecutada |
| Publicación exitosa y rollback tras su UPDATE | No existe caso positivo académico ratificado | Bloqueado por definición de completitud |
| Copia persistida y Archivado | Salvaguardas existentes | Políticas pendientes; no se implementan |

Los resultados anteriores pertenecen a sus respectivas corridas. No se declaran
ejecutadas estas dos pruebas ni se incrementan los conteos históricos.

### Decisión institucional requerida

Ratificar qué condiciones sobre Rubrica y RubricaPregunta permiten publicar:
alcance de preguntas incluidas, cobertura exigida, tratamiento de actividad y
visibilidad, campos opcionales académicamente obligatorios y cualquier relación
exigible con puntajeTotal. Definir además el tratamiento del modelo legado.
La aprobación de continuar el desarrollo no establece por sí sola estas reglas.
No se convierte valorPregunta a entero ni se extrae una fórmula del JSON.
La autorización institucional de publicación y su ensamblaje real siguen siendo
prerrequisitos independientes; CREAR_CASO no los sustituye.

Validación manual desde la raíz del backend, sin haberla ejecutado aquí:

```powershell
.\mvnw.cmd -B -pl m3 -am "-Dtest=CasoPublicationContractTest,CasoApplicationServiceTest,CasoMetadataTest,ContenidoAcademicoMetadataTest,RubricaStructureTest,CasoUpdateContractTest,JsonbCanonicalizerTest,CrearCasoRequestTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoPersistenceAdapterIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT,CasoCommandAddContentIT,CasoJpaAggregateReadIT,CasoJpaAggregateTransactionIT,RubricaJpaReadIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

## Resultado real reportado por Integrante 2 — 4 de noviembre de 2026

Fuente: confirmación expresa del Integrante 2, quien ejecutó manualmente Maven.
Este registro documenta su ejecución; el asistente no volvió a ejecutar pruebas
ni inspeccionó en esta intervención reportes XML o logs completos de esa corrida.
Las menciones anteriores a ejecución pendiente corresponden a la preparación
histórica y quedan actualizadas por esta confirmación en el alcance indicado.

| Tipo | Ejecutadas | Fallos | Errores | Resultado reportado |
| --- | --- | --- | --- | --- |
| Unitarias | 132 | 0 | 0 | Satisfactorio |
| Integración PostgreSQL | 71 | 0 | 0 | Satisfactorio |

Resultado final informado: **BUILD SUCCESS**. Entorno de integración:
Testcontainers y PostgreSQL 16.15. Desglose confirmado:

- CasoPublicationContractTest: 9 aprobadas, incluidas dentro de las 132 unitarias.
- CasoPersistenceAdapterIT: 25 aprobadas, incluidas dentro de las 71 integraciones.

Los conteos por clase no se suman otra vez a los totales. Tampoco se suman a
corridas históricas ni a Bruno/Newman. El número de pruebas omitidas, los comandos
exactos ejecutados, la hora y los identificadores de reportes no fueron aportados;
no se infieren a partir de los comandos propuestos anteriormente.

### Avisos de PostgreSQL y Hikari

Se registra la referencia del Integrante 2 a avisos observados de PostgreSQL y
Hikari. No se suministró su texto literal, frecuencia ni contexto completo.
No se les asigna una causa ni se clasifican automáticamente como defectos,
errores funcionales o incidentes resueltos. Para clasificarlos será necesario
conservar los mensajes originales depurados y correlacionarlos con la corrida.
El resultado comunicado de esta ejecución fue cero fallos y cero errores.

### Alcance acreditado y pendientes

La confirmación de las dos clases actualiza a aprobadas las regresiones de
reconstrucción sin criterios artificiales y rollback por rechazo académico,
según la ejecución reportada. No demuestra una publicación persistente exitosa:
la prueba de rollback alcanza INCOMPLETE_RUBRIC, no el UPDATE de publicación.

Continúan pendientes la definición DD-03 de completitud/cobertura sobre la
rúbrica persistida, el caso positivo de publicación, el rollback posterior a su
UPDATE, las carreras de publicación exitosa y su autorización institucional real.
Copia persistida y transiciones de Archivado conservan sus decisiones pendientes.
M3 no se declara terminado por el BUILD SUCCESS.

No se verificó SHA Git, identificación del JAR ni un manifiesto del código de
esta corrida. No se atribuyen estos resultados a un commit o artefacto histórico.
Para trazabilidad completa quedan por adjuntar los reportes Surefire/Failsafe,
logs depurados, comandos y la identificación verificable de la versión ejecutada.
No se reetiquetan los resultados anteriores de Bruno ni Newman.
