-- Execute only against an explicitly identified/authorized environment.
-- No credentials, student rows, repair, DDL or mutations are collected.
BEGIN READ ONLY;
SELECT current_database() AS entorno_bd, current_setting('server_version') AS postgres;
SELECT table_schema, table_name, table_type FROM information_schema.tables
WHERE table_schema='public' ORDER BY table_name;
SELECT installed_rank, version, type, script, checksum, success
FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT c.relname AS tabla, k.conname AS restriccion, k.convalidated,
       pg_get_constraintdef(k.oid,false) AS definicion
FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
ORDER BY c.relname,k.conname;
SELECT c.relname AS tabla,t.tgname AS trigger,t.tgenabled,pg_get_triggerdef(t.oid,false)
FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal
ORDER BY c.relname,t.tgname;
ROLLBACK;
