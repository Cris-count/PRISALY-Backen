# Docker persistence evidence (M1)

## Attempted on 2026-10-30

`docker compose up -d` failed because Docker Desktop Linux engine was not running:

`failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`

## Commands to run when Docker is available

```powershell
cd PRISALY-Backend
copy .env.example .env
docker compose up -d
docker compose ps
docker compose exec postgres pg_isready -U prisaly -d prisaly
docker compose exec postgres psql -U prisaly -d prisaly -c "CREATE TABLE IF NOT EXISTS m1_persist_probe(id int primary key, note text); INSERT INTO m1_persist_probe(id, note) VALUES (1, 'before-restart') ON CONFLICT (id) DO UPDATE SET note = EXCLUDED.note;"
docker compose restart postgres
docker compose exec postgres psql -U prisaly -d prisaly -c "SELECT * FROM m1_persist_probe;"
```

Expected: row `(1, before-restart)` after restart. Do not use `docker compose down -v`.
