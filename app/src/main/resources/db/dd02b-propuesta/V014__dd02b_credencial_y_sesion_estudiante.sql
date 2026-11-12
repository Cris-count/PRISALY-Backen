-- DD-02 ALTERNATIVA B — CANDIDATA NO DESPLEGABLE (DC-DD02-B4-B5-01). Ubicación opcional db/dd02b-propuesta:
-- NO está en db/migration y no la aplica el Flyway productivo. Numeración técnica candidata sujeta a D-11 y D-12.
-- Amplía BD-005.2; no modifica V001-V009 ni V013. Sustituye a las propuestas V011/V012 (alternativa A).
-- Código de acceso individual, temporal y de un solo uso (D-B2.1); sesión estudiantil (D-B2.6); auditoría (D-B2.4).

CREATE TABLE "CredencialAccesoEstudiante" (
  "idCredencial" uuid NOT NULL,
  "idUsuario" uuid NOT NULL,
  "idEstudiante" uuid NOT NULL,
  "challengeId" uuid NOT NULL,
  "codigoHmac" bytea NOT NULL,
  "kidCodigo" varchar(40) NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaConsumo" timestamptz,
  "fechaRevocacion" timestamptz,
  "intentosFallidos" int NOT NULL DEFAULT 0,
  CONSTRAINT "pk_credencialaccesoestudiante" PRIMARY KEY ("idCredencial"),
  CONSTRAINT "uq_credencialaccesoestudiante_challenge" UNIQUE ("challengeId"),
  CONSTRAINT "ck_credencialaccesoestudiante_intentos" CHECK ("intentosFallidos" >= 0 AND "intentosFallidos" <= 5),
  CONSTRAINT "ck_credencialaccesoestudiante_fechas" CHECK ("fechaExpiracion" > "fechaCreacion"),
  CONSTRAINT "ck_credencialaccesoestudiante_vigencia"
    CHECK ("fechaExpiracion" <= "fechaCreacion" + interval '5 minutes'),
  CONSTRAINT "fk_credencialaccesoestudiante_idusuario" FOREIGN KEY ("idUsuario")
    REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT,
  CONSTRAINT "fk_credencialaccesoestudiante_idestudiante" FOREIGN KEY ("idEstudiante")
    REFERENCES "Estudiante" ("idEstudiante") ON DELETE RESTRICT
);
CREATE INDEX "ix_credencialaccesoestudiante_usuario_creacion"
  ON "CredencialAccesoEstudiante" ("idUsuario", "fechaCreacion" DESC);

CREATE TABLE "SesionEstudiante" (
  "idSesionEstudiante" uuid NOT NULL,
  "idUsuario" uuid NOT NULL,
  "idEstudiante" uuid NOT NULL,
  "sesionTokenHmac" bytea NOT NULL,
  "kidSesion" varchar(40) NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaRevocacion" timestamptz,
  "fechaUltimaActividad" timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT "pk_sesionestudiante" PRIMARY KEY ("idSesionEstudiante"),
  CONSTRAINT "uq_sesionestudiante_tokenhmac" UNIQUE ("sesionTokenHmac"),
  CONSTRAINT "ck_sesionestudiante_fechas" CHECK ("fechaExpiracion" > "fechaCreacion"),
  CONSTRAINT "ck_sesionestudiante_vigencia" CHECK ("fechaExpiracion" <= "fechaCreacion" + interval '8 hours'),
  CONSTRAINT "fk_sesionestudiante_idusuario" FOREIGN KEY ("idUsuario")
    REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT,
  CONSTRAINT "fk_sesionestudiante_idestudiante" FOREIGN KEY ("idEstudiante")
    REFERENCES "Estudiante" ("idEstudiante") ON DELETE RESTRICT
);

-- Auditoría de accesos (D-B2.4): sin códigos, HMAC, tokens ni correos. Retención propuesta 365 días.
CREATE TABLE "IntentoAccesoEstudiante" (
  "idIntentoAcceso" uuid NOT NULL,
  "idEstudiante" uuid,
  "evento" varchar(20) NOT NULL,
  "resultado" varchar(30) NOT NULL,
  "origen" varchar(64) NOT NULL,
  "fecha" timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT "pk_intentoaccesoestudiante" PRIMARY KEY ("idIntentoAcceso"),
  CONSTRAINT "ck_intentoaccesoestudiante_evento" CHECK ("evento" IN ('SOLICITUD', 'VALIDACION', 'CIERRE'))
);
CREATE INDEX "ix_intentoaccesoestudiante_fecha" ON "IntentoAccesoEstudiante" ("fecha");
