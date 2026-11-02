# M3-INC-12 — Alta explícita de opciones de respuesta

## Resolución y alcance

Dirección Técnica/Integrante 2 autorizó AgregarOpcionRespuestaCommand sobre preguntas
existentes en el snapshot inicial de un caso Borrador sin bloqueo F3-03. Se permite
composición con eliminación explícita según el orden recibido. No se modifican
publicación, modelos de dominio, rúbricas, lector/mapper, huella, POM ni otros módulos.

Rama confirmada mediante lectura de .git/HEAD: feature/m3-gestion-casos.
327e8b6, merge entrante 934f71c y resultados anteriores de 97 unitarias/45 IT
seleccionadas aprobadas son referencias reportadas, no verificadas mediante Git.
No se ejecutaron Git, Maven, Docker, scripts, compilaciones ni pruebas.

## Inspección y matriz oficial

Se revisaron OpcionRespuesta, Pregunta, comandos, adaptador, eliminación actual,
lector transaccional, cálculo m3-v2, pruebas de alta/eliminación/concurrencia y las
restricciones pertinentes de las migraciones V001–V009. No se sustituyen por un
esquema supuesto ni por declaraciones de dominio.

Tabla public."OpcionRespuesta":

| Columna | SQL / nulidad | Default oficial | Restricciones | Java / nuevo comando |
| --- | --- | --- | --- | --- |
| idOpcion | uuid NOT NULL | Ninguno | pk_opcionrespuesta; uq_opcionrespuesta_idx1(idOpcion,idPregunta) | String dominio; UUID explícito comando |
| idPregunta | uuid NOT NULL | Ninguno | fk_opcionrespuesta_idpregunta; ON DELETE RESTRICT | Pertenencia anidada; UUID explícito comando |
| texto | text NOT NULL | Ninguno | Sin CHECK nonblank SQL; dominio exige no blanco | String literal obligatorio |
| esCorrecta | boolean NOT NULL | false | ux_opcion_max_una_correcta(idPregunta) WHERE esCorrecta=true | boolean explícito; no se usa DEFAULT |
| explicacion | text nullable | Ninguno | Sin restricción adicional | String opcional, sin trim |
| orden | int NOT NULL | Ninguno | ck_opcion_orden>=1; uq_opcionrespuesta_idx2(idPregunta,orden) | Integer dominio; int positivo explícito |

Fuentes: V001:85–95 (columnas/PK/UNIQUE), V001:249 (FK), V002:4 (índice parcial),
V002:19 (CHECK). V001:272 contiene la FK histórica Respuesta(idOpcionSeleccionada,
idPregunta) → OpcionRespuesta(idOpcion,idPregunta), ON DELETE RESTRICT. V007:170
instala tr_opcion_bloqueo_f303 sobre INSERT/UPDATE/DELETE y coordina la raíz.
V003 valida correspondencia de respuestas con el caso. Las demás migraciones
inspeccionadas no agregan atributos obligatorios a OpcionRespuesta.

No hay ponderación, puntaje, actividad, visibilidad ni fechas en esta tabla.
Máximo una correcta no equivale a exactamente una: no se trasladan los requisitos
de publicación a cada paso de construcción de un borrador.

## Contrato implementado

Record anidado en CasoUpdateCommand:

```java
record AgregarOpcionRespuestaCommand(UUID idOpcion, UUID idPregunta, String texto,
        boolean esCorrecta, String explicacion, int orden) implements CasoUpdateCommand {}
```

UUID no nulos; texto obligatorio/no blanco según el dominio; orden positivo;
explicación nullable sin transformación. Corrección suministrada por el llamador.
No hay generación automática ni modificación de opciones anteriores.

## Pertenencia y persistencia

Se conserva el protocolo del adaptador: validar formato m3-v2, iniciar la misma
transacción REQUIRES_NEW/READ_COMMITTED, SELECT Caso FOR UPDATE, leer el agregado,
comprobar triggers, comparar huella y validar pertenencia antes de escribir.
La prioridad existente de CASE_NOT_FOUND, STALE_CASE, Archivado y F3-03 no cambia.
Alta en Publicado sin bloqueo produce OPTION_ADD_REQUIRES_DRAFT. En lotes mixtos
permanecen también las comprobaciones de altas de escenario/pregunta anteriores.

Pregunta se busca en snapshot.preguntas(), recuperado mediante escenario y caso
en el lector existente; una pregunta inexistente o ajena produce ELEMENT_NOT_IN_CASE.
No se permite usar una pregunta creada dentro del lote. Si el UUID de opción ya
aparece en el snapshot se rechaza con OPTION_ID_IN_USE, incluso si un comando
pretende eliminarlo antes. No hay reutilización del UUID como nueva identidad.

Antes de cada INSERT se consulta globalmente la identidad sobre la misma conexión:

```sql
SELECT 1 FROM public."OpcionRespuesta" WHERE "idOpcion" = ?;
INSERT INTO public."OpcionRespuesta"
  ("idOpcion", "idPregunta", "texto", "esCorrecta", "explicacion", "orden")
VALUES (?, ?, ?, ?, ?, ?);
```

La consulta global rechaza identidades de otro caso y repeticiones dentro del lote.
No garantiza por sí sola ausencia de colisiones sin confirmar: la PK sigue siendo
definitiva. Valores enlazados por parámetros; esCorrecta se escribe explícitamente.
No hay UPSERT, eliminación implícita ni cambios de otros componentes.

Orden y corrección se comprueban al ejecutar mediante los índices UNIQUE oficiales,
sin anticipar un rechazo basado en filas que una eliminación anterior podría quitar.
Los errores SQL 23505 se propagan conforme al tratamiento actual; no se inventa
un mapeo genérico que confunda UUID, orden y segunda correcta.

## Composición autorizada con eliminación

- Eliminar correcta y agregar nueva correcta: permitido si la secuencia satisface
  FK, F3-03 y unicidad; puede reutilizar el orden liberado, nunca el UUID anterior.
- Agregar segunda correcta y eliminar después: el primer INSERT falla por el
  índice parcial; la eliminación no se ejecuta y se revierte cualquier paso previo.
- Una opción incorrecta no reemplaza ni corrige otra opción automáticamente.
- Agregar opción y eliminar esa opción nueva en el mismo lote: rechazado antes
  de escribir, porque EliminarOpcionCommand conserva pertenencia al snapshot inicial.
- Referencias históricas y bloqueo siguen aplicándose a EliminarOpcionCommand;
  no se modifica su SQL ni su comprobación de pertenencia.

Se respeta el orden explícito de ejecución. Una secuencia que deja temporalmente
cero correctas tras eliminar no se convierte en una política de publicación:
solo es construcción parcial permitida del borrador. Las validaciones de
publicación permanecen intactas.

## Concurrencia y rollback

El bloqueo de Caso se mantiene hasta el commit; las escrituras de hijos coordinan
esa raíz mediante V007. Tras los comandos se releen las seis tablas y se genera
la huella m3-v2 existente, que ya incluye opciones y sus campos. No se cambia
su formato. El resultado se devuelve después de confirmar. Fallos SQL, de dominio
o de commit revierten todo el lote, incluidas eliminaciones e INSERT previos.

La protección depende de triggers oficiales activos y privilegios apropiados.
No se añaden reintentos, eliminación de interbloqueos ni detección de historial ABA.
REQUIRES_NEW conserva sus límites conocidos de pool y transacciones externas.

## Pruebas preparadas, sin ejecutar

CasoUpdateContractTest añade tres métodos:

| Método | Cobertura |
| --- | --- |
| optionAddValidatesExplicitParametersWithoutNormalizingTextOrChoosingCorrectness | Correcta/incorrecta, UUID nulos, texto nulo/blanco, explicación null/literal, orden cero/negativo y parámetros intactos. |
| optionAddChecksInitialMembershipAndIdentityButDoesNotPreemptEarlierExplicitDeletion | Pregunta ausente/ajena a snapshot, UUID usado, eliminación existente y ausencia de rechazo anticipado por orden/corrección. |
| optionAddRequiresDraftAndPreservesArchivedStaleAndF303Rejections | Borrador, rechazo Publicado/Archivado, huella obsoleta y bloqueo. |

CasoCommandAddContentIT añade ocho métodos PostgreSQL 16.15:

| Método | Cobertura |
| --- | --- |
| addsExplicitIncorrectAndCorrectOptionsPreservingAcademicContentAndFingerprint | Ambas correcciones, UUID/FK/texto/explicación/orden, Unicode, null, huecos físicos, agregado sin cambios colaterales, otro caso y huella nueva. |
| optionAddRejectsUnknownForeignParentsStatesAndStalePreconditionWithoutChanges | Caso ausente, pregunta ausente/otro caso, Publicado/Archivado y STALE_CASE. |
| duplicateOptionsOrderAndSecondCorrectRejectAndRollBackEarlierCommands | UUID duplicado local/ajeno/en lote, orden ocupado, segunda correcta y fallo posterior a UPDATE+INSERT, con rollback integral. |
| deletingCorrectThenAddingNewCorrectCanReuseOrderButNeverOldUuidAndReverseOrderRollsBack | Eliminación seguida de alta correcta/reutilización de orden, orden inverso rechazado, UUID antiguo rechazado; fallo posterior a DELETE+INSERT restaura también la correcta eliminada. |
| optionAddRejectsNewQuestionParentAndDeletionOfNewOptionInSameBatch | Sin altas dependientes ni eliminación de opción recién agregada. |
| optionFkAndPositiveOrderRemainProtectedByOfficialSql | FK 23503 y CHECK 23514, sin persistir fixtures inválidas. |
| programmedCaseRejectsOptionAddAndDirectSqlInsertUsingOfficialF303 | Programación legítima, huella anterior/actual y trigger directo sin manipular fechas. |
| concurrentOptionAddsWithSameFingerprintHaveExactlyOneWinner | Escritores independientes, espera PostgreSQL real, un commit y STALE_CASE posterior. |

Se amplía solo el gate JDBC de pruebas para observar también INSERT OpcionRespuesta;
se mantienen límites, PID, latches, pg_blocking_pids y liberación de recursos previos.
No hay Thread.sleep ni triggers desactivados. La pregunta de la prueba de correcta
se crea/confirma primero mediante INC-11; no se introduce una dependencia dentro
del lote. Las notas/criterios y los estados académicos no se alteran.

La regresión conserva las pruebas existentes de escenarios y preguntas en la misma
clase, así como CasoCommandUpdateIT (eliminación) y CasoCommandConcurrencyIT
(incluidas referencias históricas). No fue necesario editar esas dos últimas clases.

## Archivos y validación manual

Modificados: CasoUpdateCommand.java, CasoCommandUpdateAdapter.java,
CasoUpdateContractTest.java y CasoCommandAddContentIT.java. Creado este documento.
No se modifican más archivos ni se amplían puertos transversales/update(Caso).

Desde PRISALY-Backen, por Integrante 2:

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandAddContentIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT,CasoPersistenceAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

Registrar commit ejecutado, fecha, XML Surefire/Failsafe, recuentos y SQLSTATE por
constraint/índice. Resultados nuevos pendientes; no se declara aprobado INC-12.
Persisten autorización institucional, DD-03, publicación/copia persistidas y otras
políticas previamente pendientes. No se avanza a otro incremento.
