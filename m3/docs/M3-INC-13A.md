# M3-INC-13A — Creación persistente de borradores

Estado: implementación preparada; pruebas pendientes de ejecución por Integrante 2.

## Flujo y contratos

`CasoApplicationService` recibe por constructor `CaseAuthorizationPort` y
`CasoRepositoryPort`. `createDraft()` ejecuta, en orden: `buildDraft()`, autorización
de modificación, una única llamada a `insert(draft)` y retorno del mismo objeto.
No captura errores de persistencia, no reintenta y no relee el agregado.
Los consumidores del constructor anterior deben proporcionar el repositorio.
No se incorpora un repositorio ficticio ni un constructor alternativo sin persistencia.

El adaptador existente inserta las seis tablas con SQL parametrizado y una transacción
REQUIRED, utilizando la conexión asociada al EntityManager. Si participa en una
transacción externa, el commit definitivo corresponde al llamador. Un fallo no debe
tratarse como éxito ni capturarse para continuar esa transacción.

## Archivos afectados

- `src/main/java/com/prisaly/m3/application/service/CasoApplicationService.java`:
  inyección del puerto e inserción del borrador autorizado.
- `src/test/java/com/prisaly/m3/CasoApplicationServiceTest.java`: adaptación del
  constructor y cuatro pruebas de flujo, orden, rechazo y propagación de errores.
- `src/test/java/com/prisaly/m3/CasoMetadataTest.java` y `RubricaStructureTest.java`:
  adaptación del constructor con repositorio simulado; reglas conservadas.
- `src/test/java/com/prisaly/m3/infrastructure/CasoPersistenceAdapterIT.java`:
  seis pruebas del recorrido de creación por servicio con el adaptador real.
- `docs/M3-INC-13A.md`: este registro.

## Restricciones de persistencia

Se conservan sin cambios las validaciones de CasoPersistenceMapper y del adaptador:

- UUID explícitos válidos para raíz, docente y elementos internos.
- Órdenes físicos explícitos; PostgreSQL mantiene FK, UNIQUE y CHECK oficiales.
- `Escenario.descripcion` legado no tiene equivalencia SQL aprobada: se rechaza.
- Una rúbrica debe tener estructura explícita y criterios legados vacíos. No se
  convierte RubricaPregunta a CriterioRubrica ni se descartan criterios.
- Las referencias de rúbrica deben pertenecer al caso.
- No se importa bloqueo académico mediante INSERT; permanece en los triggers.
- Fechas explícitas con precisión máxima de microsegundos; no se redondean.
- Fechas de creación omitidas usan DEFAULT now() oficial. El objeto retornado
  permanece inmutable y puede conservar null donde PostgreSQL asignó una fecha.
- JSON inválido, FK o unicidad pueden provocar errores SQL: se propagan y la
  transacción de inserción revierte. JSONB conserva semántica, no formato textual.

El Builder sigue permitiendo representaciones legadas que no son persistibles.
Se documenta esa limitación sin cambiar modelos, validaciones ni equivalencias.

## Pruebas preparadas

Unitarias: construcción antes de autorización, inserción exactamente una vez del
mismo objeto, UUID conservado, autorización denegada sin inserción, error del Builder
sin autorización/inserción y propagación de la misma excepción sin reintento.

PostgreSQL 16.15, V001–V009 oficiales, usando el arnés existente:

1. Crear y recuperar después del commit las seis tablas, con UUID, textos, fechas,
   actividad, órdenes no consecutivos, referencias, BigDecimal y JSONB semántico.
2. DEFAULT de fecha y visibilidad independiente: retorno sin relectura; recuperación
   después del commit de la transacción externa.
3. Rechazo FK y UUID duplicado sin modificar el caso existente.
4. Rollback de raíz e hijos ante JSON inválido en la rúbrica.
5. Rollback de la inserción por decisión de la transacción externa.
6. Rechazo de rúbrica legada sin descarte de sus criterios.

Las pruebas anteriores de persistencia, concurrencia y altas de escenarios,
preguntas y opciones se conservan. No hay resultados nuevos de ejecución.

## Límites y compatibilidad

Publicación persistida bloqueada y fuera de este incremento: isComplete() sigue
dependiendo de criterios legados sin conciliación aprobada con SQL. No se habilita
update(Caso), REST, copia persistida ni integración M1. La autorización de las pruebas
es una fixture local, no evidencia de permisos institucionales.

La disponibilidad para M4 permanece `isPublished() && activo`. Un borrador creado
no está disponible para programación. No se alteran F3-03, huella m3-v2, bloqueos,
triggers, migraciones, entidades ni lectores.

## Validación manual

Desde PRISALY-Backen (no ejecutado por el agente):

```powershell
mvn -pl m3 -am test
mvn -pl m3 -am -Pdocker-it verify
```

El segundo comando requiere Docker disponible y ejecuta la regresión de integración,
incluidas las pruebas existentes de INC-10D, INC-11 e INC-12. Registrar resultados,
conteos, entorno y errores reales antes de declarar aprobado M3-INC-13A.
