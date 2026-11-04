-- LOCAL TEST SEED ONLY for Bruno M7 (M4 convocatoria → M7 aviso/cola).
-- Requires m1-bruno-seed-local.sql first (same docente / password LocalBruno!234).
-- Idempotent. Do NOT run on shared/prod DBs.
-- Fixed UUIDs are for Bruno env vars only.

BEGIN;

INSERT INTO "Permiso" ("codigo", "nombre", "descripcion")
VALUES ('GESTIONAR_SESION', 'Gestionar sesión', 'Permiso local Bruno / M4–M7 convocatoria')
ON CONFLICT ("codigo") DO NOTHING;

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
  AND p."codigo" = 'GESTIONAR_SESION'
ON CONFLICT ("idUsuario", "idPermiso") DO UPDATE
SET "habilitado" = true, "fechaAsignacion" = now();

-- Ensure no-perm user does not keep GESTIONAR_SESION
DELETE FROM "UsuarioPermiso" up
USING "Usuario" u, "Permiso" p
WHERE up."idUsuario" = u."idUsuario"
  AND up."idPermiso" = p."idPermiso"
  AND u."correo" = 'sinperm.bruno@prisaly.local'
  AND p."codigo" = 'GESTIONAR_SESION';

-- Estudiante linked to a dedicated Usuario (same DocenteBruno role for simplicity)
INSERT INTO "Usuario" (
  "idUsuario", "idRol", "nombre", "correo", "passwordHash", "estado", "fechaCreacion"
)
SELECT
  '11111111-1111-1111-1111-111111111201'::uuid,
  r."idRol",
  'Estudiante Bruno M7',
  'estudiante.bruno@prisaly.local',
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

INSERT INTO "Estudiante" ("idEstudiante", "idUsuario", "fechaRegistro")
VALUES (
  '22222222-2222-2222-2222-222222222201'::uuid,
  '11111111-1111-1111-1111-111111111201'::uuid,
  now()
)
ON CONFLICT ("idEstudiante") DO UPDATE
SET "idUsuario" = EXCLUDED."idUsuario";

-- Caso Publicado owned by Bruno docente (mínimo para crear sesión / convocar)
INSERT INTO "Caso" (
  "idCaso", "idDocente", "nombre", "tiempoEstimadoSegundos", "estado", "activo", "fechaCreacion"
)
VALUES (
  '33333333-3333-3333-3333-333333333301'::uuid,
  '11111111-1111-1111-1111-111111111101'::uuid,
  'Caso Bruno M7',
  60,
  'Publicado',
  true,
  now()
)
ON CONFLICT ("idCaso") DO UPDATE
SET
  "idDocente" = EXCLUDED."idDocente",
  "nombre" = EXCLUDED."nombre",
  "estado" = 'Publicado',
  "activo" = true,
  "fechaActualizacion" = now();

COMMIT;
