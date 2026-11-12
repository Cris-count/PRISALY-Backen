-- DC-CIERRE-MODULAR-01: QA student for the M3 -> M4 -> M7 campaign. DISPOSABLE DATABASES ONLY.
-- There is no HTTP route to register students (out of the approved contracts), so the student is the only SQL
-- fixture of the flow. No "Caso", "Sesion" or "Convocatoria" is inserted here: they are created over HTTP.
-- No password: the student does not authenticate in this campaign (DD-02 stays disabled).
BEGIN;
INSERT INTO "Rol" ("nombre", "descripcion") VALUES ('ESTUDIANTE_QA', 'Rol de fixture QA (base desechable)')
ON CONFLICT ("nombre") DO NOTHING;
INSERT INTO "Usuario" ("idUsuario", "idRol", "nombre", "correo", "passwordHash", "estado", "fechaCreacion")
SELECT '11111111-1111-1111-1111-111111111301'::uuid, r."idRol", 'Estudiante QA M3-M4-M7', 'estudiante.qa@prisaly.invalid',
       NULL, 'Activo', now()
FROM "Rol" r WHERE r."nombre" = 'ESTUDIANTE_QA'
ON CONFLICT ("correo") DO NOTHING;
INSERT INTO "Estudiante" ("idEstudiante", "idUsuario", "fechaRegistro")
VALUES ('22222222-2222-2222-2222-222222222301'::uuid, '11111111-1111-1111-1111-111111111301'::uuid, now())
ON CONFLICT ("idEstudiante") DO NOTHING;
COMMIT;
