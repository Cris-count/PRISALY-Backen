-- DC-92 DD-04 (ratificada) CANDIDATE. NOT in db/migration: applied only where SPRING_FLYWAY_LOCATIONS adds
-- classpath:db/dc92-propuesta. Number subject to D-ESQ-1. Non-destructive: one new insert-only table and one CHECK.
-- "Una unica repeticion excepcional": at most attempt 1 and its single repetition (attempt 2) per participation.
ALTER TABLE public."Intento" ADD CONSTRAINT ck_intento_una_repeticion_dd04 CHECK ("numeroIntento" BETWEEN 1 AND 2);

-- "Seleccion docente del intento que tendra validez academica" + "registro de usuario, fecha, motivo y operacion".
-- Insert-only history: the current selection is the latest row of the participation.
CREATE TABLE public."SeleccionIntento" (
  "idSeleccion"     uuid        NOT NULL,
  "idParticipacion" uuid        NOT NULL,
  "idIntento"       uuid        NOT NULL,
  "idDocente"       uuid        NOT NULL,
  "motivo"          text        NOT NULL,
  "fechaSeleccion"  timestamptz NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT "pk_seleccionintento" PRIMARY KEY ("idSeleccion"),
  CONSTRAINT "fk_seleccion_participacion" FOREIGN KEY ("idParticipacion") REFERENCES public."Participacion" ("idParticipacion"),
  CONSTRAINT "fk_seleccion_intento" FOREIGN KEY ("idIntento") REFERENCES public."Intento" ("idIntento"),
  CONSTRAINT "fk_seleccion_docente" FOREIGN KEY ("idDocente") REFERENCES public."Usuario" ("idUsuario"),
  CONSTRAINT ck_seleccion_motivo CHECK (length(btrim("motivo")) > 0)
);
CREATE INDEX ix_seleccion_participacion ON public."SeleccionIntento" ("idParticipacion", "fechaSeleccion");

CREATE OR REPLACE FUNCTION public.prisaly_validar_seleccion_intento() RETURNS trigger LANGUAGE plpgsql
SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public."Intento" i WHERE i."idIntento" = NEW."idIntento"
                   AND i."idParticipacion" = NEW."idParticipacion" AND i."estado" = 'Finalizado') THEN
    RAISE EXCEPTION 'Solo se selecciona un intento finalizado de la misma participacion'
      USING ERRCODE = '23514', CONSTRAINT = 'tr_seleccion_intento_valido';
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER tr_seleccion_intento_valido BEFORE INSERT ON public."SeleccionIntento"
  FOR EACH ROW EXECUTE FUNCTION public.prisaly_validar_seleccion_intento();

CREATE OR REPLACE FUNCTION public.prisaly_historial_dc92_inmutable() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  RAISE EXCEPTION 'Historial DC-92 inmutable: % no admite UPDATE ni DELETE', TG_TABLE_NAME USING ERRCODE = '55000';
END $fn$;
CREATE TRIGGER tr_seleccion_intento_inmutable BEFORE UPDATE OR DELETE ON public."SeleccionIntento"
  FOR EACH ROW EXECUTE FUNCTION public.prisaly_historial_dc92_inmutable();
