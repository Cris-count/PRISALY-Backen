# M6-INC-07 — Contratos consumidores estructurales M3/M5

Estado: preparado, sin ejecución de pruebas ni integración productiva.
Dirección Técnica autoriza exclusivamente puertos locales, DTO inmutables,
validación estructural, pruebas unitarias y documentación. No autoriza calificar,
escribir resultados, seleccionar última calificación ni liberar información.

## Archivos nuevos

Rutas relativas a m6:

- `src/main/java/com/prisaly/m6/application/port/ConsultaIntentoEvaluacionPort.java`
- `src/main/java/com/prisaly/m6/application/port/ConsultaContextoAcademicoPort.java`
- `src/main/java/com/prisaly/m6/application/integration/IntentoEvaluacion.java`
- `src/main/java/com/prisaly/m6/application/integration/ContextoAcademico.java`
- `src/test/java/com/prisaly/m6/application/integration/ContratosConsumidoresTest.java`
- `docs/M6-INC-07.md`

No se modifican archivos anteriores, otros módulos, POM ni migraciones.

## API local

```java
Optional<IntentoEvaluacion> findById(UUID idIntento);
Optional<ContextoAcademico> findByCasoId(UUID idCaso);
```

Son demandas de información de M6, no contratos productores ya ratificados.
Los futuros adaptadores deben rechazar UUID null, devolver Optional.empty solo
para ausencia y propagar fallos técnicos. Una colección vacía no significa ausencia
del registro raíz. Las pruebas con dobles ilustran el contrato, no acreditan un
adaptador real. No existen beans ni implementaciones ficticias productivas.

## DTO, campos, obligatoriedad y origen

Todos los UUID son tipos propios del JDK sin generar ni sustituir identificadores.
Las listas/sets son obligatorios, sin elementos null, y se copian defensivamente.
Se admiten vacíos. El orden recibido de listas se conserva; no se renumera ni se
certifica que su posición sea el orden físico. Las asociaciones de rúbrica son Set
sin orden contractual.

| Record | Campos y tipos | Obligatorios / opcionales | Origen inspeccionado |
|---|---|---|---|
| IntentoEvaluacion | idIntento, idParticipacion, idCaso: UUID; estado: EstadoIntento; respuestas: List<RespuestaReferencia> | Todos no null; respuestas puede estar vacía | Intento/Participacion/Sesion V001 y resolución técnica; M5 ResumenIntento |
| RespuestaReferencia | idPregunta, idOpcionSeleccionada: UUID | Ambos obligatorios | M5 Respuesta/MapeoIntento y Respuesta V001 |
| ContextoAcademico | idCaso, idDocente: UUID; nombre, historia, desarrolloSituacional, estado: String; tiempoEstimadoSegundos: int; activo: boolean; fechaCreacion, fechaActualizacion, fechaBloqueoAcademico: Instant; escenarios: List<Escenario>; rubrica: Rubrica | UUID/nombre/estado/lista obligatorios; narrativas, fechas y rúbrica admiten null | Caso V001/V002 y modelo actual M3 |
| Escenario | idEscenario, idCaso: UUID; titulo, contexto, instrucciones: String; orden: Integer; activo: boolean; preguntas: List<Pregunta> | UUID/lista obligatorios; textos y orden admiten null | Escenario V001/V002 y M3 |
| Pregunta | idPregunta, idEscenario: UUID; enunciado, textoAyuda, estadoEdicion: String; visible: boolean; orden: Integer; fechaCreacion, fechaActualizacion: Instant; opciones: List<Opcion> | UUID/enunciado/estado/lista obligatorios; ayuda, orden y fechas admiten null | Pregunta V001/V002 y M3 |
| Opcion | idOpcion, idPregunta: UUID; texto, explicacion: String; esCorrecta: boolean; orden: Integer | UUID/texto obligatorios; explicación/orden nullable | OpcionRespuesta V001/V002 y M3 |
| Rubrica | idRubrica, idCaso: UUID; nombre, descripcion, definicionEvaluacion: String; puntajeTotal: BigDecimal; fechaCreacion: Instant; preguntas: Set<RubricaPregunta> | UUID/nombre/set obligatorios; descripción, total, JSON y fecha nullable | Rubrica V001/V002 y M3 estructural |
| RubricaPregunta | idRubricaPregunta, idRubrica, idPregunta: UUID; valorPregunta: BigDecimal; criterio: String | UUID/valor obligatorios; criterio nullable | RubricaPregunta V001/V002/V003 y M3 |

EstadoIntento local conserva exclusivamente EN_CURSO y FINALIZADO, correspondientes
a EnCurso y Finalizado de V007/M5. No contiene métodos de transición ni elegibilidad.
Estado de Caso conserva Borrador/Publicado/Archivado; estadoEdicion conserva
Borrador/Lista. Se rechazan valores ajenos a esos catálogos sin traducirlos por heurística.

### Diferencia entre DTO consumidor y fila SQL completa

SQL requiere fechaCreacion y órdenes en varias tablas. El dominio M3 admite fechas
y órdenes aún no asignados. Este consumidor conserva esos null en vez de inventar
defaults o exigir garantías que el productor aún no ofrece. Un adaptador que declare
entregar filas persistidas completas deberá certificar sus campos NOT NULL. Estos
DTO no son entidades ni comandos de escritura y no validan toda la integridad SQL.

No se incorpora Escenario.descripcion legado porque no tiene equivalencia SQL aprobada;
un futuro adaptador deberá rechazar o conciliar esa representación sin perderla.
No se incorpora CriterioRubrica ni se transforma la asociación RubricaPregunta.
Una rúbrica legada sin estructura tampoco se transforma inventando nombre o UUID.

## Validaciones y límites

Se exigen referencias no null y tipos UUID. Tiempo positivo y órdenes positivos
cuando se proporcionan, conforme V002. Nombre máximo 150 y título de escenario
máximo 200 puntos de código Unicode, sin trim ni cambios de contenido. Los textos
NOT NULL SQL no se convierten en una nueva regla de no-vacío académica.

Total opcional y valorPregunta obligatorio deben ser positivos y exactamente
representables en NUMERIC(8,2). Se conserva el BigDecimal original, incluida su escala;
no hay double ni redondeo. JSON y fechas se transportan sin parsear, calcular o
normalizar; no se exige precisión adicional en esta proyección no persistible.

No se certifican coincidencias entre UUID padre-hijo, unicidad SQL ni cobertura de
preguntas: se conservan las referencias explícitas. La integridad real debe aportarla
el productor aprobado y su persistencia. No se aplica correspondencia académica
intento-caso-rúbrica, completitud, respuesta correcta obligatoria o lista no vacía.

## Garantías pendientes y matriz de dependencias

| Productor | Código actual | Pendiente antes del adaptador |
|---|---|---|
| M5 | ResumenIntento con estado/motivo/respuestas; MapeoIntento vincula valor con UUID de opción; HistorialParticipacionPort solo consulta existencia para M4 | Proyección de UUID persistente, participación y caso; consistencia de lectura; tratamiento de ausencia/errores; finalización y autorización histórica |
| M3 | CasoRepositoryPort/CasoUpdatePort internos, modelos y persistencia académica | Proyección pública estable, UUID/relaciones, datos persistidos o transitorios, consistencia y acceso a respuestas correctas; rúbrica estructural sin equivalencia legada |
| M1 | IdentityAccessPort y contrato consumidor AutorizacionEvaluacionPort de M6 | Identidad confiable y permisos concretos; no derivarlos de la solicitud |

Los puertos no garantizan una instantánea conjunta M3/M5 ni autorización institucional.
M6 no consulta SQL de M3/M5 ni depende de sus clases de compilación. No se modifica
SolicitudEvaluacion, Strategy, Protection Proxy ni lectores JPA existentes.

DD-03 sigue bloqueando fórmulas, escalas, ponderaciones, redondeos y conciliación de
rúbrica. DD-04 no se implementa: no se selecciona última calificación ni se deduce
autorización histórica. DD-12 no se implementa: no se libera ni expone información.
FINALIZADO no equivale a autorizado, evaluable, evaluado ni publicado.

## Pruebas y evidencia pendiente

Seis pruebas unitarias: UUID/estados/referencias y listas defensivas; obligatorios
null y listas vacías; estructura anidada, nulos/metadatos/órdenes/decimales/asociaciones;
vacíos y metadatos no asignados; límites físicos/Unicode; Optional.empty frente a
fallos técnicos con dobles locales y API de consulta sin escritura.

Las pruebas no ejecutan Strategy ni JPA, no simulan calificaciones ni autorizaciones
académicas, y no acreditan integración. Los futuros adaptadores necesitarán pruebas
propias de mapeo, pertenencia, consistencia y propagación de errores tras aprobar contratos.

No se ejecutaron Git, Maven, Docker ni pruebas. Validación manual desde PRISALY-Backen:

```powershell
mvn -pl m6 -am test
mvn -pl m6 -am -Pdocker-it verify
```

El segundo comando es regresión opcional de los lectores PostgreSQL anteriores,
requiere Docker y no prueba una integración M3/M5 nueva. Registrar salidas y conteos
reales antes de aprobar el incremento. M6 no dispone aún de evaluador productivo.
