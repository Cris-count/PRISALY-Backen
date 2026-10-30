# Flyway V001–V009 — blocker cleared (Fase 0)

## Status (2026-10-30)

**Localization blocker resolved.** Authorized SQL from `PRISALY_M1_V001_V009_VERIFICADAS.zip` (source lineage: `PRISALY_INCREMENTO_1.zip`, identical to `PRISALY_CIERRE_TECNICO_FINAL.zip`) is in the canonical path:

`app/src/main/resources/db/migration/`

No other Maven resource tree contained V001–V009 before or after restore (single canonical location).

## V009 caveat

`PRISALY_CIERRE_BD_V008_V009_PREPARADO.zip` carries a **different** V009 (alternate RF-09). It must not be mixed with this sequence. The files in this repo are the Incremento 1 / cierre técnico final set (`V009__rf09_solo_casos_publicados.sql`). Confirm with the database owner if a later authorized switch is required.

## Execution evidence

See `docs/M1-FLYWAY-APPLY.md`.
