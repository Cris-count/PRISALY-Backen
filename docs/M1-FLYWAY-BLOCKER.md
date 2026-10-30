# Blocker: historical Flyway migrations V001–V009

## Fase 0 inspection (2026-10-30)

This blocker is about the **current Spring Boot repository**, not the whole PRISALY project history. The team reports V001–V009 existed in historical packages (Increment 1 / cierre técnico). They are still missing from this machine’s searchable locations.

### Searched

| Location | Result |
| --- | --- |
| `PRISALY-Backend/app/src/main/resources/db/migration` | only `.gitkeep` |
| Workspace `PRISALY` | no `V001*.sql`…`V009*.sql`, no `PRISALY_INCREMENTO_*.zip`, no `PRISALY_CIERRE_TECNICO_*.zip` |
| `Downloads` (PRISALY* files) | UML/OCL/pptx/dbml and `PRISALY_Entrega_Integrante_3_Parcial.zip` — **no Flyway SQL** |
| GitHub `Cris-count/PRISALY-Backen` branches | no V001–V009; M4 notes their absence |
| Filenames of record | `V001__bd0052_estructura.sql` … `V009__rf09_caso_publicado.sql` |

### Policy

Restore **only** from an authorized package, without rewriting content and without substitute files named V001–V009.

### What is needed from the team

Provide one of:

- `PRISALY_INCREMENTO_1.zip`
- `PRISALY_CIERRE_TECNICO_2026-10-28.zip` / `PRISALY_CIERRE_TECNICO_FINAL.zip`
- Or a folder already containing the nine authorized `.sql` files

Then M1 will copy them unchanged into `app/src/main/resources/db/migration/`, verify Flyway order on the running Postgres 16.15 container, and clear this blocker.

### Until then

- Canonical Flyway path stays empty of historical SQL.
- Docker Postgres is ready (see `M1-DOCKER-EVIDENCE.md`).
- Do **not** start treating auth UI as done; Phase 1 persistence against official schema waits on this restore.
