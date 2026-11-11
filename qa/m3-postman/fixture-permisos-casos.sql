-- DC-M3-01: ONLY loaded by qa/m3-postman/run.mjs (configuración "prueba") inside its own disposable PostgreSQL.
-- Grants the four PD-01 case permissions to the public Bruno fixture teacher. It is NOT the productive
-- procedure: productive assignment requires an administrator actor (GestionPermisosService, DM1-01/DM1-04).
-- Loaded BEFORE qa/backend-http/fixture.sql, which copies the teacher's permissions to other.qa@prisaly.invalid.
BEGIN;
INSERT INTO "UsuarioPermiso" ("idUsuario","idPermiso","habilitado","fechaAsignacion")
SELECT u."idUsuario", p."idPermiso", true, now()
FROM "Usuario" u CROSS JOIN "Permiso" p
WHERE u."correo" = 'docente.bruno@prisaly.local'
  AND p."codigo" IN ('CONSULTAR_CASO','MODIFICAR_CASO','PUBLICAR_CASO','ARCHIVAR_CASO')
ON CONFLICT ("idUsuario","idPermiso") DO UPDATE SET "habilitado" = true;
COMMIT;
