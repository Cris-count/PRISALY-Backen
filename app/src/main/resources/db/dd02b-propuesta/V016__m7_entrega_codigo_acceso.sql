-- DD-02 ALTERNATIVA B — CANDIDATA NO DESPLEGABLE (DC-DD02-B4-B5-01). Outbox cifrada de códigos de acceso (M7).
-- La carga (destinatario + código) va cifrada con AES-256-GCM (K-OUT, D-B2.7); en claro solo hay metadatos.
-- D-B2.4: la carga se elimina (NULL) al enviarse o vencer; tope técnico de 10 minutos; metadatos hasta 30 días.

CREATE TABLE "EntregaCodigoAcceso" (
  "idEntrega" uuid NOT NULL,
  "challengeId" uuid NOT NULL,
  "claveIdempotencia" varchar(80) NOT NULL,
  "cargaCifrada" bytea,
  "nonce" bytea,
  "kidCifrado" varchar(40),
  "estado" varchar(25) NOT NULL DEFAULT 'PENDIENTE',
  "intentos" int NOT NULL DEFAULT 0,
  "proximoIntento" timestamptz NOT NULL DEFAULT now(),
  "fechaCreacion" timestamptz NOT NULL DEFAULT now(),
  "fechaExpiracion" timestamptz NOT NULL,
  "fechaFinal" timestamptz,
  "codigoError" varchar(40),
  "reclamadaHasta" timestamptz,
  CONSTRAINT "pk_entregacodigoacceso" PRIMARY KEY ("idEntrega"),
  CONSTRAINT "uq_entregacodigoacceso_idempotencia" UNIQUE ("claveIdempotencia"),
  CONSTRAINT "uq_entregacodigoacceso_challenge" UNIQUE ("challengeId"),
  CONSTRAINT "ck_entregacodigoacceso_estado"
    CHECK ("estado" IN ('PENDIENTE', 'ENVIADA', 'CANCELADA_VENCIDA', 'FALLIDA_PERMANENTE', 'AGOTADA')),
  CONSTRAINT "ck_entregacodigoacceso_intentos" CHECK ("intentos" >= 0 AND "intentos" <= 3),
  -- DC-EJEC-CIERRE-01 (DD02-REV-01): two explicit branches. PENDIENTE carries the complete encrypted payload;
  -- every final state carries none of it. Any partial residue (payload without nonce, nonce without payload, ...) fails.
  CONSTRAINT "ck_entregacodigoacceso_carga"
    CHECK (("estado" = 'PENDIENTE'
             AND "cargaCifrada" IS NOT NULL AND "nonce" IS NOT NULL AND "kidCifrado" IS NOT NULL)
        OR ("estado" <> 'PENDIENTE'
             AND "cargaCifrada" IS NULL AND "nonce" IS NULL AND "kidCifrado" IS NULL)),
  CONSTRAINT "ck_entregacodigoacceso_tope"
    CHECK ("fechaExpiracion" <= "fechaCreacion" + interval '10 minutes')
);
CREATE INDEX "ix_entregacodigoacceso_pendiente" ON "EntregaCodigoAcceso" ("proximoIntento") WHERE "estado" = 'PENDIENTE';
CREATE INDEX "ix_entregacodigoacceso_creacion" ON "EntregaCodigoAcceso" ("fechaCreacion");
