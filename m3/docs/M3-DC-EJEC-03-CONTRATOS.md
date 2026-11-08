# DC-EJEC-03: componentes implementados y decisiones pendientes

Base verificada: `1d26f7d120bc7d121c996db297872bd40fbe103f`, con PR #58 integrado.
Solo se modifica M3. No se habilita autorización institucional ni publicación.

## Rúbrica: estructura y semántica

BD-005.2 y los contratos locales respaldan UUID y referencias de caso/rúbrica/
pregunta, una rúbrica por caso, identidades de asociación y pregunta únicas,
pertenencia de la pregunta al mismo caso y valores positivos exactamente
representables en NUMERIC(8,2). No determinan cobertura ni fórmula de evaluación.

La persistencia conserva `RubricaPregunta.idPregunta`, `valorPregunta` decimal
exacto y `criterio` nullable, así como `Rubrica.puntajeTotal` nullable y
`definicionEvaluacion` JSONB opaco. PostgreSQL conserva el significado JSON, no
su espaciado original. No se calcula un total ni se interpreta ese JSON.
Ingrid debe consumir estos atributos como estructura; no equivalen a
`CriterioRubrica` ni constituyen una estrategia de evaluación M6 aprobada.

Defecto técnico reproducido y corregido: un snapshot con RubricaPregunta sin
Rubrica se reconstruía sin esas asociaciones. El mapper ahora rechaza el snapshot
antes de reconstruir el agregado, sin entregar datos parciales. No cambia SQL,
la política de completitud ni las representaciones válidas.

`CasoValidation.assertRubricComplete` sigue exigiendo una rúbrica no nula cuyo
`isComplete()` sea verdadero. Esa función exige criterios legacy no vacíos.
La persistencia los rechaza por carecer de equivalencia SQL aprobada y reconstruye
esa lista vacía. La publicación persistente permanece en `INCOMPLETE_RUBRIC`.
Referencias: M3-INC-13A y M3-INC-13B, incluida la matriz de atributos y DD-03.

Dirección Central debe escoger y ratificar expresamente una solución:

1. Mantener criterios legacy como fuente de completitud: definir su representación
   persistente sin pérdidas y la equivalencia formal de identidad/descripción/
   puntaje, con evaluación de compatibilidad de datos y esquema. No existe hoy
   ese mapeo; este incremento no crea tablas ni modifica migraciones.
2. Definir completitud sobre la estructura persistida: proporcionar el predicado
   académico exacto y la política de compatibilidad con casos legacy. UUID/FK y
   positividad por sí solos no autorizan publicar. No se propone un mínimo de
   preguntas, suma de valores, escala, cobertura ni equivalencia no ratificados.

## Composición ETag que necesita Cristian

La documentación disponible mantiene transporte y códigos HTTP pendientes.
Se implementa un codec candidato, **sin @Component ni @Bean productivo**:

- `CasoEtagConcurrencyContract extends CasoPatchConcurrencyContract` añade
  `String etag(String fingerprint)` y conserva
  `String expectedFingerprint(HttpHeaders headers)`.
- `CasoStrongEtagContract` representa una única huella `m3-v2:<64 hex minúsculas>`
  entre comillas en ETag/If-Match. Rechaza débiles, comodín, listas, duplicados,
  versiones distintas y valores ausentes/malformados. Solo admite SP/HTAB externos.
  Es un subconjunto candidato; no se declara una implementación general de todas
  las formas If-Match ni se activa para consumidores institucionales.
- `CasoDocenteConsultaService.consultarConVersion(String)` devuelve
  `CasoDocenteFichaVersionada(ficha, fingerprint)` después de identidad, permiso,
  propiedad y visibilidad. Usa `CasoUpdatePort.findById(UUID)` exactamente una
  vez. Ficha y huella proceden del mismo snapshot consistente; no se relee el
  repositorio ni se calcula una huella del DTO.
- El controlador usa ese recorrido solo si se suministra un
  `CasoEtagConcurrencyContract`. Sin él conserva el GET previo sin ETag. Requiere
  también el `CasoUpdatePort` existente; si falta, falla cerrado sin otra lectura.
  El cuerpo mantiene sus cuatro campos. La respuesta autorizada usa no-store.
- PATCH entrega If-Match decodificado al mismo servicio protegido y al mismo
  update transaccional con bloqueo y comparación m3-v2. No cambia el SQL escritor,
  los guards ni las reglas de bloqueo M4. La huella nunca concede autorización.

Antes de registrar el codec, Cristian y Dirección Central deben ratificar:

- El subconjunto de validadores admitido y la compatibilidad con consumidores.
- Los códigos para ausencia/formato/precondición obsoleta. Las pruebas candidatas
  conservan los 400/409 actuales; no introducen 428/412 silenciosamente.
- Transporte ETag de GET, ausencia de ETag nuevo en PATCH 204 (reconsultar GET)
  y configuración CORS de APP: permitir If-Match y exponer ETag a clientes web.
- Proveedores reales de lectura/visibilidad y modificación, estados editables e
  identidad M1 vigente. Se conservan propiedad y denegación por defecto; no se
  inventan permisos ni se modifica APP/M1.

X-Test-Fingerprint sigue limitado a las pruebas históricas. Los positivos nuevos
usan el codec candidato explícito y autorizadores controlados: demuestran el
recorrido técnico GET → ETag → If-Match → persistencia, no operación institucional.

## Verificación reproducible

La prueba de descarte de asociación falla antes de la corrección y pasa después.
Logs locales ignorados en `m3/target/dc03-*.log`, sin staging ni publicación.

```powershell
.\mvnw.cmd -B -o -pl m3 -am '-Dtest=CasoEtagControllerTest,CasoPersistenceMapperIntegrityTest,RubricaStructureTest,CasoGestionControllerTest,CasoDocenteConsultaServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -B -o -pl m3 -am -Pdocker-it '-Dit.test=CasoPersistenceAdapterIT,RubricaJpaReadIT,CasoCommandUpdateIT,CasoJpaAggregateTransactionIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Se conservan las pruebas previas del PR #58; la regresión incluye publicación
negativa, integridad de rúbricas, creación, GET/PATCH, cambios invisibles al DTO,
concurrencia, rollback y el protocolo de bloqueo por programación M4.

Resultados del 2026-11-08: 78 pruebas focalizadas aprobadas; regresión completa
de 214 unitarias/MVC y 63 integraciones PostgreSQL 16.15 aprobadas, sin fallos,
errores ni omisiones, BUILD SUCCESS. Los 63 casos se distribuyen entre
CasoPersistenceAdapterIT (47), CasoCommandUpdateIT (13),
CasoJpaAggregateTransactionIT (2) y RubricaJpaReadIT (1).
