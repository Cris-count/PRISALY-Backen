-- D-05 (DC-BE-D05-AUTORIZACION-QA-77, candidato aislado, SOLO QA): tokens de activacion de la primera credencial
-- docente. Fuera de db/migration: solo se aplica con SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/d05-propuesta
-- en el QA aislado de FE-60. El numero 020 evita colisionar con V014-V019 (candidatos) y queda sujeto a D-ESQ-1.
-- Solo se guarda el SHA-256 (hex) del token; nunca el token ni contrasenas. Sin datos semilla.
CREATE TABLE "ActivacionDocente" (
  "hashToken"   char(64)    NOT NULL,
  "idUsuario"   uuid        NOT NULL,
  "idEmisor"    uuid        NOT NULL,
  "emitidoEn"   timestamptz NOT NULL,
  "expiraEn"    timestamptz NOT NULL,
  "consumidoEn" timestamptz NULL,
  "revocadoEn"  timestamptz NULL,
  CONSTRAINT "pk_activaciondocente" PRIMARY KEY ("hashToken"),
  CONSTRAINT "ck_activaciondocente_hash" CHECK ("hashToken" ~ '^[0-9a-f]{64}$'),
  CONSTRAINT "ck_activaciondocente_vigencia" CHECK ("expiraEn" > "emitidoEn"),
  CONSTRAINT "ck_activaciondocente_cierre_unico" CHECK ("consumidoEn" IS NULL OR "revocadoEn" IS NULL),
  CONSTRAINT "fk_activaciondocente_usuario" FOREIGN KEY ("idUsuario") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT,
  CONSTRAINT "fk_activaciondocente_emisor" FOREIGN KEY ("idEmisor") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT
);
-- Como maximo un token vivo (ni consumido ni revocado) por usuario: dos emisiones concurrentes no pueden dejar dos.
CREATE UNIQUE INDEX "ux_activaciondocente_vivo" ON "ActivacionDocente" ("idUsuario")
  WHERE "consumidoEn" IS NULL AND "revocadoEn" IS NULL;
-- Historial inmutable: una fila cerrada (consumida o revocada) no se reabre ni cambia de token, usuario o fechas.
CREATE FUNCTION fn_activaciondocente_transicion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW."hashToken" <> OLD."hashToken" OR NEW."idUsuario" <> OLD."idUsuario" OR NEW."idEmisor" <> OLD."idEmisor"
     OR NEW."emitidoEn" <> OLD."emitidoEn" OR NEW."expiraEn" <> OLD."expiraEn"
     OR OLD."consumidoEn" IS NOT NULL OR OLD."revocadoEn" IS NOT NULL THEN
    RAISE EXCEPTION 'M1-ACTIVACION-INMUTABLE' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tr_activaciondocente_transicion BEFORE UPDATE ON "ActivacionDocente"
  FOR EACH ROW EXECUTE FUNCTION fn_activaciondocente_transicion();
CREATE FUNCTION fn_activaciondocente_sin_borrado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'M1-ACTIVACION-INMUTABLE' USING ERRCODE = '23514'; END $$;
CREATE TRIGGER tr_activaciondocente_sin_borrado BEFORE DELETE ON "ActivacionDocente"
  FOR EACH ROW EXECUTE FUNCTION fn_activaciondocente_sin_borrado();
