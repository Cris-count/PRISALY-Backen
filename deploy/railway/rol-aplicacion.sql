-- DC-DEPLOY-01 · rol de aplicación con permisos mínimos para Staging (mismo modelo que DC-OPS-85).
-- Se ejecuta UNA vez, después de que Flyway haya migrado con el usuario propietario de Railway y de aplicar PD-01
-- (docs/provision/pd01-inicializacion.sql y pd01-p78-rol-estudiante.sql).
-- La contraseña NO está en este archivo: el operador la pasa a psql como variable y no queda en el historial:
--   psql "$DATABASE_URL" -v clave_app="<generada por el operador>" -f rol-aplicacion.sql
\set ON_ERROR_STOP on

SELECT format('CREATE ROLE prisaly_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', :'clave_app')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'prisaly_app') \gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO prisaly_app', current_database()) \gexec
GRANT USAGE ON SCHEMA public TO prisaly_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO prisaly_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO prisaly_app;
-- Sin DDL: las migraciones siguientes se ejecutan con el propietario (Flyway apagado en el servicio normal).
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = 'prisaly_app';
