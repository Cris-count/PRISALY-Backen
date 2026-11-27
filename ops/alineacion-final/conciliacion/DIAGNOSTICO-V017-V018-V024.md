# Conciliación V017 / V018 / V024 — diagnóstico y ensayo aislado (DC-ALINEACION-INTEGRAL-02)

Ensayo en PostgreSQL 16.15 desechable (contenedor sin volumen, eliminado al terminar) con la imagen del Backend validada (código `b23c217`). La base estable `prisaly-dc105-local` no se tocó: ni repair, ni clean, ni cambios de historial.

## Resultado

| Ensayo | Configuración | Resultado |
|---|---|---|
| 1 | Cadena estable (`db/migration`, `dd02b`, `m6`, `d05`, `dc92`), contrato `bd0052-v013-integral-dc92` | Historial `001–009, 013–016, 019–023`, idéntico al QA. Backend arranca |
| 2 | Ensayo 1 + `db/sim-propuesta` (V017) + `db/sim-propuesta-b` (V018) | **Flyway rechaza**: `Detected resolved migration not applied to database: 017` y `018` (`outOfOrder=false`). Historial intacto |
| 3 | Ensayo 1 + `db/dc116-propuesta` (V024) + candidato `V025`/`V026` (= V017/V018 sin cambios, SHA-256 abajo) | **Flyway aplica V024, V025 y V026** sin errores (0,087 s). **El guard M4 detiene el arranque**: `M4-ESQUEMA-PENDIENTE: identidad incompatible; adicionales=[EdificioIntento, FaseIntento, GuionNarrativoCaso, NotaIntento, PistaIntento, ZonaIntento]` |

## Causa exacta (tres barreras independientes)

1. **Orden Flyway.** V017/V018 son menores que V019–V023 ya aplicadas; con `outOfOrder=false` Flyway invalida el arranque. Renumerar a V025/V026 resuelve **solo** esta barrera.
2. **Guard de esquema M4** (`M4SchemaContract.verificar`). Cada contrato fija el conjunto **exacto** de tablas, el SHA-256 de la estructura (columnas, claves, índices, triggers) y el SHA-256 del historial Flyway. Ningún contrato existente combina `integral-dc92` con las tablas del simulador ni con las de DC-116. Por eso V024 (investigación persistida) tampoco puede activarse hoy en el QA.
3. **Permisos de ensamblaje.** `EsquemaFasesIntentoConfig.PERMITIDOS` solo admite `bd0052-v013-sim-ab` y `bd0052-v013-dd02b-sim-ab`, que no incluyen D-05, N2 (M6) ni DC-92. La narrativa M3 exige igualmente los contratos `sim-a/sim-ab`.

## Compatibilidad semántica comprobada en la base aislada

- Las funciones PL/pgSQL de V025/V026 (que PostgreSQL no valida al crearlas) usan columnas y objetos presentes tras V023: `Intento.estado` (CHECK `EnCurso`/`Finalizado`), `Intento.idParticipacion`, `Caso.fechaBloqueoAcademico` y la función `prisaly_caso_participacion`.
- No hay colisión de nombres de tablas, índices, funciones ni triggers con V019–V024. Triggers creados: `tr_guion_bloqueo_sim`, `tr_fase_intento_sim`, `tr_fase_intento_no_truncate_sim`. Los triggers previos de `Intento` (`tr_intento_no_reabrir`, `tr_intento_identidad_inmutable`, `tr_intento_no_delete`, `tr_intento_autorizacion`, `tr_autorizacion_consumo_f302`) se mantienen.
- **Observación para DC (no se corrige):** las tablas de V024 no tienen triggers de protección. Nada en el esquema impide añadir notas o pistas a un intento ya finalizado. La regla A1 protege solo respuestas; si se quiere lo mismo para la exploración, es una regla nueva que hay que decidir.

## Qué haría falta (no ejecutado: requiere decisión de DC)

1. Publicar el candidato como migraciones nuevas (`V025__sim_guion_narrativo.sql`, `V026__sim_fase_intento.sql`), sin tocar V017/V018 (las usan bases QA históricas).
2. Añadir a `M4SchemaContract` un contrato `bd0052-v013-integral-dc92-dc116-sim` con sus tablas y su `.properties` (huellas de estructura e historial calculadas sobre la base del ensayo 3).
3. Admitir ese contrato en `EsquemaFasesIntentoConfig`, en la narrativa M3 y en `EsquemaAccesoEstudianteConfig`.
4. Pruebas focalizadas del guard y del ensamblaje y, después, ensayo sobre una copia de la base QA antes de tocar cualquier base estable.

## Archivos

- `candidato-migraciones/V025__sim_guion_narrativo.sql` — sha256 `3a5c7c6f542bf55935d93de608837aaca2e32b2a7af2b6a4f31df3214f73cf1f`.
- `candidato-migraciones/V026__sim_fase_intento.sql` — sha256 `7c62950801ef57553d10a571805850de8c776622bc58dfc5a27ab26f9116b66d`.
- `ensayo-aislado.sh` — arranque de la imagen contra la base desechable `alin02-pg` (uso: `run <dir> <nombre> <locations> <contrato> [montaje]`).

Estas migraciones están fuera del classpath (`ops/`): no se aplican por accidente.
