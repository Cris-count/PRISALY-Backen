# M6-INC-06 — Lectura de RetroalimentacionGeneral

## Autorización y estado

Dirección Técnica/Integrante 2 autorizó exclusivamente seis archivos nuevos para
consultar retroalimentaciones existentes. Se confirmó la rama mediante lectura de
.git/HEAD: feature/m6-evaluacion-calificaciones. d988b9a, 25 unitarias y nueve IT
aprobadas son antecedentes reportados de INC-05, no resultados de este incremento.
No se ejecutaron Git, Maven, Docker, compilaciones ni pruebas.

Se inspeccionaron el modelo RetroalimentacionGeneral, V001–V009 respecto de esta
tabla, los cuatro componentes de lectura Calificacion, su IT y m6/pom.xml.
No se modificó ningún archivo existente, POM, modelo, migración ni otro módulo.

## Seis archivos incorporados

1. src/main/java/com/prisaly/m6/application/port/ConsultaRetroalimentacionPort.java
2. src/main/java/com/prisaly/m6/infrastructure/persistence/jpa/RetroalimentacionGeneralJpaEntity.java
3. src/main/java/com/prisaly/m6/infrastructure/persistence/jpa/RetroalimentacionGeneralJpaRepository.java
4. src/main/java/com/prisaly/m6/infrastructure/persistence/jpa/RetroalimentacionGeneralReadAdapter.java
5. src/test/java/com/prisaly/m6/infrastructure/RetroalimentacionGeneralJpaReadIT.java
6. docs/M6-INC-06.md

Se reutilizan las dependencias JPA, starter-test, driver PostgreSQL y Testcontainers
existentes y el perfil docker-it/Failsafe de INC-05. No se agregan dependencias.

## Esquema y mapeo

Tabla public."RetroalimentacionGeneral", con nombres físicos citados exactamente.

| Columna | SQL oficial V001 | Java |
| --- | --- | --- |
| idRetroalimentacion | uuid PK NOT NULL | UUID @Id |
| idParticipacion | uuid NOT NULL UNIQUE, FK Participacion | UUID escalar |
| idDocente | uuid NOT NULL, FK Usuario | UUID escalar |
| contenido | text NOT NULL | String, columnDefinition=text |
| fechaRegistro | timestamptz NOT NULL DEFAULT now() | Instant |

Ambas FK tienen ON DELETE RESTRICT. No hay CHECK nonblank ni límite varchar para
contenido: se conserva literalmente, incluidos vacío, espacios, Unicode y saltos
de línea. No se recorta ni normaliza. La FK docente solo acredita Usuario existente,
no un permiso o rol institucional. El modelo vigente requiere fecha con precisión
exacta de microsegundos; no se modifica ni se generan fechas por defecto en Java.

## Consulta y semántica

ConsultaRetroalimentacionPort expone Optional<RetroalimentacionGeneral> mediante
findById(UUID) y findByParticipacionId(UUID). El adaptador rechaza null antes de
llamar al repositorio y reconstruye los cinco campos. Optional.empty() significa
ausencia, no fallo. Por participación, empty no distingue participación inexistente
de participación sin retroalimentación. Fallos de persistencia/reconstrucción se
propagan sin convertirlos en ausencia.

RetroalimentacionGeneralJpaRepository extiende únicamente Repository y expone
findById y findByIdParticipacion. UNIQUE(idParticipacion) justifica el Optional
singular. No hay save, delete, flush ni mutaciones. La entidad lleva @Immutable,
constructor protegido, UUID escalares y getters; no hay setters, generadores,
asociaciones ajenas, DDL ni valores predeterminados de aplicación.

Repositorio y adaptador declaran @Transactional(readOnly=true), siguiendo INC-05.
La propagación es REQUIRED y el aislamiento predeterminado: no se añade una nueva
política ni un snapshot común entre llamadas. Una transacción escritora externa
no pasa automáticamente a ser de solo lectura por esta anotación.

## Diferencia entre lectura JPA e inmutabilidad SQL

V001–V009 no establecen para RetroalimentacionGeneral el trigger que V003 aplica
a Calificacion. No se inventa una prohibición SQL de editar/eliminar feedback.
@Immutable limita dirty checking del mapeo; readOnly es una característica de
la transacción, no una frontera de privilegios que impida SQL arbitrario.
No hay escrituras productivas en este incremento. La protección efectiva requiere
privilegios apropiados y ensamblado autorizado, todavía fuera de alcance.

Otro escritor puede modificar la tabla entre lecturas. Cada consulta recupera una
fila completa, sin asegurar estabilidad entre invocaciones ni resolver concurrencia
de futuras ediciones. No se implementan políticas de publicación, visibilidad,
liberación, eliminación, acceso ni DD-03. El puerto es interno, sin REST.

## Pruebas preparadas (10 métodos, sin ejecutar)

RetroalimentacionGeneralJpaReadIT usa postgres:16.15, ejecuta V001–V009 canónicas
por JDBC y levanta contexto Spring con repositorios/proxies transaccionales reales.
Hibernate validate verifica el mapeo, sin generar DDL; zona JDBC UTC.

| Método | Cobertura |
| --- | --- |
| readsBothIdentifiersAndAllFiveFieldsWithoutMixingParticipations | Ambas claves, campos exactos, docente, microsegundos e independencia de participaciones. |
| missingFeedbackIsEmptyAndNullIdentifiersAreInvalid | Ausencia, participación existente sin registro y UUID null. |
| textIsPreservedLiterallyIncludingEmptyWhitespaceLineBreaksAndSupplementaryUnicode | Vacío, espacios, CR/LF, tabuladores, caracteres suplementarios, combinaciones Unicode y texto extenso. |
| readsPostgresDefaultDateWithoutManufacturingOrLosingMicroseconds | DEFAULT real, comparación con JDBC y precisión. |
| uniqueParticipationRejectsDuplicateWithoutChangingOriginal | UNIQUE, SQLSTATE 23505 y conservación. |
| rejectsUnknownParticipationAndTeacherThroughOfficialForeignKeys | Ambas FK, SQLSTATE 23503. |
| rejectsNullInEachMandatoryColumnIncludingExplicitNullDate | Los cinco NOT NULL, SQLSTATE 23502; null explícito no usa DEFAULT. |
| productionApiHasOnlyQueriesAndEntityHasNoPublicMutationApi | Reflexión sobre contratos y mapeo sin API de mutación. |
| failuresArePropagatedAndNullInputNeverReachesRepository | Doble solo en test; errores no ocultos y null rechazado antes de consultar. |
| existingGradeReaderWorksAlongsideFeedbackReaderInSameJpaContext | Regresión INC-05 por ambas claves, BigDecimal/JSONB/Instant y coexistencia con feedback. |

Ocho métodos ejercitan datos PostgreSQL; dos complementan con reflexión/dobles.
Las fixtures JDBC usan Usuario/Estudiante, Caso Publicado activo, Sesion,
Convocatoria y Participacion válidos. La programación activa F3-03 mediante su
trigger, sin actualizar el bloqueo manualmente. Para la regresión se crea la
rúbrica antes de programar y un primer Intento Finalizado con tiempos coherentes;
la nota es un dato sintético de transporte, no un cálculo ni integración M5.
No se inventan estados de evaluación o participación; se usan defaults oficiales.
No se modifican/desactivan restricciones ni migraciones.

El escaneo JPA incluye ambos mapeos/repositorios de M6; las nueve pruebas originales
de INC-05 deben ejecutarse además sin editar su archivo. La regresión nueva no
sustituye esa suite. La ejecución JDBC de scripts no certifica Flyway schema history
ni el despliegue de app. Los valores especiales timestamptz infinity quedan fuera
del modelo Instant actual; fallos de reconstrucción no se ocultan.

## Evidencia pendiente y validación manual

Desde PRISALY-Backen, por Integrante 2:

```powershell
.\mvnw.cmd -B -pl m6 -am test
.\mvnw.cmd -B -pl m6 -am -Pdocker-it "-Dit.test=RetroalimentacionGeneralJpaReadIT,CalificacionJpaReadIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

Registrar fecha, commit ejecutado, XML Surefire/Failsafe, recuentos y SQLSTATE.
La ausencia de Docker o migraciones debe fallar, no saltar pruebas silenciosamente.
No se declara BUILD SUCCESS, aprobación de INC-06 ni evaluación funcional completa.
Siguen pendientes autorización M1, políticas de retroalimentación y contratos de
integración. Fin de alcance: no se avanza a otro incremento.
