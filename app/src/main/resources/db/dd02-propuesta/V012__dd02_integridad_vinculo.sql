-- DD-02 CANDIDATA (DC-M1-POSTCIERRE-01). No está en db/migration: requiere aprobación de
-- Dirección Central junto con V011. No altera V001-V009 ni datos existentes de BD-005.2.
-- Común a las alternativas A (cuenta + activación) y B (código por acceso).
BEGIN;
SET LOCAL lock_timeout='10s';

-- 1. Correspondencia usuario-estudiante. Dos FK independientes ("idUsuario" -> Usuario,
--    "idEstudiante" -> Estudiante) aceptan un estudiante de otro usuario; BD-005.2 no tiene
--    UNIQUE ("idEstudiante","idUsuario") para una FK compuesta y no se altera su estructura.
CREATE OR REPLACE FUNCTION public.prisaly_dd02_vinculo_coherente()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public."Estudiante" e
    WHERE e."idEstudiante" = NEW."idEstudiante" AND e."idUsuario" = NEW."idUsuario"
  ) THEN
    RAISE EXCEPTION 'DD02-VINCULO-INCOHERENTE' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER tr_activacion_estudiante_vinculo
BEFORE INSERT OR UPDATE OF "idUsuario", "idEstudiante" ON public."ActivacionCuentaEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02_vinculo_coherente();

CREATE TRIGGER tr_sesion_estudiante_vinculo
BEFORE INSERT OR UPDATE OF "idUsuario", "idEstudiante" ON public."SesionEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02_vinculo_coherente();

-- 2. Una sesión estudiantil solo se abre para una cuenta activa.
CREATE OR REPLACE FUNCTION public.prisaly_dd02_sesion_cuenta_activa()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public."Usuario" u
    WHERE u."idUsuario" = NEW."idUsuario" AND upper(u."estado") = 'ACTIVO'
  ) THEN
    RAISE EXCEPTION 'DD02-CUENTA-INACTIVA' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER tr_sesion_estudiante_cuenta_activa
BEFORE INSERT ON public."SesionEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02_sesion_cuenta_activa();

-- 3. El vínculo de Estudiante no cambia de usuario mientras existan filas DD-02 que lo usen
--    (solo se protege la columna; no se añade restricción estructural a la tabla histórica).
CREATE OR REPLACE FUNCTION public.prisaly_dd02_vinculo_inmutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW."idUsuario" IS DISTINCT FROM OLD."idUsuario" AND (
    EXISTS (SELECT 1 FROM public."ActivacionCuentaEstudiante" a WHERE a."idEstudiante" = OLD."idEstudiante")
    OR EXISTS (SELECT 1 FROM public."SesionEstudiante" s WHERE s."idEstudiante" = OLD."idEstudiante")
  ) THEN
    RAISE EXCEPTION 'DD02-VINCULO-EN-USO' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER tr_estudiante_vinculo_dd02
BEFORE UPDATE OF "idUsuario" ON public."Estudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02_vinculo_inmutable();

-- 4. Concurrencia de emisión: como máximo un código abierto por usuario. La aplicación revoca los
--    abiertos antes de emitir; dos emisiones simultáneas dejan un único ganador.
CREATE UNIQUE INDEX uq_activacioncuentaestudiante_abierta
ON public."ActivacionCuentaEstudiante" ("idUsuario")
WHERE "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL;

-- 5. Cierre coherente: consumo y revocación son excluyentes y posteriores a la creación.
ALTER TABLE public."ActivacionCuentaEstudiante"
  ADD CONSTRAINT ck_activacioncuentaestudiante_cierre
  CHECK (NOT ("fechaConsumo" IS NOT NULL AND "fechaRevocacion" IS NOT NULL)),
  ADD CONSTRAINT ck_activacioncuentaestudiante_consumo
  CHECK ("fechaConsumo" IS NULL OR "fechaConsumo" >= "fechaCreacion"),
  ADD CONSTRAINT ck_activacioncuentaestudiante_revocacion
  CHECK ("fechaRevocacion" IS NULL OR "fechaRevocacion" >= "fechaCreacion");

ALTER TABLE public."SesionEstudiante"
  ADD CONSTRAINT ck_sesionestudiante_revocacion
  CHECK ("fechaRevocacion" IS NULL OR "fechaRevocacion" >= "fechaCreacion");

-- 6. Inactividad de sesión (parámetro ratificado: 30 min; DD02-DISENO-PERSISTENCIA §3.3).
ALTER TABLE public."SesionEstudiante" ADD COLUMN "fechaUltimaActividad" timestamptz;
UPDATE public."SesionEstudiante" SET "fechaUltimaActividad" = "fechaCreacion";
ALTER TABLE public."SesionEstudiante" ALTER COLUMN "fechaUltimaActividad" SET NOT NULL;

COMMIT;
