-- DD-02. Cuenta estudiantil propia: codigo de activacion de un solo uso y sesion distinta del Bearer.
-- No altera V001-V009 ni las tablas de BD-005.2. No crea outbox de correo.
BEGIN;
SET LOCAL lock_timeout='10s';

CREATE TABLE "ActivacionCuentaEstudiante" (
  "idActivacion" uuid NOT NULL,
  "idUsuario" uuid NOT NULL,
  "idEstudiante" uuid NOT NULL,
  "challengeId" uuid NOT NULL,
  "codigoHmac" bytea NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaConsumo" timestamptz,
  "fechaRevocacion" timestamptz,
  "intentosFallidos" int NOT NULL DEFAULT 0,
  CONSTRAINT "pk_activacioncuentaestudiante" PRIMARY KEY ("idActivacion"),
  CONSTRAINT "uq_activacioncuentaestudiante_challenge" UNIQUE ("challengeId"),
  CONSTRAINT "ck_activacioncuentaestudiante_intentos"
    CHECK ("intentosFallidos" >= 0 AND "intentosFallidos" <= 5),
  CONSTRAINT "ck_activacioncuentaestudiante_fechas"
    CHECK ("fechaExpiracion" > "fechaCreacion")
);

CREATE TABLE "SesionEstudiante" (
  "idSesionEstudiante" uuid NOT NULL,
  "idUsuario" uuid NOT NULL,
  "idEstudiante" uuid NOT NULL,
  "sesionTokenHmac" bytea NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaRevocacion" timestamptz,
  CONSTRAINT "pk_sesionestudiante" PRIMARY KEY ("idSesionEstudiante"),
  CONSTRAINT "uq_sesionestudiante_tokenhmac" UNIQUE ("sesionTokenHmac"),
  CONSTRAINT "ck_sesionestudiante_fechas" CHECK ("fechaExpiracion" > "fechaCreacion")
);

ALTER TABLE "ActivacionCuentaEstudiante"
  ADD CONSTRAINT "fk_activacioncuentaestudiante_idusuario"
  FOREIGN KEY ("idUsuario") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT;
ALTER TABLE "ActivacionCuentaEstudiante"
  ADD CONSTRAINT "fk_activacioncuentaestudiante_idestudiante"
  FOREIGN KEY ("idEstudiante") REFERENCES "Estudiante" ("idEstudiante") ON DELETE RESTRICT;
ALTER TABLE "SesionEstudiante"
  ADD CONSTRAINT "fk_sesionestudiante_idusuario"
  FOREIGN KEY ("idUsuario") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT;
ALTER TABLE "SesionEstudiante"
  ADD CONSTRAINT "fk_sesionestudiante_idestudiante"
  FOREIGN KEY ("idEstudiante") REFERENCES "Estudiante" ("idEstudiante") ON DELETE RESTRICT;

COMMIT;
