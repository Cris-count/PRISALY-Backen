# Bruno — PRISALY M1

1. Start Postgres: `docker compose up -d`
2. Start API with `DATABASE_*` env vars
3. Open this `bruno/` folder in Bruno
4. Select environment `local`
5. Run `health`, then auth requests

`personal-autenticar` expects a seeded personnel user. Until official migrations and seed exist, use integration tests for automated evidence.
