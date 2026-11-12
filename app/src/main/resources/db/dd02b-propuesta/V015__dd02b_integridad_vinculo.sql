-- DD-02 ALTERNATIVA B — CANDIDATA NO DESPLEGABLE (DC-DD02-B4-B5-01). Adapta la propuesta V012 a la alternativa B.

-- 1. Correspondencia usuario-estudiante en credenciales y sesiones (BD-005.2 no tiene FK compuesta).
CREATE FUNCTION public.prisaly_dd02b_vinculo_coherente()
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
CREATE TRIGGER tr_credencial_acceso_vinculo
BEFORE INSERT OR UPDATE OF "idUsuario", "idEstudiante" ON public."CredencialAccesoEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02b_vinculo_coherente();
CREATE TRIGGER tr_sesion_estudiante_b_vinculo
BEFORE INSERT OR UPDATE OF "idUsuario", "idEstudiante" ON public."SesionEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02b_vinculo_coherente();

-- 2. Credenciales y sesiones solo para cuentas activas.
CREATE FUNCTION public.prisaly_dd02b_cuenta_activa()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public."Usuario" u WHERE u."idUsuario" = NEW."idUsuario" AND upper(u."estado") = 'ACTIVO'
  ) THEN
    RAISE EXCEPTION 'DD02-CUENTA-INACTIVA' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tr_credencial_acceso_cuenta_activa
BEFORE INSERT ON public."CredencialAccesoEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02b_cuenta_activa();
CREATE TRIGGER tr_sesion_estudiante_b_cuenta_activa
BEFORE INSERT ON public."SesionEstudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02b_cuenta_activa();

-- 3. El vínculo de Estudiante no cambia mientras existan credenciales o sesiones que lo usen.
CREATE FUNCTION public.prisaly_dd02b_vinculo_inmutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW."idUsuario" IS DISTINCT FROM OLD."idUsuario" AND (
    EXISTS (SELECT 1 FROM public."CredencialAccesoEstudiante" c WHERE c."idEstudiante" = OLD."idEstudiante")
    OR EXISTS (SELECT 1 FROM public."SesionEstudiante" s WHERE s."idEstudiante" = OLD."idEstudiante")
  ) THEN
    RAISE EXCEPTION 'DD02-VINCULO-EN-USO' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tr_estudiante_vinculo_dd02b
BEFORE UPDATE OF "idUsuario" ON public."Estudiante"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_dd02b_vinculo_inmutable();

-- 4. D-B2.1: un solo código vigente por estudiante. La aplicación revoca el anterior antes de emitir;
--    dos emisiones simultáneas dejan un único ganador.
CREATE UNIQUE INDEX uq_credencialaccesoestudiante_abierta
ON public."CredencialAccesoEstudiante" ("idUsuario")
WHERE "fechaConsumo" IS NULL AND "fechaRevocacion" IS NULL;

-- 5. Cierre coherente: consumo y revocación excluyentes y posteriores a la creación.
ALTER TABLE public."CredencialAccesoEstudiante"
  ADD CONSTRAINT ck_credencialaccesoestudiante_cierre
    CHECK (NOT ("fechaConsumo" IS NOT NULL AND "fechaRevocacion" IS NOT NULL)),
  ADD CONSTRAINT ck_credencialaccesoestudiante_consumo
    CHECK ("fechaConsumo" IS NULL OR "fechaConsumo" >= "fechaCreacion"),
  ADD CONSTRAINT ck_credencialaccesoestudiante_revocacion
    CHECK ("fechaRevocacion" IS NULL OR "fechaRevocacion" >= "fechaCreacion");
ALTER TABLE public."SesionEstudiante"
  ADD CONSTRAINT ck_sesionestudiante_revocacion
    CHECK ("fechaRevocacion" IS NULL OR "fechaRevocacion" >= "fechaCreacion");
