# PRISALY — DIRECCIÓN CENTRAL 4.0
## DC-PR69-ACEP-FINAL-01 — ACTA DE ACEPTACIÓN TÉCNICA GLOBAL

- **Fecha:** 8 de noviembre de 2026
- **Repositorio:** Cris-count/PRISALY-Backen
- **PR evaluado:** #69
- **SHA del incremento:** `37741db4910d23e38d5eeca67a957f7e2ad6ea9f`
- **SHA de integración:** `b97ba887629b6229fdd9676877e31049be4c3461`

Fuente: resolución de Dirección Central suministrada por el usuario en esta conversación.

### I. Antecedentes

Dirección Central ordenó una revisión técnica posterior a la integración del PR #69
debido a la ausencia de revisiones formales previas a su fusión.

Se recibieron dictámenes favorables con observaciones de:

- Cristian: seguridad e integración institucional.
- Mariana: compatibilidad contractual y académica.
- Nicolás: persistencia, integridad y transacciones.
- Ingrid: implementación M5/M6 y evidencias de pruebas.

### II. Alcance aceptado

Se acepta técnicamente el incremento M5/M6 del PR #69, limitado a sus 16 archivos
y a los comportamientos inspeccionados:

1. Robustez de referencias de preguntas y opciones.
2. Canonicalización de identificadores UUID.
3. Protección de lectura y relaciones intento–participación.
4. Tratamiento controlado de errores de persistencia.
5. Conservación de integridad y rollback.
6. Compatibilidad con D-06 en los escenarios comprobados.
7. Compatibilidad de contratos académicos M3–M6.
8. Ausencia de modificaciones productivas no autorizadas en M6.

### III. Observaciones consolidadas

**OBS-C69-01:** se conserva el riesgo de clasificación demasiado amplia de
`DataIntegrityViolationException` como HTTP 409. No se aprueba universalmente esa
traducción. Cualquier caso causal reproducible nuevo se tramitará como incidencia independiente.

**OBS-C69-02:** cerrada para el alcance de composición y controles institucionales
negativos demostrado.

**N69-01 / OBS-M69-01:** consolidadas como una sola observación sobre vínculos
huérfanos representados como ausencia mediante INNER JOIN. Riesgo bajo, no
bloqueante. La integridad referencial continúa dependiendo de las restricciones
de persistencia correspondientes.

No se ordenan cambios preventivos ni PR correctivos sin defecto reproducible.

### IV. Evidencias

Se reconocen las campañas reportadas:

| Campaña | Aprobadas | Omitidas |
|---|---|---|
| General | 1.150 | 18, identificadas en suites M4 |
| Nicolás | 38 PostgreSQL y 21 unitarias/MVC | 0 en su selección |
| Mariana | 11 PostgreSQL y 63 unitarias/MVC | Según su evidencia de campaña |

No se suman las campañas como pruebas únicas ni se consideran aprobadas las omisiones.

Trazabilidad de Nicolás:

- [Revisión registrada](https://github.com/Cris-count/PRISALY-Backen/pull/69#pullrequestreview-5819850237).
- [Ejecución PostgreSQL/D-06](https://github.com/Cris-count/PRISALY-Backen/actions/runs/42527556523).
- [Dictamen y alcance](DC-REV-PR69-NICOLAS.md).
- [Resumen de pruebas](evidencias/DC-REV-PR69-NICOLAS/summary.json).

### V. Límites de la aceptación

Esta resolución no acredita:

- Habilitación productiva DD-02.
- Cálculo académico definitivo DD-03.
- Persistencia de calificaciones no implementada.
- Activación de D07.
- Cierre completo de M1–M7.
- Aprobación de V011/V012.
- Ausencia de defectos fuera del incremento inspeccionado.

Se conserva DD-04 conforme al criterio histórico del último intento autorizado y evaluado.

### VI. Decisión

**DICTAMEN GLOBAL: ACEPTADO CON OBSERVACIONES NO BLOQUEANTES.**

Se declara concluida la revisión cruzada DC-REV-PR69-01 / DC-PR69-REV-02.
No se requieren nuevas auditorías generales, pruebas repetidas, cambios
productivos preventivos ni nuevos PR derivados exclusivamente de esta revisión.
Solo se abrirán incidencias nuevas ante defectos causales reproducibles.

### VII. Responsabilidades

Cristian, Mariana, Nicolás e Ingrid quedan liberados de nuevas actuaciones
ordinarias de revisión sobre el PR #69.

Dirección Central conservará esta acta como referencia de trazabilidad y
continuará con los pendientes funcionales e institucionales mediante órdenes separadas.

**DIRECCIÓN CENTRAL 4.0 — PRISALY**

Este registro documental se publica por solicitud expresa posterior del usuario;
no añade implementación, nueva campaña ni amplía la aceptación a otro SHA.
