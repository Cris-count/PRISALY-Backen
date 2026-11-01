-- LOCAL TEST SEED ONLY for Bruno M1. Idempotent. Do NOT run on shared/prod DBs.
-- Password plaintext (local only): LocalBruno!234
-- BCrypt: $2a$10$P/IxGSwcw55pXZaBGb5iL.Pyib5GCz1PcEQs.ZMI00Vkb27loRVgi
-- Permission code matches PersonalAuthorizationController.PERMISSION_CREAR_CASO = CREAR_CASO

BEGIN;

INSERT INTO "Rol" ("nombre", "descripcion")
VALUES ('DocenteBruno', 'Rol local Bruno M1')
ON CONFLICT ("nombre") DO NOTHING;

INSERT INTO "Permiso" ("codigo", "nombre", "descripcion")
VALUES ('CREAR_CASO', 'Crear caso', 'Permiso local Bruno / M1 probe')
ON CONFLICT ("codigo") DO NOTHING;

-- Active user WITH CREAR_CASO
INSERT INTO "Usuario" (
  "idUsuario", "idRol", "nombre", "correo", "passwordHash", "estado", "fechaCreacion"
)
SELECT
  '11111111-1111-1111-1111-111111111101'::uuid,
  r."idRol",
  'Docente Bruno Con Permiso',
  'docente.bruno@prisaly.local',
  '$2a$10$P/IxGSwcw55pXZaBGb5iL.Pyib5GCz1PcEQs.ZMI00Vkb27loRVgi',
  'Activo',
  now()
FROM "Rol" r
WHERE r."nombre" = 'DocenteBruno'
ON CONFLICT ("correo") DO UPDATE
SET
  "passwordHash" = EXCLUDED."passwordHash",
  "estado" = 'Activo',
  "idRol" = EXCLUDED."idRol",
  "fechaActualizacion" = now();

-- Active user WITHOUT CREAR_CASO
INSERT INTO "Usuario" (
  "idUsuario", "idRol", "nombre", "correo", "passwordHash", "estado", "fechaCreacion"
)
SELECT
  '11111111-1111-1111-1111-111111111102'::uuid,
  r."idRol",
  'Docente Bruno Sin Permiso',
  'sinperm.bruno@prisaly.local',
  '$2a$10$P/IxGSwcw55pXZaBGb5iL.Pyib5GCz1PcEQs.ZMI00Vkb27loRVgi',
  'Activo',
  now()
FROM "Rol" r
WHERE r."nombre" = 'DocenteBruno'
ON CONFLICT ("correo") DO UPDATE
SET
  "passwordHash" = EXCLUDED."passwordHash",
  "estado" = 'Activo',
  "idRol" = EXCLUDED."idRol",
  "fechaActualizacion" = now();

-- Inactive user
INSERT INTO "Usuario" (
  "idUsuario", "idRol", "nombre", "correo", "passwordHash", "estado", "fechaCreacion"
)
SELECT
  '11111111-1111-1111-1111-111111111103'::uuid,
  r."idRol",
  'Docente Bruno Inactivo',
  'inactive.bruno@prisaly.local',
  '$2a$10$P/IxGSwcw55pXZaBGb5iL.Pyib5GCz1PcEQs.ZMI00Vkb27loRVgi',
  'Inactivo',
  now()
FROM "Rol" r
WHERE r."nombre" = 'DocenteBruno'
ON CONFLICT ("correo") DO UPDATE
SET
  "passwordHash" = EXCLUDED."passwordHash",
  "estado" = 'Inactivo',
  "idRol" = EXCLUDED."idRol",
  "fechaActualizacion" = now();

INSERT INTO "UsuarioPermiso" (
  "idUsuario", "idPermiso", "habilitado", "idAdministradorAsignador", "fechaAsignacion"
)
SELECT
  u."idUsuario",
  p."idPermiso",
  true,
  NULL,
  now()
FROM "Usuario" u
CROSS JOIN "Permiso" p
WHERE u."correo" = 'docente.bruno@prisaly.local'
  AND p."codigo" = 'CREAR_CASO'
ON CONFLICT ("idUsuario", "idPermiso") DO UPDATE
SET "habilitado" = true, "fechaAsignacion" = now();

-- Ensure no-perm user does not keep CREAR_CASO enabled
DELETE FROM "UsuarioPermiso" up
USING "Usuario" u, "Permiso" p
WHERE up."idUsuario" = u."idUsuario"
  AND up."idPermiso" = p."idPermiso"
  AND u."correo" = 'sinperm.bruno@prisaly.local'
  AND p."codigo" = 'CREAR_CASO';

COMMIT;
