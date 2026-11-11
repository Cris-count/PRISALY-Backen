-- PD-01: initial data for each environment. Idempotent. This is not a Flyway migration.
-- It creates no users and assigns no permissions: nothing is granted to ADMINISTRADOR.
-- Assignments must go through the audited application service, never through direct SQL.
INSERT INTO "Rol" ("nombre", "descripcion")
VALUES ('ADMINISTRADOR', 'Administra la asignacion y revocacion de permisos')
ON CONFLICT ("nombre") DO NOTHING;

-- D-04 (DC-P5-FASE-B-01): rol docente canonico. Sin permisos: siguen siendo individuales (DM1-03).
INSERT INTO "Rol" ("nombre", "descripcion")
VALUES ('DOCENTE', 'Docente que gestiona casos y sesiones')
ON CONFLICT ("nombre") DO NOTHING;

INSERT INTO "Permiso" ("codigo", "nombre")
VALUES ('CONSULTAR_CASO', 'Consultar caso'),
       ('MODIFICAR_CASO', 'Modificar caso'),
       ('PUBLICAR_CASO', 'Publicar caso'),
       ('ARCHIVAR_CASO', 'Archivar caso')
ON CONFLICT ("codigo") DO NOTHING;

-- DM1-03 (DC-M1-03): operational permissions already checked by M1/M3 (CREAR_CASO) and M4
-- (GESTIONAR_SESION). Catalog entries only: they are NOT assigned to any teacher or administrator.
INSERT INTO "Permiso" ("codigo", "nombre")
VALUES ('CREAR_CASO', 'Crear caso'),
       ('GESTIONAR_SESION', 'Gestionar sesion')
ON CONFLICT ("codigo") DO NOTHING;
