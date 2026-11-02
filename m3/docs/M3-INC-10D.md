# M3-INC-10D — Alta explícita de escenario vacío

## Decisión y alcance

Dirección Técnica/Integrante 2 autorizó AgregarEscenarioCommand exclusivamente para
casos existentes Borrador sin bloqueo F3-03. Rama y commit de referencia reportados:
feature/m3-gestion-casos, 8267bc0, PR #6 únicamente para revisión. No se verificaron
mediante Git. No se ejecutaron Maven, Docker ni pruebas durante esta intervención.

Contrato anidado en CasoUpdateCommand:

```java
record AgregarEscenarioCommand(UUID idEscenario, String titulo, String contexto,
        String instrucciones, int orden) implements CasoUpdateCommand {}
```

UUID obligatorio, suministrado por el llamador. Título nullable de hasta 200 puntos
de código Unicode; contexto e instrucciones nullable; orden positivo explícito.
No contiene activo, preguntas, fechas ni un idCaso independiente. El padre es el
UUID de update(). No se infiere ni renumera orden. No se generan identidades.

## SQL y protocolo

Se conserva REQUIRES_NEW/READ_COMMITTED, el mismo JpaTransactionManager y conexión
Hibernate, SELECT Caso FOR UPDATE, lectura de las seis tablas y precondición m3-v2.
No se modifica CasoRepositoryPort.update(Caso), el lector, mapper ni la huella.
La huella existente ya incluye filas de Escenario y su contenido.

La prioridad de errores permanece: precondición obsoleta, Archivado, F3-03;
después se aplica SCENARIO_ADD_REQUIRES_DRAFT para las altas fuera de Borrador.
SCENARIO_ID_IN_USE identifica UUID existente observado; SCENARIO_ORDER_IN_USE,
orden ocupado en el snapshot inicial. Antes de insertar se consulta globalmente
el UUID sobre la misma conexión para impedir reutilización entre casos.

```sql
INSERT INTO public."Escenario"
  ("idEscenario", "idCaso", "titulo", "contexto", "instrucciones", "orden")
VALUES (?, ?, ?, ?, ?, ?)
```

activo se omite para utilizar DEFAULT true de V001. No se insertan preguntas ni
se actualiza Caso u otras filas como efecto del alta. El trigger V007 vuelve a
coordinar la raíz y rechaza contenido bloqueado. PK y UNIQUE(idCaso,orden), CHECK
y FK permanecen activos; la validación previa no sustituye sus garantías.

Todos los comandos se validan contra el snapshot inicial antes de escribir. No
se permite editar el escenario nuevo dentro del mismo lote. Altas independientes
pueden componerse; si un UUID u orden se repite dentro del lote, cualquier fallo
posterior revierte también los comandos anteriores. El resultado incluye la
relectura completa y huella nueva, entregadas después del commit. No hay UPSERT.
Una colisión concurrente no visible en la consulta previa sigue siendo rechazada
por PostgreSQL; los errores SQL de unicidad no se convierten indiscriminadamente
en errores de dominio.

## Pruebas incorporadas (ejecución pendiente)

CasoUpdateContractTest añade tres métodos: contrato válido/nulos/orden/Unicode;
conflictos con snapshot inicial y rechazo de edición dependiente sin mutarlo;
restricción a Borrador y prioridades de huella, Archivado y F3-03.

CasoCommandAddContentIT añade siete métodos con PostgreSQL 16.15 y V001–V009:

| Método | Cobertura |
| --- | --- |
| addsEmptyActiveScenarioPreservingAllPriorContentAndOtherCase | Alta, UUID, título Unicode, textos, nulos, default activo, cero preguntas, órdenes con huecos, huella y recuperación, conservación de contenido/metadatos y otro caso. |
| rejectsMissingCasePublishedArchivedAndStalePreconditionWithoutMutation | Caso ausente, estados no autorizados y precondición obsoleta. |
| rejectsDuplicateUuidAndOrderAndRollsBackCompositeSqlFailure | UUID del mismo/otro caso, orden existente, UUID repetido en lote, conflicto SQL 23505 después de comandos previos y rollback completo. |
| rejectsDependentEditOfNewScenarioBeforeAnyMutation | Alta seguida de edición dependiente rechazada antes de insertar. |
| twoAddsWithSameFingerprintHaveExactlyOneWinner | Dos escritores independientes; el segundo espera la raíz y después rechaza STALE_CASE. |
| programmingWaitsForAddingDraftThenV009RejectsItWithoutLockingDraft | La programación espera al alta; V009 impide programar Borrador. El escenario confirmado se conserva y no se inventa bloqueo. |
| officialProgrammingLocksPublishedCaseAndRejectsAddWithCurrentFingerprint | Programación legítima activa F3-03; huella antigua rechazada, huella actual bloqueada y INSERT JDBC rechazado por trigger. |

La instrumentación de DataSource solo existe en pruebas: detiene el INSERT después
de validar y retener Caso; verifica esperas mediante pg_blocking_pids y PID del
backend. Conexión observadora independiente, sin pool limitado; sin Thread.sleep.
Conexión JDBC: connectTimeout 5 s, socketTimeout 12 s, statement_timeout 10 s;
barreras/sondeo 5 s, liberación 10 s, futuros/terminación 15 s, JUnit 60 s.
Recursos JDBC y ejecutores se cierran incluso ante errores. No se desactivan triggers.

No es legítimo probar programación exitosa sobre Borrador: V009 lo prohíbe.
Las dos variantes de programación usan estados válidos, sin simular publicación
ni actualizar manualmente fechaBloqueoAcademico. No se certifica integración M4.

## Matriz de cobertura de M3 y fuentes disponibles

No se localizó el catálogo V27 ni el documento de los 21 casos de uso en los
archivos locales encontrados. Tampoco se dispone aquí del texto normativo completo
ARQ/DD. No se inventan códigos ni se asignan números de casos de uso. La descripción
oficial integral y la correspondencia de cada funcionalidad con V27 quedan pendientes
de verificar cuando el equipo entregue esos documentos.

Las únicas correspondencias codificadas siguientes proceden de migraciones
oficiales locales. El estado se limita a la implementación M3, no al requisito
intermodular completo. "Parcial" no niega los resultados previos reportados:
señala integración/evidencia documental o ejecución nueva todavía pendiente.

| Código de requisito/caso | Descripción oficial localizable | Funcionalidad implementada | Estado | Evidencia de código/pruebas | Dependencias y justificación |
| --- | --- | --- | --- | --- | --- |
| RF-09 | V009: solo casos Publicado y activo para programación; texto integral V27 no disponible | Consulta de disponibilidad alineada, trigger SQL | Parcial | CasoApplicationService; CasoMetadataTest; V009; IT de programación | Integración M4 y correspondencia V27 pendientes. |
| F3-03 | V007: edición de contenido y programación comparten bloqueo sobre Caso; contenido programado inmutable | Rechazos y transacciones del adaptador; triggers oficiales | Parcial | CasoCommandUpdateAdapter; CasoCommandConcurrencyIT; CasoCommandAddContentIT; V007–V009 | Integración funcional/privilegios externos no certificados; nuevas pruebas sin ejecutar. |
| Pendiente de verificar en V27/21 CU | No disponible; inventario técnico: creación y recuperación | Inserción y lectura de seis tablas | Parcial | CasoPersistenceAdapter/Mapper/Reader; CasoPersistenceAdapterIT | Servicio sin orquestación persistida; no inventar código de requisito. |
| Pendiente de verificar en V27/21 CU | No disponible; inventario técnico: edición explícita | Comandos parciales y alta de escenario vacío en Borrador | Parcial | CasoUpdatePort/Command; CasoCommandUpdateAdapter; CasoUpdateContractTest; CasoCommandUpdateIT y AddContentIT | 10D pendiente de ejecución; no implica CRUD completo. |
| Pendiente de verificar en V27/21 CU | No disponible; inventario técnico: publicación/copia | Reglas puras existentes; persistencia del flujo no conectada | Parcial | CasoPublication; ContenidoEvaluableLock; pruebas unitarias M3 | Metadatos de copia y completitud de rúbrica pendientes de decisión. |
| Pendiente de verificar en V27/21 CU | No disponible; inventario técnico: altas de preguntas/opciones/rúbricas/asociaciones en casos existentes | No incluidas en comandos | Pendiente | CasoUpdateCommand enumera el subconjunto actual | Requieren nueva autorización; DD-03 no se implementa. |
| Pendiente de verificar en V27/21 CU | No disponible; inventario técnico: Archivado/restauración | Representación y rechazo preventivo, sin transiciones | Parcial | CasoEstado; validaciones de dominio/adaptador | Política pendiente. |

No hay base documental para declarar completo M3 ni para afirmar cobertura de los
21 casos de uso. El inventario técnico sin código no es un catálogo oficial nuevo.

## Pendientes y validación manual

Conservar resultados previos reportados: 91 unitarias, 31 PostgreSQL, BUILD SUCCESS;
no corresponden a los nuevos tests. Registrar para 10D commit realmente ejecutado,
fecha, XML Surefire/Failsafe, resultados y SQLSTATE. No se declararon aprobados.

Persisten permisos M1, publicación/copia persistidas, conciliación ARQ/DD,
interbloqueos, dimensionamiento/límites productivos y ausencia de detección ABA.
No hay reintentos automáticos, endpoints, integración ni políticas nuevas.

Desde PRISALY-Backen:

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandAddContentIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT,CasoPersistenceAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```
