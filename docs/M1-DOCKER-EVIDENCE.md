# Docker persistence evidence (M1) — Fase 0

## Result (2026-10-30)

PostgreSQL **16.15** via `compose.yaml` is operational.

| Check | Result |
| --- | --- |
| `docker compose up -d` | OK — image `postgres:16.15` pulled |
| Health | `healthy` / `pg_isready` accepting connections |
| Version | `PostgreSQL 16.15 (Debian 16.15-1.pgdg13+2)` |
| Named volume | `prisaly_pg_data` |
| Probe insert | `m1_persist_probe (1, before-restart)` |
| After `docker compose restart postgres` | Same row recovered — **PASS** |

Commands used (no `down -v`):

```powershell
docker compose up -d
docker compose exec -T postgres pg_isready -U prisaly -d prisaly
docker compose exec -T postgres psql -U prisaly -d prisaly -c "CREATE TABLE IF NOT EXISTS m1_persist_probe(id int primary key, note text); INSERT INTO m1_persist_probe(id, note) VALUES (1, 'before-restart') ON CONFLICT (id) DO UPDATE SET note = EXCLUDED.note;"
docker compose restart postgres
docker compose exec -T postgres psql -U prisaly -d prisaly -c "SELECT * FROM m1_persist_probe;"
docker compose exec -T postgres psql -U prisaly -d prisaly -c "SELECT version();"
```
