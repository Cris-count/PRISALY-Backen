-- P-78 (DC-BE-D78-1-AUTORIZACION-CONTROLADA-79): extension of PD-01 for the QA-only administrative student
-- registration. Idempotent catalog data, not a Flyway migration and not a schema change.
-- The role carries NO permissions: students can never receive permissions (DM1-02b) nor authenticate as personnel
-- (no password is ever stored for them). Student access remains exclusively the DD-02 contract.
INSERT INTO "Rol" ("nombre", "descripcion")
VALUES ('ESTUDIANTE', 'Estudiante convocable; acceso solo por el contrato DD-02')
ON CONFLICT ("nombre") DO NOTHING;
