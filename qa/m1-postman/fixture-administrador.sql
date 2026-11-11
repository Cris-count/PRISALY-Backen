-- DC-M1-04: ONLY loaded by qa/m1-postman/run.mjs inside its own disposable PostgreSQL, after
-- docs/provision/pd01-inicializacion.sql and the public Bruno seeds.
-- Creates an ADMINISTRADOR account for the administrative Newman flow, reusing the public Bruno fixture hash.
-- This is NOT the first-administrator procedure (D-C / DM1-01, stopped: V010 incompatible with the M4 schema guard).
BEGIN;
INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo","passwordHash","estado","fechaCreacion")
SELECT '44444444-4444-4444-4444-444444444401', r."idRol", 'Administrador QA', 'admin.qa@prisaly.invalid',
       u."passwordHash", 'Activo', now()
FROM "Rol" r CROSS JOIN "Usuario" u
WHERE r."nombre" = 'ADMINISTRADOR' AND u."correo" = 'docente.bruno@prisaly.local';
COMMIT;
