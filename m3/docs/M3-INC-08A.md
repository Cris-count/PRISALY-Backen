# M3-INC-08A — Estructura interna del productor académico persistido

Estado: implementación estructural parcial, sin entrada productiva autorizada.
Dirección Técnica autorizó DTO, mapeador interno y pruebas; el responsable M3
(Integrante 2) manifestó conformidad en la instrucción de este incremento.
Referencia de base reportada: commit 4239c1b, no verificado mediante Git.
Rama comprobada por lectura de HEAD: feature/m3-gestion-casos.

## Archivos nuevos y arquitectura

Rutas relativas a m3:

- `src/main/java/com/prisaly/m3/api/ContextoAcademicoPersistido.java`
- `src/main/java/com/prisaly/m3/infrastructure/persistence/jpa/ContextoAcademicoProducerMapper.java`
- `src/test/java/com/prisaly/m3/infrastructure/persistence/jpa/ContextoAcademicoProducerMapperTest.java`
- `src/test/java/com/prisaly/m3/infrastructure/persistence/jpa/ContextoAcademicoProducerReadIT.java`
- `docs/M3-INC-08A.md`

No se modifican archivos existentes ni se añaden dependencias. El DTO pertenece a
M3, sin referencias M6, entidades JPA ni modelos de dominio en su API. El mapper es
final, package-private, sin anotaciones Spring y sin métodos públicos. No consulta
la base: recibe una lectura ya materializada. No hay productor bean, controller,
servicio expuesto, adaptador de autorización ni implementación de consulta nueva.

Flujo demostrado únicamente en pruebas internas:

`CasoJpaAggregateReader -> CasoJpaAggregateSnapshot -> mapper interno -> DTO`

No se usa CasoPersistenceMapper para la proyección productora. Solo se reutiliza
en la preparación de fixtures de integración junto al insert existente.

## Contrato y autorización

El puerto invocable queda BLOQUEADO. No se publica una firma que presuponga un
contexto de autorización institucional todavía no ratificado. Un record, un UUID,
String userId, rol declarado o token construido por el consumidor no acredita acceso.
El DTO transporta datos: construirlo no concede capacidad de consultar persistencia.

| Situación | Tratamiento de este incremento |
|---|---|
| Consumidor interno arbitrario | No dispone de una nueva entrada productiva |
| Contexto tipado construido por el llamador | No existe mecanismo que lo acepte como autorización |
| canModifyCase / canPublishCase | No se reutilizan como lectura |
| Acceso a respuestas correctas | Bloqueado hasta verificación institucional |
| Pruebas del mapper | Acceso interno de paquete, sin simular credenciales |
| Proveedor M1 ausente o no ratificado | No se habilita entrada, sin autorización por defecto |

Esta denegación por defecto es ausencia deliberada de una nueva entrada, no una
afirmación de que el lector interno preexistente sea un perímetro de seguridad.
El aislamiento Java de paquetes no sustituye controles institucionales ni impide
reflexión o código malicioso con acceso al proceso.

M1 y Arquitectura deben ratificar: identidad confiable de usuario/proceso, emisión
y validación del contexto tipado, alcance por recurso, permisos de contenido
evaluable/respuestas correctas, vigencia/revocación y auditoría. Después se podrá
aprobar el puerto y el proveedor verificador sin depender de DTO M6.

## DTO y campos conservados

Todos los records son inmutables; listas/sets se copian defensivamente sin elementos
null. Las colecciones vacías son válidas. Las fechas de creación son obligatorias
porque este DTO representa filas persistidas, no constructores transitorios M3.
Las referencias son UUID; no se generan ni se convierten desde datos legados.

| Record | Campos | Nulidad |
|---|---|---|
| ContextoAcademicoPersistido | idCaso, idDocente UUID; nombre, historia, desarrolloSituacional, estado String; tiempoEstimadoSegundos int; activo boolean; fechaBloqueoAcademico, fechaCreacion, fechaActualizacion Instant; escenarios List; rubrica Rubrica | Narrativas, bloqueo, actualización y rúbrica nullable; resto obligatorio |
| Escenario | idEscenario, idCaso UUID; titulo, contexto, instrucciones String; orden int; activo boolean; preguntas List | Tres textos nullable |
| Pregunta | idPregunta, idEscenario UUID; enunciado, textoAyuda, estadoEdicion String; visible boolean; orden int; fechaCreacion, fechaActualizacion Instant; opciones List | Ayuda y actualización nullable |
| Opcion | idOpcion, idPregunta UUID; texto, explicacion String; esCorrecta boolean; orden int | Explicación nullable |
| Rubrica | idRubrica, idCaso UUID; nombre, descripcion, definicionEvaluacion String; puntajeTotal BigDecimal; fechaCreacion Instant; preguntas Set | Descripción, JSON y total nullable |
| RubricaPregunta | idRubricaPregunta, idRubrica, idPregunta UUID; valorPregunta BigDecimal; criterio String | Criterio nullable |

BigDecimal se conserva sin redondeos ni conversión a entero, incluida la escala del
snapshot recibido. PostgreSQL puede normalizar su escala durante la inserción: la
comparación de fixture contra SQL es numérica, no una garantía de formato original.
JSONB permanece como texto del documento leído, sin doble serialización, parsing
académico o cambios de semántica. Instant se conserva sin fabricar fechas.

## Mapeo e integridad

Antes de producir resultado se comprueban:

- Identidad única por tabla de escenarios, preguntas y opciones.
- Escenario pertenece a Caso; Pregunta tiene Escenario; Opcion tiene Pregunta.
- Órdenes positivos y únicos dentro de cada padre, conforme V001/V002.
- Como máximo una opción correcta por pregunta, exclusivamente por el índice
  parcial UNIQUE de V002. No se exige ninguna opción correcta ni lista no vacía.
- Rúbrica pertenece a Caso; no existen asociaciones sin rúbrica.
- UUID de asociación único; asociación pertenece a la rúbrica y referencia una
  pregunta del caso; pregunta no repetida en asociaciones (V001/V003).

Los órdenes físicos se ordenan por valor sin renumeración; se preservan huecos.
No se filtran escenarios inactivos ni preguntas invisibles. No se omiten filas con
referencias inválidas. El fallo estructural produce IllegalStateException; campos
obligatorios null se rechazan al construir records, sin devolver un DTO parcial.
Set ya representa unicidad por igualdad completa: duplicados idénticos eliminados
antes de recibir el snapshot no se pueden reconstruir ni detectar retroactivamente.

Los DTO no reemplazan toda validación SQL. Tipos, CHECK de contenido, JSONB y
capacidades de columnas siguen garantizados por la persistencia oficial; no se
añade un parser JSON ni se reimplementa validación académica de publicación.
No se modifica Rubrica.isComplete ni se crea equivalencia con CriterioRubrica.

## Transacciones y límites

La integración de prueba usa el CasoJpaAggregateReader existente: TransactionTemplate
read-only, REQUIRES_NEW, REPEATABLE_READ, mismo EntityManager en todas las consultas,
CacheMode.IGNORE. El mapper opera sobre el resultado inmutable y no abre transacciones.
No se duplica SQL de lectura ni se cambia el lector.

Cambios posteriores al snapshot no se mezclan con él. La transacción externa se
suspende; sus cambios pendientes no son visibles. REQUIRES_NEW puede necesitar
otra conexión y espera acotada del pool. Las pruebas transaccionales existentes
siguen siendo la evidencia específica de aislamiento/conexión y deben regresarse.

F3-03 depende de V007–V009 activos y protege contenido programado, no acredita
permisos ni proporciona versionado histórico. m3-v2 identifica diferencias del
agregado actual: no se exporta ni se usa como credencial en este incremento.
No se afirma lectura conjunta M3–M5 ni contexto histórico del intento.

## Pruebas añadidas

Siete métodos unitarios: mapeo de todos los campos; órdenes con huecos; vacíos,
rúbrica ausente y nulos; duplicidades de identidad/orden/asociación; padres ajenos
y referencias huérfanas; colecciones inmutables; mapper sin exposición pública o
anotaciones de bean. Incluyen correctas, actividad/visibilidad falsas, BigDecimal
con escala explícita y fechas originales. No simulan permiso institucional.

Dos pruebas PostgreSQL 16.15: lectura real de las seis tablas y mapeo interno;
inexistencia frente a caso persistido con estructura vacía. Se aplican los scripts
V001–V009 oficiales sin cambios, Hibernate validate, zona UTC y fixtures mediante
el insert existente. Solo las fixtures escriben; el mapeador no escribe.

Se comprueba JSONB semánticamente y decimales por valor; no se desactivan triggers
ni se crean estados inválidos. Inconsistencias imposibles con FK activas se prueban
con snapshots unitarios. Acceso permitido/denegado de un productor real permanece
pendiente, porque no hay entrada ni verificador institucional aprobado.

## Validación manual pendiente

No se ejecutaron Git, Maven, Docker ni pruebas. No se declara compilación ni aprobación.
Desde PRISALY-Backen:

```powershell
mvn -pl m3 -am test
mvn -pl m3 -am -Pdocker-it "-Dit.test=ContextoAcademicoProducerReadIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
mvn -pl m3 -am -Pdocker-it verify
```

Registrar entorno, conteos, salidas y fallos reales. La última ejecución preserva
la regresión M3 existente. Este incremento no integra M3–M6 ni habilita exposición
segura de respuestas correctas. La composición app y los productores/consumidores
de otros módulos quedan fuera de alcance.

## Ampliación autorizada — productor protegido mediante puerto requerido

Esta sección actualiza el estado de la entrada productora descrito arriba; conserva
la trazabilidad de la fase estructural. Dirección Técnica y Arquitectura autorizaron
una implementación ejecutable M3 con verificación institucional delegada, sin
dependencias Maven M3→M1 ni M3→M6. Referencia base reportada: d8b120c.

### Archivos nuevos

- `src/main/java/com/prisaly/m3/api/ContextoAcademicoConsultaPort.java`
- `src/main/java/com/prisaly/m3/api/AccesoContextoAcademicoDenegadoException.java`
- `src/main/java/com/prisaly/m3/application/port/AutorizacionContextoAcademicoPort.java`
- `src/main/java/com/prisaly/m3/infrastructure/persistence/jpa/ContextoAcademicoProtectedReader.java`
- `src/test/java/com/prisaly/m3/infrastructure/persistence/jpa/ContextoAcademicoProtectedReaderTest.java`

Archivos modificados: ContextoAcademicoProducerReadIT y este documento. No se
alteran el DTO, mapper, lector transaccional, dominio, POM ni otros módulos.

### Firmas definitivas locales de M3

```java
Optional<ContextoAcademicoPersistido> consultar(UUID idCaso, String capacidad);
Decision verificar(String capacidad, UUID idCaso, Operacion operacion);
```

Operacion contiene CONSULTAR_CONTEXTO_EVALUABLE. Decision admite AUTORIZADO,
DENEGADO y NO_DISPONIBLE. Solo la confirmación AUTORIZADO del proveedor confiable
permite leer. La decisión es retorno del puerto requerido, nunca un argumento que
el consumidor de consulta pueda pasar para acreditarse.

La capacidad se transporta como String opaco mínimo: M3 no lo parsea, no establece
su codificación o longitud como prueba de autenticidad, no lo registra y no lo
incluye en respuestas ni excepciones. Un String fabricado también debe pasar por
verificación. Su inmutabilidad impide borrarlo de memoria de forma garantizada;
app/M1 deben evitar trazas de argumentos y retención innecesaria. No hay tipos de
M1/M6, credenciales fabricadas ni booleano de autorización recibido del consumidor.

El contrato requiere que app delegue a M1 para autenticar la capacidad, comprobar
identidad M6_EVALUACION, operación, UUID exacto, expiración y revocación. La entropía
de al menos 256 bits, SecureRandom y HMAC-SHA-256 son responsabilidades de M1:
ninguna de esas funciones se implementa o certifica en M3.

### Protección y errores

ContextoAcademicoProtectedReader se construye explícitamente por inyección del
puerto requerido y del lector existente; no tiene @Component ni @Bean automático.
Si el proveedor es null, deniega. No hay implementación permisiva productiva.

Flujo: UUID no null → capacidad presente/no blanca y proveedor presente → verificar
capacidad/caso/operación → exigir AUTORIZADO → lector → mapper. La verificación es
exactamente una por llamada y no se cachean decisiones. No se utiliza canModifyCase
ni canPublishCase. No hay consulta de existencia antes de verificar.

Capacidad ausente, denegada, decisión null, proveedor ausente o fallo RuntimeException
producen AccesoContextoAcademicoDenegadoException con un mensaje constante, sin UUID,
capacidad ni causa del verificador. Se omite esa causa para no filtrar un secreto que
el proveedor pudiera incluir en su mensaje. La auditoría segura del fallo corresponde
al proveedor institucional sin registrar el valor original de la capacidad.

Los errores del lector y del mapper quedan fuera del catch de seguridad y se
propagan sin convertirse en ausencia o denegación. Optional.empty se reserva al caso
inexistente después de verificación válida. El rechazo no depende de la existencia
del caso. UUID null es error de argumento antes de toda consulta.

### Garantías y límites transaccionales

Se reutiliza CasoJpaAggregateReader sin cambios: REQUIRES_NEW/REPEATABLE_READ y
su conexión de lectura existente. La verificación sucede antes de abrir esa lectura.
No se afirma atomicidad entre revocación/expiración institucional y la duración de
la consulta: M1/Arquitectura deben precisar la vigencia durante una operación en curso.
No hay snapshot conjunto M3–M5, versión académica ni nueva regla de evaluación.

### Pruebas incorporadas

Seis métodos unitarios adicionales comprueban: capacidad ausente/blanca y proveedor
ausente; rechazo de escenarios fabricado/vencido/revocado/otro caso/otra operación;
indisponibilidad, decisión null y ocultación de mensajes sensibles; orden de
verificación antes de una única lectura y mapeo completo; ausencia autorizada frente
a denegación sin consultas; propagación de error PostgreSQL y UUID null.

Las etiquetas de las pruebas son escenarios de rechazo, no tokens ni un verificador
ficticio de producción. Los dobles prueban la reacción de M3, no autenticidad, HMAC,
entropía, expiración o revocación reales.

La prueba PostgreSQL de seis tablas se extiende para pasar por el lector protegido
con doble de autorización y comparar todo el mapeo contra la lectura previa.
Dos métodos nuevos comprueban denegación antes de llamar al lector real, ausencia
después de autorizar y visibilidad de un cambio confirmado durante la verificación,
antes de iniciar la lectura académica. La última prueba demuestra orden de frontera,
no una carrera concurrente durante las seis consultas ni revocación atómica. Las
pruebas transaccionales/concurrentes preexistentes siguen siendo regresión necesaria.

### Pendiente de ensamblaje y validación

app deberá implementar AutorizacionContextoAcademicoPort delegando al verificador
real de M1 e inyectarlo en el productor. También realizará la futura traducción al
consumidor M6. Este incremento no modifica app, M1, M6, dependencias ni permisos.

El mecanismo es ejecutable con un proveedor inyectado, pero no se declara habilitada
la lectura productiva hasta validar seguridad e integración real M1–M3–M6. DD-03,
DD-04 y DD-12 siguen fuera de alcance. No se ejecutaron pruebas ni compilación.

Comandos manuales desde PRISALY-Backen:

```powershell
mvn -pl m3 -am test
mvn -pl m3 -am -Pdocker-it "-Dit.test=ContextoAcademicoProducerReadIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
mvn -pl m3 -am -Pdocker-it verify
```
