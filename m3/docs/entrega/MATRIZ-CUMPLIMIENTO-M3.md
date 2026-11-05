# Matriz de cumplimiento del avance M3

Fecha: 4 de noviembre de 2026. Responsable: Integrante 2.

## Base de clasificación

No se dispone del catálogo integral V27, de los casos de uso asignados ni de una
matriz oficial de aceptación/ponderación. La limitación ya consta en
[M3-INC-10D](../M3-INC-10D.md). BD-005.2 y sus migraciones describen estructura
e integridad, pero no sustituyen esa matriz funcional.

Solo se utilizan identificadores respaldados por documentación disponible:
F3-03, RF-09 y las resoluciones/incrementos existentes. «Identificador no
disponible» no es un requisito inventado; identifica una funcionalidad cuyo
vínculo con el catálogo oficial debe confirmar Dirección Técnica.

COMPLETO se refiere al alcance técnico expresado en la fila, no al módulo ni a
un caso de uso oficial no disponible. PARCIAL indica un recorrido incompleto;
PENDIENTE, una parte no habilitada; NO VERIFICABLE, falta de base documental.

## Matriz

| Requisito oficial o fuente disponible | Implementación | Evidencia | Estado | Pendiente concreto |
| --- | --- | --- | --- | --- |
| DC-M3-REST-02/03: crear borrador por POST | CasoCreationController, DTO, CasoApplicationService.createDraft e insert | Trece solicitudes Newman/Bruno; pruebas de creación y persistencia | COMPLETO en el alcance POST validado | Capturas GUI si son obligatorias; identificar artefacto de cada nueva corrida; no certifica develop conflictivo |
| BD-005.2: representar y persistir seis tablas | Records, seis entidades de lectura, mapper e inserción SQL | CasoPersistenceAdapterIT, CasoJpaReadIT, EvaluableContentJpaReadIT, RubricaJpaReadIT | COMPLETO para el subconjunto SQL admitido | No confundir con persistencia de criterios legados ni de Escenario.descripcion |
| Consulta interna; identificador funcional oficial no disponible | CasoRepositoryPort.findById y lector REQUIRES_NEW/REPEATABLE_READ | CasoJpaAggregateReadIT, CasoJpaAggregateTransactionIT y round trips | COMPLETO como consulta interna | No concede visibilidad REST ni lectura institucional |
| INC-10B: actualización interna explícita | CasoUpdatePort, comandos parciales y CasoCommandUpdateAdapter | CasoUpdateContractTest y CasoCommandUpdateIT | COMPLETO para los comandos existentes | No equivale a PATCH autorizado ni a edición de todos los campos |
| INC-10D: alta de escenario BORRADOR | AgregarEscenarioCommand | CasoCommandAddContentIT | COMPLETO en alcance interno | Padre existente; sin altas dependientes en el mismo lote |
| INC-11: alta de pregunta | AgregarPreguntaCommand | CasoCommandAddContentIT | COMPLETO en alcance interno | No exige completitud académica para borradores |
| INC-12: alta de opción | AgregarOpcionRespuestaCommand | CasoCommandAddContentIT | COMPLETO en alcance interno | Restricciones de orden, unicidad y corrección SQL vigentes |
| INC-10B/12: conservación y eliminación explícita | Omitir no cambia; null no elimina; EliminarOpcionCommand | CasoCommandUpdateIT y CasoCommandConcurrencyIT | COMPLETO para eliminación de opción existente | No hay eliminación general de escenarios/preguntas/rúbricas |
| F3-03: protección desde programación | Reglas M3, bloqueo raíz y cooperación con V007–V009 oficiales | IT de comandos, escritores directos, programación y referencias históricas | COMPLETO en las garantías internas probadas | Triggers activos y privilegios adecuados; sin certificación de integración M4 ni snapshots históricos |
| RF-09: disponibilidad de programación | isAvailableForScheduling exige PUBLICADO y activo; alineación con V009 | CasoMetadataTest y pruebas SQL de programación | COMPLETO como predicado M3 | No implementa programación M4; cotejo integral del requisito pendiente de catálogo |
| INC-10B.1: huella m3-v2 | Canonicalización de seis tablas, JSONB y decimales | CasoUpdateContractTest, JsonbCanonicalizerTest y PostgreSQL | COMPLETO en el alcance probado | No es autorización ni versionado histórico |
| Publicación; DD-03 pendiente | Regla pura y adaptador específico REQUIRED | Pruebas de dominio; rechazo, huella, F3-03 y rollback por rechazo en CasoPersistenceAdapterIT | PARCIAL | Completitud institucional, publicación persistente positiva y rollback tras UPDATE no demostrados |
| GET /api/v1/casos/{idCaso}, ruta aprobada por instrucción de DT | Lectura interna disponible, sin controlador habilitado | Conciliación documental, sin ejecución GET válida | PENDIENTE | Permiso/visibilidad, proyección y errores ratificados; trabajo pausado |
| PATCH /api/v1/casos/{idCaso}, ruta aprobada por instrucción de DT | Comandos internos disponibles, sin controlador habilitado | Cobertura interna; sin ejecución HTTP PATCH | PENDIENTE | Política institucional, DTO, transporte de huella y HTTP; trabajo pausado |
| DC-INT-02: proveedor único en ensamblaje conciliado | Adaptador real existente; parche y tests de corrección preparados | DC-INT-02-PREPARACION.md; tests app todavía no ejecutados | PARCIAL | Corrección pausada, no integrada, sin validación de estado combinado |
| Copia independiente; identificador oficial no disponible | Copia profunda transitoria con identidades nuevas y remapeo | CasoApplicationServiceTest y RubricaStructureTest | PARCIAL | Persistencia y fechas de copia requieren política |
| Archivado; identificador oficial no disponible | Enum ARCHIVADO y rechazo preventivo | CasoMetadataTest y contratos | PARCIAL | Sin transiciones, restauración o reactivación autorizadas |
| Gestión completa de rúbrica; identificador oficial no disponible | Estructura, lectura e inserción de rúbrica/asociaciones | RubricaStructureTest e IT | PARCIAL | No hay comandos de gestión completa; no se inventa completitud DD-03 |
| Total de requisitos M3 y umbral del 40 % | Inventario técnico disponible | No hay matriz oficial integral ni pesos | NO VERIFICABLE | Obtener catálogo asignado, aceptación, pesos y validación de DT |

## Interpretación de pruebas y porcentaje

Los resultados 132 unitarias / 71 integraciones fueron reportados por Integrante 2
y posteriormente corroborados con diez XML Surefire y siete XML Failsafe, más
el resumen Failsafe de 71. Copias depuradas y procedencia: [evidencias](evidencias/README.md).
No se acredita el SHA de ejecución ni se suman los cuatro XML anteriores excluidos.
Las clases por fila identifican cobertura existente; no atribuyen
un conteo individual no comunicado. Newman 13/13 y 39 aserciones acredita POST;
Bruno tiene su corrida histórica separada. No se suman ejecuciones ni se consideran
como cumplimiento de GET, PATCH o publicación persistente exitosa.

Porcentaje de avance: **NO CALCULABLE con la documentación disponible**.
No se ponderan las filas de este inventario por igual ni se interpreta su número
como denominador oficial. Para acreditar 40 % se requiere una matriz ratificada
de requisitos asignados y evidencia de aceptación de cada elemento, con pesos
solo si están establecidos. No se declara alcanzado ese umbral.

Las fechas, limitaciones de versión, avisos y registros originales se consolidan
en [INFORME-AVANCE-M3](INFORME-AVANCE-M3.md), sin repetir pruebas ni resultados.
