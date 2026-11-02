# DD-02 — Propuesta SQL completa (no ejecutar)

**Estado:** Propuesta para revisión — **no** es archivo bajo `app/src/main/resources/db/migration/`  
**Prohibido ahora:** crear V010/V011 definitivas, `flyway migrate`, ALTER de V001–V009  
**Numeración:** condicionada a [`DD02-INVENTARIO-FLYWAY.md`](DD02-INVENTARIO-FLYWAY.md) (candidato preferente provisional: siguiente versión libre tras 009 **sin** colisión con artefacto M4 → típicamente **011**, no definitiva)

Cuando se autorice la **aprobación 2**, este SQL se volcará a un único script versionado en app (nombre exacto TBD).

---

## SQL propuesto

```sql
-- =============================================================================
-- DD-02 PROPUESTA — Acceso estudiantil (OTP, intentos, sesión, Outbox)
-- NO EJECUTAR hasta aprobación de versión + ejecución Flyway.
-- No modifica objetos creados por V001–V009.
-- =============================================================================
BEGIN;

CREATE TABLE "CredencialAccesoEstudiante" (
  "idCredencial" uuid NOT NULL,
  "idUsuario" uuid NOT NULL,
  "idEstudiante" uuid NOT NULL,
  "challengeId" uuid NOT NULL,
  "codigoHmac" bytea NOT NULL,
  "keyVersion" varchar(32) NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaConsumo" timestamptz,
  "fechaRevocacion" timestamptz,
  "intentosFallidos" int NOT NULL DEFAULT 0,
  "fechaProximoReenvioPermitido" timestamptz,
  CONSTRAINT "pk_credencialaccesoestudiante" PRIMARY KEY ("idCredencial"),
  CONSTRAINT "uq_credencialaccesoestudiante_challengeid" UNIQUE ("challengeId"),
  CONSTRAINT "ck_credencialaccesoestudiante_intentos"
    CHECK ("intentosFallidos" >= 0 AND "intentosFallidos" <= 5),
  CONSTRAINT "ck_credencialaccesoestudiante_fechas"
    CHECK ("fechaExpiracion" > "fechaCreacion")
);

CREATE TABLE "IntentoAccesoEstudiante" (
  "idIntentoAcceso" uuid NOT NULL,
  "idCredencial" uuid NOT NULL,
  "fechaIntento" timestamptz NOT NULL DEFAULT now(),
  "resultado" varchar(30) NOT NULL,
  "motivoCodigo" varchar(80),
  "challengeId" uuid NOT NULL,
  "huellaCliente" varchar(64),
  CONSTRAINT "pk_intentoaccesoestudiante" PRIMARY KEY ("idIntentoAcceso"),
  CONSTRAINT "ck_intentoaccesoestudiante_resultado" CHECK (
    "resultado" IN ('EXITO', 'FALLO', 'BLOQUEADO', 'EXPIRADO', 'REVOCADO', 'CONSUMIDO')
  )
);

CREATE TABLE "SesionEstudiante" (
  "idSesionEstudiante" uuid NOT NULL,
  "idUsuario" uuid NOT NULL,
  "idEstudiante" uuid NOT NULL,
  "sesionTokenHmac" bytea NOT NULL,
  "keyVersion" varchar(32) NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaUltimaActividad" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaRevocacion" timestamptz,
  CONSTRAINT "pk_sesionestudiante" PRIMARY KEY ("idSesionEstudiante"),
  CONSTRAINT "uq_sesionestudiante_tokenhmac" UNIQUE ("sesionTokenHmac"),
  CONSTRAINT "ck_sesionestudiante_fechas" CHECK ("fechaExpiracion" > "fechaCreacion")
);

CREATE TABLE "EntregaCodigoAcceso" (
  "idEntrega" uuid NOT NULL,
  "challengeId" uuid NOT NULL,
  "idUsuarioDestino" uuid NOT NULL,
  "correoDestino" varchar(255) NOT NULL,
  "payloadCifrado" bytea NOT NULL,
  "nonce" bytea NOT NULL,
  "keyVersion" varchar(32) NOT NULL,
  "claveIdempotencia" varchar(150) NOT NULL,
  "estadoEntrega" varchar(25) NOT NULL DEFAULT 'Pendiente',
  "numeroIntentos" int NOT NULL DEFAULT 0,
  "ultimoError" text,
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaUltimoIntento" timestamptz,
  "proximoReintento" timestamptz,
  "fechaEnvio" timestamptz,
  CONSTRAINT "pk_entregacodigoacceso" PRIMARY KEY ("idEntrega"),
  CONSTRAINT "uq_entregacodigoacceso_claveidempotencia" UNIQUE ("claveIdempotencia"),
  CONSTRAINT "uq_entregacodigoacceso_challengeid" UNIQUE ("challengeId"),
  CONSTRAINT "ck_entregacodigoacceso_intentos" CHECK ("numeroIntentos" >= 0),
  CONSTRAINT "ck_entregacodigoacceso_estado" CHECK (
    "estadoEntrega" IN ('Pendiente', 'Enviado', 'Fallido', 'Expirado', 'Descartado')
  )
);

CREATE INDEX "ix_credencialaccesoestudiante_usuario_creacion"
  ON "CredencialAccesoEstudiante" ("idUsuario", "fechaCreacion" DESC);

CREATE INDEX "ix_intentoaccesoestudiante_credencial_fecha"
  ON "IntentoAccesoEstudiante" ("idCredencial", "fechaIntento");

CREATE INDEX "ix_sesionestudiante_estudiante_revocacion"
  ON "SesionEstudiante" ("idEstudiante", "fechaRevocacion");

CREATE INDEX "ix_entregacodigoacceso_estado_reintento"
  ON "EntregaCodigoAcceso" ("estadoEntrega", "proximoReintento");

ALTER TABLE "CredencialAccesoEstudiante"
  ADD CONSTRAINT "fk_credencialaccesoestudiante_idusuario"
  FOREIGN KEY ("idUsuario") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT;

ALTER TABLE "CredencialAccesoEstudiante"
  ADD CONSTRAINT "fk_credencialaccesoestudiante_idestudiante"
  FOREIGN KEY ("idEstudiante") REFERENCES "Estudiante" ("idEstudiante") ON DELETE RESTRICT;

ALTER TABLE "IntentoAccesoEstudiante"
  ADD CONSTRAINT "fk_intentoaccesoestudiante_idcredencial"
  FOREIGN KEY ("idCredencial") REFERENCES "CredencialAccesoEstudiante" ("idCredencial") ON DELETE RESTRICT;

ALTER TABLE "SesionEstudiante"
  ADD CONSTRAINT "fk_sesionestudiante_idusuario"
  FOREIGN KEY ("idUsuario") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT;

ALTER TABLE "SesionEstudiante"
  ADD CONSTRAINT "fk_sesionestudiante_idestudiante"
  FOREIGN KEY ("idEstudiante") REFERENCES "Estudiante" ("idEstudiante") ON DELETE RESTRICT;

ALTER TABLE "EntregaCodigoAcceso"
  ADD CONSTRAINT "fk_entregacodigoacceso_idusuariodestino"
  FOREIGN KEY ("idUsuarioDestino") REFERENCES "Usuario" ("idUsuario") ON DELETE RESTRICT;

COMMIT;
```

---

## Notas de implementación futura

1. Consumo atómico OTP: `UPDATE ... SET "fechaConsumo" = now() WHERE "challengeId" = ? AND "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL AND "fechaExpiracion" > now() AND "intentosFallidos" < 5`.
2. Outbox claim: patrón `FOR UPDATE SKIP LOCKED` análogo al worker de convocatorias M7.
3. Claves HMAC/AES: configuración / secretos fuera de PostgreSQL (`keyVersion` solo etiqueta).
4. No INSERT en `"EnvioCorreo"` para OTP.

---

## Estrategia de recuperación / rollback (resumen)

Detalle completo: [`DD02-PLAN-PRUEBAS-POSTGRES.md`](DD02-PLAN-PRUEBAS-POSTGRES.md) § Rollback.

- **No** basta un “SQL inverso” ad hoc en producción sin registro Flyway.
- Preferir: migración correctiva versionada siguiente + respaldo previo en entornos compartidos.
- `flyway repair` solo con diagnóstico y autorización; no como atajo de desarrollo.

---

**Aprobación 2 pendiente:** fijar nombre `V0NN__dd02_acceso_estudiante.sql` y volcar este cuerpo a app.  
**Aprobación 3 pendiente:** ejecutar migrate.
