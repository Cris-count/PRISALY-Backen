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
INSERT INTO "Permiso" ("codigo","nombre","descripcion")
VALUES ('CONSULTAR_CASO','Consultar caso','Permiso de lectura institucional')
ON CONFLICT ("codigo") DO NOTHING;
INSERT INTO "UsuarioPermiso" ("idUsuario","idPermiso","habilitado","fechaAsignacion")
SELECT '11111111-1111-1111-1111-111111111101', "idPermiso", true, now()
FROM "Permiso" WHERE "codigo"='CONSULTAR_CASO'
ON CONFLICT ("idUsuario","idPermiso") DO UPDATE SET "habilitado"=true;

INSERT INTO "Rol" ("nombre","descripcion") VALUES ('ADMINISTRADOR','Administrador de prueba')
ON CONFLICT ("nombre") DO NOTHING;
INSERT INTO "Usuario" ("idUsuario","idRol","nombre","correo","passwordHash","estado","fechaCreacion")
SELECT '11111111-1111-1111-1111-111111111105', r."idRol", 'Admin QA', 'admin.qa@prisaly.invalid',
       u."passwordHash", 'Activo', now()
FROM "Usuario" u CROSS JOIN "Rol" r
WHERE u."idUsuario"='11111111-1111-1111-1111-111111111101' AND r."nombre"='ADMINISTRADOR';

INSERT INTO "Rol" ("nombre","descripcion") VALUES ('DOCENTE','Docente de prueba')
ON CONFLICT ("nombre") DO NOTHING;
UPDATE "Usuario" SET "idRol"=(SELECT "idRol" FROM "Rol" WHERE "nombre"='DOCENTE')
WHERE "idUsuario"='11111111-1111-1111-1111-111111111101';

COMMIT;
