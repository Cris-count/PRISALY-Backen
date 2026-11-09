-- PD-01: initial data for each environment. Idempotent. This is not a Flyway migration.
-- It creates no users: the first ADMINISTRADOR account is assigned by the environment owner.
INSERT INTO "Rol" ("nombre", "descripcion")
VALUES ('ADMINISTRADOR', 'Administra la asignacion y revocacion de permisos')
ON CONFLICT ("nombre") DO NOTHING;

INSERT INTO "Permiso" ("codigo", "nombre")
VALUES ('CONSULTAR_CASO', 'Consultar caso'),
       ('MODIFICAR_CASO', 'Modificar caso')
ON CONFLICT ("codigo") DO NOTHING;
