# M6-INC-05 — Lectura de calificaciones persistidas

## Alcance y evidencia de inspección

Dirección Técnica/Integrante 2 autorizó únicamente consulta de Calificacion y
dependencias/perfil de integración de m6/pom.xml. Se confirmó la rama
feature/m6-evaluacion-calificaciones leyendo .git/HEAD, sin ejecutar Git.
e7bb443 y 25 pruebas unitarias aprobadas son referencias reportadas, no resultados
de esta intervención. No se ejecutaron Maven, Docker, compilaciones ni pruebas.

Se inspeccionaron POM raíz/M6, Calificacion.java, V001–V009 oficiales, POM M3,
RubricaJpaEntity, CasoJpaRepository, RubricaJpaReadIT, archivos locales del BOM
Spring Boot y configuración .mvn (wrapper). No se modifican fuentes ajenas a M6.

## Dependencias y versiones

M6 solo declaraba spring-boot-starter-test, scope test. El padre administra
versiones pero no hereda JPA de app u otros módulos. Se conserva la dependencia
de pruebas y se agregan, sin versiones explícitas:

| Dependencia | Alcance | Administración inspeccionada |
| --- | --- | --- |
| spring-boot-starter-data-jpa | compile | Spring Boot 4.1.2; Hibernate 7.4.13.Final |
| org.postgresql:postgresql | test | 42.7.14 |
| org.testcontainers:testcontainers-postgresql | test | 2.0.5 |
| maven-failsafe-plugin, perfil docker-it | plugin integration-test/verify | 3.5.6 |

Versiones verificadas en el POM local spring-boot-dependencies/4.1.2 y su import
del BOM Testcontainers. La configuración efectiva/resolución debe comprobarse
manualmente; no se ejecutó Maven. El servidor de prueba es postgres:16.15.
No se agrega Flyway a M6: la IT ejecuta los scripts canónicos por JDBC siguiendo
la convención de M3; no certifica el historial ni el arranque de Flyway en app.

## Archivos y contratos

- application/port/ConsultaCalificacionPort: findById(UUID) y findByIntentoId(UUID),
  ambos Optional<Calificacion>. UUID null se rechaza en el adaptador antes de
  consultar. Empty significa ausencia de calificación, no error de persistencia.
  Por intento, empty no distingue intento inexistente de intento sin calificación.
- infrastructure/persistence/jpa/CalificacionJpaEntity: fila JPA separada del
  dominio, @Immutable, getters y constructor protegido, sin setters ni generador.
- CalificacionJpaRepository: extiende el marcador Repository, únicamente findById
  y findByIdIntento; no hereda save/delete/flush ni publica escrituras.
- CalificacionReadAdapter: implementa el puerto, readOnly transaccional, reconstruye
  todos los componentes de Calificacion. No captura errores para convertirlos en
  Optional.empty(). No accede a repositorios ni entidades de otros módulos.
- test/infrastructure/CalificacionJpaReadIT: PostgreSQL real y contexto Spring
  reducido que crea el repositorio y proxy transaccional reales.

Las rutas de producción anteriores son relativas a src/main/java/com/prisaly/m6;
la prueba está en src/test/java/com/prisaly/m6/infrastructure.

## Mapeo BD-005.2

Tabla public."Calificacion"; todas las columnas tienen su nombre físico citado.

| Columna | SQL | Java / tratamiento |
| --- | --- | --- |
| idCalificacion | uuid PK NOT NULL | UUID @Id, sin generación |
| idIntento | uuid NOT NULL UNIQUE, FK Intento | UUID escalar |
| idRubrica | uuid NOT NULL, FK Rubrica | UUID escalar |
| valor | numeric(8,2) NOT NULL | BigDecimal, precision=8 scale=2, sin conversiones flotantes |
| fechaCalculo | timestamptz NOT NULL DEFAULT now() | Instant sin fechas ficticias |
| detalleEvaluacion | jsonb NOT NULL | @Formula cast("detalleEvaluacion" as text) |
| versionPolitica | varchar(50) nullable | String, length=50 |

La fórmula devuelve el texto JSONB emitido por PostgreSQL sin FormatMapper,
serialización duplicada ni estructura académica inventada. Conserva semántica,
no formato original, espacios o claves duplicadas previas al almacenamiento.
El documento JSON null devuelve "null"; SQL NULL sería Java null y además está
prohibido por V001. La fórmula no es un mapeo de escritura ni valida la columna
JSONB mediante hbm2ddl; su existencia/tipo/nulidad y comportamiento se contrastan
con scripts oficiales y pruebas reales, no por la anotación sola.

BigDecimal conserva el valor numérico y la escala recibida de PostgreSQL (dos
decimales), no necesariamente la escala del cliente antes del INSERT fixture.
Cero y negativos físicamente representables son admitidos: no hay CHECK de
positividad para Calificacion.valor. Las fechas se recuperan con microsegundos;
el modelo existente mantiene sus validaciones, sin modificarlas.

## Garantías y límites de solo lectura

Cada operación recupera una fila completa en una consulta. Repositorio y adaptador
declaran @Transactional(readOnly=true), propagación/isolation predeterminadas;
no añaden REQUIRES_NEW, bloqueos ni prometen snapshot común entre invocaciones.
Si existe una transacción externa compatible, se participa en ella; readOnly
no convierte por sí solo una transacción escritora externa en lectura estricta.

La API solo expone consulta y @Immutable deshabilita dirty checking de cambios
de la entidad. Ninguna de esas medidas constituye un cortafuegos contra SQL
arbitrario u operaciones EntityManager ajenas. V003 protege UPDATE/DELETE de
calificaciones históricas; permisos productivos y triggers activos siguen siendo
necesarios. No hay escritura productiva ni DDL automático implementado.

V001 impone una calificación por intento. V003 exige rúbrica del mismo caso que
el intento y rechaza UPDATE/DELETE con SQLSTATE 23514. No se infiere una fórmula,
escala, versión de política aprobada o autorización para evaluar. El lector no
filtra por estado de intento ni aplica políticas nuevas de liberación de resultados.
El puerto es interno; no hay REST ni autorización institucional de consulta.

## Pruebas preparadas: nueve métodos, pendientes de ejecución

La IT utiliza @EnableJpaRepositories y @EnableTransactionManagement, mapeo validate,
zona JDBC UTC y solo el paquete de entidades M6. No usa entidades de otros módulos.

| Método | Evidencia prevista |
| --- | --- |
| readsAllColumnsByGradeAndPersistentAttemptUuidWithoutMixingAttempts | UUID de ambos tipos, todos los campos, 12.50/7.25, fecha exacta, política nullable, dos intentos, JSONB semánticamente igual con objetos/arrays/número de alta precisión. |
| missingGradeIsEmptyAndNullIdentifiersAreRejected | UUID ausente, intento existente sin calificación e identificadores null. |
| readsZeroNegativeAndPhysicalNumericBoundariesExactly | 0.00, -12.50 y ambos extremos de NUMERIC(8,2). |
| preservesOfficialDateDefaultAndUnicodePolicyAndDistinguishesJsonNullFromSqlNull | DEFAULT real de fecha, microsegundos, política Unicode de 50 caracteres, JSON null aceptado frente a SQL NULL rechazado con 23502. |
| uniqueAttemptRejectsSecondGradeWithoutChangingOriginal | UNIQUE idIntento, 23505, conservación del registro. |
| foreignKeysAndCaseCorrespondenceRemainEnforcedWithOfficialTriggerPriority | Rúbrica de otro caso, referencias ausentes y FK validadas RESTRICT en catálogo. |
| officialV003RejectsUpdateAndDeleteAndPreservesStoredGrade | Escrituras JDBC rechazadas con 23514, sin cambios de calificación. |
| productionContractsExposeOnlyQueriesAndEntityIsImmutableWithoutPublicSetters | Reflexión sobre los dos contratos y entidad sin API pública de mutación. |
| repositoryFailuresArePropagatedRatherThanReportedAsMissingGrades | Doble solo en test: ambos fallos de repositorio se propagan, no producen empty. |

Siete métodos ejercitan datos/SQL PostgreSQL; dos complementan con reflexión/dobles.
Los INSERT/UPDATE/DELETE existen únicamente en src/test para fixtures y pruebas
negativas. No se deshabilitan ni modifican triggers.

Cada fixture crea una cadena referencial válida con estudiante/caso nuevos:
Usuario/Estudiante, Caso Publicado activo, Rubrica, Sesion (activa F3-03 mediante
trigger), Convocatoria, Participacion y primer Intento Finalizado con tiempos
coherentes. No se inventan motivos, estados de evaluación ni algoritmos: los
valores de calificación son datos sintéticos de transporte y no certifican el
flujo funcional M5/M6. Los defaults SQL permanecen activos.

Una referencia ausente puede producir 23514 primero porque V003 compara los casos
antes de comprobar FK. La prueba lo reconoce para cada referencia aislada y
comprueba ambas FK en pg_constraint; con ambas referencias inexistentes, la
comparación NULL/NULL permite llegar al rechazo FK 23503. Las filas inválidas
solo son intentos de inserción rechazados, no datos almacenados forzadamente.

## Riesgos y pendientes

No se certifica arranque de app, privilegios productivos, Flyway schema history,
integración M1/M3/M5 ni escenarios de evaluación. El lector no suple permisos M6
ni evita por sí mismo acceso directo a datos por otros componentes.
El modelo usa BigDecimal/Instant finitos y precisos: valores especiales de
PostgreSQL como numeric NaN o timestamps infinity quedan fuera del modelo actual;
un fallo de reconstrucción se propaga, nunca se disfraza como ausencia.
No se promete consistencia de varias consultas en transacciones distintas.
Las pruebas no dependen de un pool limitado ni implementan una prueba concurrente.

DD-03, redondeo, inserción funcional/idempotencia, autorización, corrección histórica,
reevaluación, liberación y lectura de retroalimentación requieren otros incrementos.
No se modifica Strategy, Protection Proxy, modelos anteriores ni otros módulos.

## Validación manual y registro de evidencia

Desde PRISALY-Backen, únicamente por Integrante 2:

```powershell
.\mvnw.cmd -B -pl m6 -am test
.\mvnw.cmd -B -pl m6 -am -Pdocker-it "-Dit.test=CalificacionJpaReadIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

Registrar commit real ejecutado, fecha, PostgreSQL 16.15, XML Surefire/Failsafe,
recuento de tests y SQLSTATE de restricciones. Si falta Docker o las migraciones,
la prueba falla; no se omite silenciosamente. Los scripts se aplican como en M3,
no mediante migraciones inventadas. No se declara M6-INC-05 aprobado ni evaluación
funcional terminada sin ejecución y revisión de resultados.
