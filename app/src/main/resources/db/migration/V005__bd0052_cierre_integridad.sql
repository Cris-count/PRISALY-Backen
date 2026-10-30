-- PRISALY BEBD3-002.1 / V005, cierre tecnico de integridad.
-- Requiere V001..V004. No crea ni altera tablas, columnas ni tipos.
-- Estados definitivos (DD pendiente) NO se imponen en esta migracion.
BEGIN;

-- Mejora del bloqueo serializador de V004: permite FK KEY SHARE de
-- Convocatoria sin perder exclusividad entre inserciones de Intento.
CREATE OR REPLACE FUNCTION prisaly_validar_intento()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
DECLARE
 v_aut public."AutorizacionRepeticion"%ROWTYPE;
 v_prev boolean;
 v_est uuid;
 v_caso uuid;
BEGIN
  -- Bloqueo existente por participacion (mantenido por compatibilidad).
  PERFORM 1 FROM public."Participacion"
    WHERE "idParticipacion"=NEW."idParticipacion" FOR UPDATE;
  SELECT c."idEstudiante", s."idCaso" INTO v_est, v_caso
    FROM public."Participacion" p
    JOIN public."Convocatoria" c ON c."idConvocatoria"=p."idConvocatoria"
    JOIN public."Sesion" s ON s."idSesion"=c."idSesion"
    WHERE p."idParticipacion"=NEW."idParticipacion";
  -- Un id desconocido sera rechazado por FK; no disfrazarlo como repeticion.
  IF v_est IS NULL OR v_caso IS NULL THEN RETURN NEW; END IF;

  -- Serializacion entre participaciones y sesiones del MISMO estudiante.
  PERFORM 1 FROM public."Estudiante" WHERE "idEstudiante"=v_est FOR NO KEY UPDATE;
  IF NOT FOUND THEN RETURN NEW; END IF;

  -- Releer historial DESPUES de adquirir el bloqueo.
  SELECT EXISTS (
    SELECT 1 FROM public."Intento" i
      JOIN public."Participacion" p ON p."idParticipacion"=i."idParticipacion"
      JOIN public."Convocatoria" c ON c."idConvocatoria"=p."idConvocatoria"
      JOIN public."Sesion" s ON s."idSesion"=c."idSesion"
    WHERE c."idEstudiante"=v_est AND s."idCaso"=v_caso
  ) INTO v_prev;

  IF (NEW."numeroIntento" > 1 OR v_prev) AND NEW."idAutorizacion" IS NULL THEN
    RAISE EXCEPTION 'Repetición exige autorización expresa'
      USING ERRCODE='23514', CONSTRAINT='tr_intento_autorizacion';
  END IF;

  IF NEW."idAutorizacion" IS NOT NULL THEN
    SELECT * INTO v_aut FROM public."AutorizacionRepeticion"
      WHERE "idAutorizacion"=NEW."idAutorizacion" FOR UPDATE;
    IF NOT FOUND OR v_aut."idParticipacion" <> NEW."idParticipacion"
       OR v_aut."estadoAutorizacion" <> 'Vigente'
       OR v_aut."fechaAutorizacion" > clock_timestamp()
       OR (v_aut."fechaVencimiento" IS NOT NULL
           AND v_aut."fechaVencimiento" <= clock_timestamp()) THEN
      RAISE EXCEPTION 'Autorización inválida o vencida'
        USING ERRCODE='23514', CONSTRAINT='tr_intento_autorizacion';
    END IF;
    IF EXISTS (SELECT 1 FROM public."Intento"
       WHERE "idAutorizacion"=NEW."idAutorizacion") THEN
      RAISE EXCEPTION 'Autorización ya consumida'
        USING ERRCODE='23505', CONSTRAINT='tr_intento_autorizacion';
    END IF;
  END IF;
  RETURN NEW;
END
$fn$;


-- El borrado de un intento invalida el historial empleado por DD-14.
CREATE OR REPLACE FUNCTION public.prisaly_impedir_delete_intento()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  RAISE EXCEPTION 'Un intento académico no puede eliminarse'
    USING ERRCODE='23514', CONSTRAINT='tr_intento_no_delete';
END
$fn$;
CREATE TRIGGER tr_intento_no_delete
BEFORE DELETE ON public."Intento"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_impedir_delete_intento();

-- Proteccion puntual ya sustentada: un intento finalizado no se reabre.
-- El catalogo de todos los estados y transiciones sigue pendiente.
CREATE OR REPLACE FUNCTION public.prisaly_impedir_reabrir_intento()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF OLD."estado" = 'Finalizado' AND NEW."estado" = 'EnCurso' THEN
    RAISE EXCEPTION 'Intento finalizado no puede reabrirse'
      USING ERRCODE='23514', CONSTRAINT='tr_intento_no_reabrir';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE TRIGGER tr_intento_no_reabrir
BEFORE UPDATE OF "estado" ON public."Intento"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_impedir_reabrir_intento();

-- Bloquear sustitucion de caso tras generar convocatorias.
-- Es mas conservador que esperar el primer intento y protege
-- el historial ante cambios posteriores de origen de sesion.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_caso_sesion()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idCaso" IS DISTINCT FROM OLD."idCaso"
     AND EXISTS (SELECT 1 FROM public."Convocatoria"
                 WHERE "idSesion"=OLD."idSesion") THEN
    RAISE EXCEPTION 'Caso de sesión convocada es inmutable'
      USING ERRCODE='23514', CONSTRAINT='tr_sesion_caso_inmutable';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE TRIGGER tr_sesion_caso_inmutable
BEFORE UPDATE OF "idCaso" ON public."Sesion"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_caso_sesion();

-- No se cambia la titularidad de una convocatoria con Participacion.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_estudiante_convocatoria()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idEstudiante" IS DISTINCT FROM OLD."idEstudiante"
     AND EXISTS (SELECT 1 FROM public."Participacion"
                 WHERE "idConvocatoria"=OLD."idConvocatoria") THEN
    RAISE EXCEPTION 'Estudiante con participación registrada es inmutable'
      USING ERRCODE='23514', CONSTRAINT='tr_convocatoria_estudiante_inmutable';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE TRIGGER tr_convocatoria_estudiante_inmutable
BEFORE UPDATE OF "idEstudiante" ON public."Convocatoria"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_estudiante_convocatoria();

-- La autorizacion pertenece a una Participacion de destino desde el alta.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_destino_autorizacion()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idParticipacion" IS DISTINCT FROM OLD."idParticipacion" THEN
    RAISE EXCEPTION 'Destino de autorización es inmutable'
      USING ERRCODE='23514', CONSTRAINT='tr_autorizacion_destino_inmutable';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE TRIGGER tr_autorizacion_destino_inmutable
BEFORE UPDATE OF "idParticipacion" ON public."AutorizacionRepeticion"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_destino_autorizacion();

COMMIT;

-- PENDIENTE: catalogo de estados (incluyendo EN_CURSO), ciclo Vigente/Utilizada,
-- DD-08 correcciones academicas excepcionales, politica de edicion de contenidos.
