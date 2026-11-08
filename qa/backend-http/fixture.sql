-- DC-EJEC-02: ONLY loaded by run.mjs inside its own disposable PostgreSQL.
-- Reuses existing Bruno credentials. No production account or new permission.
BEGIN;
INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo","passwordHash","estado","fechaCreacion")
SELECT '11111111-1111-1111-1111-111111111104', "idRol", 'Otro docente QA', 'other.qa@prisaly.invalid', "passwordHash", 'Activo', now()
FROM "Usuario" WHERE "idUsuario"='11111111-1111-1111-1111-111111111101';
INSERT INTO "UsuarioPermiso" ("idUsuario","idPermiso","habilitado","fechaAsignacion")
SELECT '11111111-1111-1111-1111-111111111104', "idPermiso", true, now()
FROM "UsuarioPermiso" WHERE "idUsuario"='11111111-1111-1111-1111-111111111101';
INSERT INTO "Caso" ("idCaso","idDocente","nombre","tiempoEstimadoSegundos","estado","activo") VALUES
('33333333-3333-3333-3333-333333333302','11111111-1111-1111-1111-111111111101','QA Borrador',60,'Borrador',true),
('33333333-3333-3333-3333-333333333303','11111111-1111-1111-1111-111111111101','QA Inactivo',60,'Publicado',false);
COMMIT;
