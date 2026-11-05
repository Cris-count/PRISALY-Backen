# Informe de avance académico — M3: Gestión de Casos Académicos

Responsable: Integrante 2. Fecha de preparación: 4 de noviembre de 2026.
Tipo de entrega: avance verificable, no certificación de módulo terminado.
DC-INT-01/02, GET/PATCH e integración quedan pausados por instrucción del responsable.

## Objetivo y alcance

M3 representa y gestiona casos académicos con escenarios, preguntas, opciones y
rúbricas, preservando su identidad, integridad y protección frente a modificaciones
de contenido después de la programación académica.

El alcance entregable reúne dominio, creación de borradores persistidos, lectura
interna del agregado, comandos parciales autorizados y el POST de creación.
No incluye un recorrido docente completo hasta publicación ni CRUD REST completo.
La [matriz de cumplimiento](MATRIZ-CUMPLIMIENTO-M3.md) distingue logros técnicos
de cumplimiento normativo pendiente de cotejo con el catálogo oficial.

## Arquitectura implementada

Rutas relativas a `m3/src/main/java/com/prisaly/m3/`:

| Capa | Componentes y responsabilidades |
| --- | --- |
| Dominio | Records Caso, Escenario, Pregunta, OpcionRespuesta, Rubrica, RubricaPregunta y CriterioRubrica; CasoBuilder; reglas de validación, publicación y bloqueo |
| Aplicación | CasoApplicationService; puertos de repositorio, comandos, publicación e identidad/autorización; orquestación sin dependencias directas de M1 |
| Infraestructura | Seis entidades JPA @Immutable; lector consistente; mapper; inserción SQL parametrizada y comandos sobre la conexión transaccional Hibernate |
| Presentación | CasoCreationController, DTO específicos y manejo de errores de POST /api/v1/casos |

Las FK se representan mediante UUID escalares en JPA. BigDecimal conserva los
NUMERIC(8,2); JSONB se lee como texto de documento y se inserta mediante cast SQL,
sin convertirlo en una cadena JSON serializada dos veces. La huella m3-v2 aplica
canonicalización semántica al JSONB sin definir su significado académico.

El ensamblaje institucional existe en app y es una dependencia externa al paquete
fuente M3. El conflicto de ensamblaje de develop no se declara resuelto por esta
entrega: ver [preparación DC-INT-02](../DC-INT-02-PREPARACION.md).

## Funcionalidades entregables

| Funcionalidad | Alcance implementado y acreditación | Límite |
| --- | --- | --- |
| Creación de borradores | Builder, autorización de creación, inserción y propagación de errores; pruebas unitarias y PostgreSQL | Solo representaciones SQL admitidas; sin equivalencias legadas inventadas |
| POST de creación | Identidad institucional, propietario obtenido del servidor, DTO estricto y commit antes de 201; evidencias Bruno/Newman | No implica disponibilidad garantizada del ensamblaje conflictivo de develop |
| Lectura interna | Recuperación de seis tablas, nulos, metadatos, UUID, decimales y órdenes físicos | No es GET REST autorizado |
| Inserción del agregado | SQL parametrizado, restricciones oficiales y rollback completo | No habilita update(Caso) |
| Actualización parcial interna | Campos explícitos; omitir conserva; null no elimina filas; pertenencia y huella | Autorización institucional y PATCH REST pendientes |
| Altas académicas | Escenario, pregunta y opción sobre padres existentes en BORRADOR | No hay altas dependientes de elementos recién creados dentro del mismo lote |
| Eliminación explícita | EliminarOpcionCommand, sujeto a FK y F3-03 | No equivale a eliminación general de todos los tipos de elemento |
| Consistencia y protección | Bloqueo raíz, triggers oficiales, huella, transacciones y pruebas concurrentes | F3-03 no constituye versionado histórico; requiere triggers y privilegios adecuados |
| Metadatos y disponibilidad | Conservación de textos/fechas/actividad; disponibilidad PUBLICADO y activo | No introduce transiciones de archivado ni programación M4 |

Las decisiones y detalles originales se referencian, sin duplicarlos:
[10B](../M3-INC-10B.md), [10D](../M3-INC-10D.md), [11](../M3-INC-11.md),
[12](../M3-INC-12.md), [13A](../M3-INC-13A.md) y
[REST](../DC-M3-REST-03.md). Sus estados históricos se interpretan junto con
los registros posteriores de ejecución.

## Resultados reales disponibles, separados por corrida

| Evidencia | Fecha y herramienta | Resultado | Procedencia y límites |
| --- | --- | --- | --- |
| Unitarias M3 | 2026-11-04; Maven, ejecución manual del Integrante 2 | 132 ejecutadas, 0 fallos, 0 errores, 0 omitidas en XML | Confirmación corroborada con diez XML; CasoPublicationContractTest: 9 incluidas; sin SHA verificado de esa corrida |
| Integración PostgreSQL | 2026-11-04; Maven, Testcontainers y PostgreSQL 16.15 | 71 ejecutadas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS reportado por el ejecutor | Siete XML y resumen Failsafe coinciden; CasoPersistenceAdapterIT: 25 incluidas; no se aportó log completo de BUILD SUCCESS |
| Colección Postman vía Newman | 2026-11-04, 16:12:22–16:12:34 UTC-05; Newman 6.2.1, Node 22.14.0 | 13 solicitudes aprobadas, 39/39 aserciones, 0 fallidas y 0 bloqueadas | HTTP real en entorno técnico local; versión exacta del backend no identificada; no son capturas Desktop |
| Bruno histórico | 2026-11-04; Bruno CLI 4.3.0 | 13 solicitudes aprobadas, 14 tests, 27 aserciones | Corrida separada con fixtures locales; no se suma a Newman ni a Maven |

Registro Maven: [M3-INC-13B](../M3-INC-13B.md), sección de resultados reportados
por Integrante 2. Registro HTTP:
[EVIDENCIA-POSTMAN](../../../bruno/m3-gestion-casos/EVIDENCIA-POSTMAN.md).
Bruno y sus archivos se localizan en [colección M3](../../../bruno/m3-gestion-casos/README.md).

Se localizaron dos avisos Hikari en XML de pruebas explícitas de agotamiento del
pool, ambas aprobadas; se conservan depurados en evidencias/metadatos/avisos-hikari.json.
Otros avisos PostgreSQL referidos por el responsable siguen sin contexto completo.
No se clasifican automáticamente como defectos ni se declaran corregidos.
Los resultados antiguos de 131/77 y los de app corresponden
a otras selecciones: no sustituyen ni se agregan a 132/71.

Las dos pruebas app ajustadas posteriormente por DC-INT-02 están PREPARADAS,
NO EJECUTADAS. Los resultados anteriores no acreditan esos ajustes.

La consolidación posterior conserva copias depuradas permanentes de los XML,
el reporte seguro Newman y un resumen derivado Bruno en [evidencias/](evidencias/README.md).
Se excluyeron cuatro XML Failsafe anteriores para no sumarlos a la selección 71.
Las fechas LastWriteTime son metadatos de archivo: los XML no incluyen timestamp
de suite y no prueban por sí solos una hora exacta de ejecución o versión del código.
Procedencias y hashes de originales/derivados permiten verificar la conservación,
sin atribuir a las corridas el HEAD actual del paquete.

## Funcionalidades parciales y bloqueos

- GET y PATCH: rutas aprobadas, sin frontera REST habilitada; faltan política
  institucional, proyección/DTO y respuestas conciliadas. Ver
  [conciliación GET/PATCH](../DC-M3-REST-GET-PATCH.md). Trabajo pausado.
- Publicación persistente: infraestructura con bloqueo, autorización, huella y
  validación. La rúbrica SQL no reconstruye criterios legados y la regla vigente
  exige criterios no vacíos. DD-03 debe ratificar completitud/cobertura. No existe
  evidencia positiva de publicación persistente; tampoco de rollback posterior a
  su UPDATE exitosamente alcanzado. Ver [13B](../M3-INC-13B.md).
- Modificación y publicación institucionales: no ratificadas; no se derivan de
  CREAR_CASO. No se concede acceso a casos ajenos por ser usuario autenticado.
- DC-INT-02: parche de retiro de configuración antigua y regresiones preparados,
  no aplicados/integrados ni ejecutados. No se elimina un archivo ausente de la rama.
- Copia: copia profunda en memoria con nuevas identidades; copia persistida y
  política de fechas pendientes. Archivado: representado y protegido, sin
  transiciones, restauración ni reactivación aprobadas.
- Capturas Postman Desktop: no disponibles; no se fabrican. Newman acredita la
  ejecución automatizada, no interacción con la GUI.

## Conclusión del avance

El avance acredita creación persistente y un subconjunto significativo de gestión
interna con pruebas de integridad y concurrencia. No acredita módulo completo,
integración completa con develop ni publicación académica exitosa.

No se calcula porcentaje: no está disponible una matriz oficial integral de
requisitos M3 con criterios de aceptación y ponderación. En particular, no puede
declararse demostrado un avance igual o superior al 40 % contando clases, tests
o filas de este inventario. La [guía de entrega](GUIA-ENTREGA-M3.md) identifica
los archivos y las comprobaciones previas pendientes del responsable.
