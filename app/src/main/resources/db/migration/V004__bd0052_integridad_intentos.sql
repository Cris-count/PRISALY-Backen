-- PRISALY BEBD3-002.1 Fase 2 / PostgreSQL 16
-- Se aplica DESPUES de V001, V002 y V003. No crea tablas ni columnas.
-- Bajo READ COMMITTED; SERIALIZABLE requiere politica de reintentos ante 40001.
BEGIN;

-- Protege los identificadores de un intento contra reescritura posterior.
CREATE OR REPLACE FUNCTION prisaly_proteger_identidad_intento()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idParticipacion" IS DISTINCT FROM OLD."idParticipacion"
     OR NEW."numeroIntento" IS DISTINCT FROM OLD."numeroIntento"
     OR NEW."idAutorizacion" IS DISTINCT FROM OLD."idAutorizacion" THEN
    RAISE EXCEPTION 'Identidad y autorización del intento son inmutables'
      USING ERRCODE='23514', CONSTRAINT='tr_intento_identidad_inmutable';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE TRIGGER tr_intento_identidad_inmutable
BEFORE UPDATE OF "idParticipacion", "numeroIntento", "idAutorizacion"
ON public."Intento" FOR EACH ROW
EXECUTE FUNCTION prisaly_proteger_identidad_intento();

-- Sustituye exclusivamente la funcion de V003. Conserva el trigger ya instalado.
-- La fila Estudiante actua como mutex de estudiante/caso (mas amplio por estudiante).
-- Cada INSERT de Intento, aun entre distintas sesiones, toma el mismo bloqueo.
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
  PERFORM 1 FROM public."Estudiante" WHERE "idEstudiante"=v_est FOR UPDATE;
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

COMMIT;
-- PENDIENTE de Dirección Central: CHECK de Intento.estado, transiciones de
-- AutorizacionRepeticion y politica de correccion historica DD-08.
-- Nota: las escrituras de Convocatoria.idEstudiante / Sesion.idCaso y la
-- gestion transaccional del historial requieren disciplina de dominio;
-- esta V004 no autoriza cambiar su titularidad/caso retrospectivamente.
