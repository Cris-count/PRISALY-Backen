# M3-INC-11 — Alta explícita de preguntas en escenarios existentes

## Autorización e inspección

Dirección Técnica/Integrante 2 autorizó condicionalmente AgregarPreguntaCommand,
solo sobre casos BORRADOR sin bloqueo académico y escenarios existentes del caso.
Rama confirmada por lectura de .git/HEAD: feature/m3-gestion-casos. 34f248a,
94 unitarias y 38 IT PostgreSQL aprobadas son referencias reportadas, no una
ejecución de este incremento. No se ejecutaron Git, Maven, Docker ni pruebas.

Se revisaron las definiciones y restricciones oficiales V001–V009 pertinentes,
Pregunta/Caso/Escenario, asociaciones de rúbrica, comandos, adaptador, lector
transaccional y pruebas de altas/actualización. El esquema permite empezar sin
opciones; el constructor Pregunta también admite esa representación incompleta.
No se usa el builder, que aplica validación de selección única al agregar preguntas.

## Matriz de inspección de public."Pregunta"

| Columna física | Tipo SQL | Nulidad | DEFAULT oficial | Restricciones | Java | Valor para nueva pregunta | Fuente |
| --- | --- | --- | --- | --- | --- | --- | --- |
| idPregunta | uuid | NOT NULL | Ninguno | PK pk_pregunta | UUID comando / String dominio | UUID explícito del llamador, no reutilizado | V001:73,82; contrato INC-11 |
| idEscenario | uuid | NOT NULL | Ninguno | FK fk_pregunta_idescenario, ON DELETE RESTRICT | UUID comando / pertenencia anidada dominio | Escenario existente del caso bloqueado | V001:74,248; INC-11 |
| enunciado | text | NOT NULL | Ninguno | Sin CHECK nonblank SQL | String | Explícito, no null ni blanco por dominio vigente | V001:75; Pregunta constructor; requiredText de comandos |
| textoAyuda | text | Nullable | Ninguno | Sin CHECK adicional | String | Texto explícito o null, sin trim | V001:76 |
| estadoEdicion | varchar(20) | NOT NULL | 'Borrador' | ck_pregunta_estado: Borrador/Lista | String | Obligatorio explícito, uno de esos dos valores; no se elige por el llamador implícitamente | V001:77; V002:11; Pregunta constructor |
| visible | boolean | NOT NULL | true | Sin CHECK adicional | boolean | Obligatorio explícito; no se decide visibilidad por política nueva | V001:78; contrato INC-11 |
| orden | int | NOT NULL | Ninguno | ck_pregunta_orden >=1; uq_pregunta_idx1(idEscenario,orden) | int comando / Integer dominio | Positivo explícito, sin renumerar hermanos | V001:79,83; V002:18 |
| fechaCreacion | timestamptz | NOT NULL | now() | Sin CHECK temporal adicional | Instant dominio | Omitida del INSERT: la asigna el default oficial | V001:80 |
| fechaActualizacion | timestamptz | Nullable | Ninguno | Sin CHECK adicional | Instant dominio | Omitida del INSERT: NULL SQL, sin fecha Java inventada | V001:81 |

El trigger tr_pregunta_bloqueo_f303 de V007:169 protege INSERT/UPDATE/DELETE,
resuelve Caso por el escenario, bloquea la raíz y rechaza contenido programado.
V003 protege coherencia de asociaciones/respuestas; V004–V006 y V008–V009 no
añaden campos ni defaults de Pregunta. V009 impide programar un Borrador.

No se identificó columna obligatoria que obligue a elegir una política pendiente:
se reciben estadoEdicion y visible explícitamente. Sus defaults SQL se documentan
pero NO se utilizan para esconder decisiones del comando. Fecha de creación usa
un default temporal oficial ya existente. No se impone una política nueva de
completitud para Lista: el valor inicial es explícito y la publicación mantiene
las reglas vigentes, incluso cuando una pregunta no tiene opciones.

## Contrato implementado

Record anidado en CasoUpdateCommand:

```java
record AgregarPreguntaCommand(UUID idPregunta, UUID idEscenario,
        String enunciado, String textoAyuda, String estadoEdicion,
        boolean visible, int orden) implements CasoUpdateCommand {}
```

Identificadores no nulos, sin generación ni conversión desde strings. Java UUID
representa la identidad que se envía mediante JDBC. Estado obligatorio y validado
contra el catálogo, contenido obligatorio conforme al dominio y orden positivo.
textoAyuda admite null. No recibe opciones, rúbricas, fechas ni propiedades del
caso. No establece visibilidad o estado implícitamente ni modifica filas existentes.

## Persistencia y errores

No cambian firmas de CasoUpdatePort ni CasoRepositoryPort; update(Caso) sigue
sin habilitarse. Se conservan REQUIRES_NEW/READ_COMMITTED, el mismo EMF/gestor y
conexión transaccional, SELECT Caso FOR UPDATE, las seis lecturas iniciales,
comprobación de triggers oficiales y huella canónica m3-v2.

Se conserva la prioridad: CASE_NOT_FOUND, precondición STALE_CASE, rechazo de
Archivado y F3-03; las altas de pregunta sobre Publicado sin bloqueo producen
QUESTION_ADD_REQUIRES_DRAFT. En lotes mixtos se conserva también la validación
existente de alta de escenarios. No se modifica la prioridad de errores anterior.

Pertenencia de escenario se valida en el snapshot inicial con ELEMENT_NOT_IN_CASE,
tanto si no existe como si pertenece a otro caso. QUESTION_ID_IN_USE rechaza una
identidad presente en el agregado o encontrada en consulta global antes del INSERT.
QUESTION_ORDER_IN_USE rechaza el orden ya ocupado dentro de ese escenario.

```sql
SELECT 1 FROM public."Pregunta" WHERE "idPregunta" = ?;
INSERT INTO public."Pregunta"
  ("idPregunta", "idEscenario", "enunciado", "textoAyuda", "estadoEdicion", "visible", "orden")
VALUES (?, ?, ?, ?, ?, ?, ?);
```

Valores parametrizados, sin UPSERT. FK, PK y UNIQUE siguen siendo definitivos
frente a colisiones concurrentes. No se confunde la consulta previa con garantía
global de unicidad: un conflicto no visible todavía puede producir SQLSTATE 23505.
El orden se controla por escenario, no por todo el caso. No hay efectos laterales
sobre Caso, escenarios, preguntas anteriores, opciones o rúbrica.

Todos los padres deben existir antes del lote. No se admiten escenario nuevo más
pregunta dependiente, ni alta de pregunta más edición dependiente en la misma
composición: la validación previa se conserva. Sí pueden ejecutarse altas
independientes sobre escenarios existentes. Una colisión entre comandos revierte
la composición completa, incluido cualquier UPDATE anterior. Se relee el agregado
y se calcula la nueva huella antes del commit; el resultado se devuelve después.

## Pruebas preparadas, sin ejecutar

CasoUpdateContractTest añade tres métodos:

- questionAddRequiresExplicitIdentityContentStateVisibilityAndPositiveOrder:
  contrato válido, texto nullable, UUID null, enunciado null/blanco, estado null/no
  permitido, orden cero/negativo y conservación de valores explícitos.
- questionAddValidatesExistingParentIdentityAndOrderWithoutMutatingSnapshot:
  pertenencia, duplicados y ausencia de modificación del snapshot/huella.
- questionAddRequiresDraftAndPreservesStaleArchivedAndAcademicLockPriority:
  Borrador permitido, Publicado/Archivado rechazados y prioridad de huella/F3-03.

CasoCommandAddContentIT añade siete métodos sobre PostgreSQL 16.15 y migraciones
oficiales existentes:

| Método | Cobertura |
| --- | --- |
| addsQuestionWithExplicitAttributesAndOfficialDateWithoutChangingPriorContent | Inserción, UUID/FK, estado y visibilidad explícitos, ayuda/null, órdenes con huecos, DEFAULT temporal, opciones vacías, relectura, m3-v2, conservación del agregado y otro caso, regla de publicación sin relajar. |
| questionAddRejectsMissingForeignParentsStatesAndStalePreconditionWithoutWriting | Caso ausente, escenario ausente/ajeno, Publicado/Archivado y huella obsoleta. |
| questionDuplicatesAndCompositeConstraintFailureRollBackEveryCommand | UUID del mismo/otro caso, orden ocupado, UUID repetido en lote, UNIQUE intermedio tras UPDATE+INSERT y rollback completo. |
| questionAddCannotDependOnNewParentOrBeEditedWithinSameBatch | Padres/sujetos nuevos no se consideran existentes para otros comandos del lote. |
| officialProgrammingRejectsQuestionAddAndSqlInsertWithoutManualLockChanges | Programación válida activa F3-03; rechazo con huella antigua/actual y trigger directo intacto. |
| officialQuestionFkAndOrderConstraintsRemainActive | INSERT negativos rechazados por FK y CHECK, sin conservar filas inválidas. |
| concurrentQuestionAddsWithSameFingerprintHaveOneWinner | Primera alta retiene raíz; segundo escritor espera, primera confirma y segunda rechaza STALE_CASE. |

Se extiende únicamente la barrera JDBC de pruebas existente para detectar también
INSERT Pregunta, sin cambiar los escenarios de alta existentes. Se identifican
backends reales, se consulta pg_blocking_pids(), se usan latches y límites previos;
sin Thread.sleep, desactivación de triggers ni cambios de estados forzados.
Los casos programados de la prueba son Publicado legítimos: no se fabrica un
Borrador con fechaBloqueoAcademico manual para probar F3-03.

La comparación del agregado excluyendo únicamente la pregunta nueva conserva
metadatos, preguntas, opciones y asociaciones originales. El escenario fixture
inactivo confirma que no se inventa una prohibición de editar por actividad.
Las filas con FK/orden inválido solo se intentan como pruebas negativas: se
comprueba el rechazo y que no cambie la instantánea.

## Archivos y límites

Modificados: CasoUpdateCommand.java, CasoCommandUpdateAdapter.java,
CasoUpdateContractTest.java y CasoCommandAddContentIT.java. Creado este documento.
No se modifican dominio, publicación, rúbricas, POM, migraciones ni otros módulos.

Autorización M1, política de publicación/completitud de rúbrica DD-03, alta de
opciones, copia persistida y demás decisiones siguen pendientes. No se certifica
que el estado Lista signifique publicación o completitud. El comando no habilita
transiciones de estado de preguntas existentes ni nuevas políticas de visibilidad.
La protección concurrente depende de triggers oficiales activos y privilegios
adecuados; no elimina interbloqueos ni añade detección de historial ABA/reintentos.

Validación manual desde PRISALY-Backen:

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandAddContentIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT,CasoPersistenceAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

Registrar fecha, commit realmente ejecutado, XML Surefire/Failsafe, recuentos y
SQLSTATE. No se declara M3-INC-11 completado/aprobado sin validación manual del
Integrante 2. No se avanza a otro incremento.
