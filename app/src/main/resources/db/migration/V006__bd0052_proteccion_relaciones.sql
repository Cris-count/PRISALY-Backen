-- PRISALY BEBD3-002.1 / V006: proteccion de relaciones academicas.
-- Requiere V001..V005. NO altera tablas, columnas, tipos ni modelos de estados.
-- IMPORTANTE: probar con READ COMMITTED y dos conexiones independientes.
BEGIN;

-- El UPDATE de idCaso obtiene NO KEY UPDATE inicialmente. El FOR UPDATE
-- fuerza conflicto con la FK KEY SHARE de convocatorias sin confirmar.
-- Se reevalua EXISTS DESPUES del bloqueo, evitando el snapshot prematuro.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_caso_sesion()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idCaso" IS DISTINCT FROM OLD."idCaso" THEN
    PERFORM 1 FROM public."Sesion"
      WHERE "idSesion" = OLD."idSesion" FOR UPDATE;
    IF EXISTS (SELECT 1 FROM public."Convocatoria"
               WHERE "idSesion" = OLD."idSesion") THEN
      RAISE EXCEPTION 'Caso de sesión convocada es inmutable'
        USING ERRCODE='23514', CONSTRAINT='tr_sesion_caso_inmutable';
    END IF;
  END IF;
  RETURN NEW;
END
$fn$;

-- IdSesion es identidad de asignacion de la convocatoria. Se mantiene editable
-- solo mientras no exista Participacion asociada, conforme a V005.
-- El FOR UPDATE espera cualquier FK KEY SHARE de participaciones concurrentes.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_sesion_convocatoria()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idSesion" IS DISTINCT FROM OLD."idSesion" THEN
    PERFORM 1 FROM public."Convocatoria"
      WHERE "idConvocatoria" = OLD."idConvocatoria" FOR UPDATE;
    IF EXISTS (SELECT 1 FROM public."Participacion"
               WHERE "idConvocatoria" = OLD."idConvocatoria") THEN
      RAISE EXCEPTION 'Sesion de convocatoria con participación es inmutable'
        USING ERRCODE='23514', CONSTRAINT='tr_convocatoria_sesion_inmutable';
    END IF;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER tr_convocatoria_sesion_inmutable
BEFORE UPDATE OF "idSesion" ON public."Convocatoria"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_sesion_convocatoria();

-- Una participacion no cambia su convocatoria de origen aunque aun no
-- tenga intentos: evita traslado de titularidad e historial académico.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_convocatoria_participacion()
RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idConvocatoria" IS DISTINCT FROM OLD."idConvocatoria" THEN
    RAISE EXCEPTION 'Convocatoria de participación es inmutable'
      USING ERRCODE='23514', CONSTRAINT='tr_participacion_convocatoria_inmutable';
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER tr_participacion_convocatoria_inmutable
BEFORE UPDATE OF "idConvocatoria" ON public."Participacion"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_convocatoria_participacion();
COMMIT;

-- PENDIENTE DE DIRECCION CENTRAL (no implementado): catálogo de estados,
-- variantes EN_CURSO, consumo Vigente -> Utilizada, DD-08.
