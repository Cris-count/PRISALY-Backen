-- DC-BE-SIM-B1-B2A-24 (Incremento B, B1, M5): fase academica persistida del intento (exploracion / cuestionario).
-- CANDIDATA. Ubicacion db/sim-propuesta-b: NO forma parte del Flyway productivo (db/migration) ni de las 21 tablas de
-- BD-005.2. Va en una ubicacion hermana de db/sim-propuesta para que los contratos del Incremento A (V017 sola) no
-- cambien: Flyway recorre cada ubicacion de forma recursiva. Depende de V017 (GuionNarrativoCaso). Solo se aplica en
-- QA con los contratos bd0052-v013-sim-ab o bd0052-v013-dd02b-sim-ab. Su promocion productiva requiere D-ESQ-1.
-- No codifica ninguna politica academica pendiente (D-SIM-2, D-SIM-3, D-SIM-4, D-SIM-10): solo las reglas R3-R5
-- (transicion explicita, sin retroceso, persistida en el servidor) y la integridad del vinculo intento-guion.

CREATE TABLE public."FaseIntento" (
  "idIntento" uuid NOT NULL,
  "idGuion" uuid NOT NULL,
  "fase" varchar(20) NOT NULL DEFAULT 'Exploracion',
  "fechaInicioExploracion" timestamptz NOT NULL DEFAULT clock_timestamp(),
  "fechaInicioCuestionario" timestamptz,
  CONSTRAINT "pk_faseintento" PRIMARY KEY ("idIntento"),
  CONSTRAINT "fk_faseintento_idintento" FOREIGN KEY ("idIntento") REFERENCES public."Intento" ("idIntento") ON DELETE RESTRICT,
  CONSTRAINT "fk_faseintento_idguion" FOREIGN KEY ("idGuion") REFERENCES public."GuionNarrativoCaso" ("idGuion") ON DELETE RESTRICT,
  CONSTRAINT ck_faseintento_fase CHECK ("fase" IN ('Exploracion', 'Cuestionario')),
  CONSTRAINT ck_faseintento_coherencia CHECK (("fase" = 'Exploracion') = ("fechaInicioCuestionario" IS NULL)),
  CONSTRAINT ck_faseintento_orden CHECK ("fechaInicioCuestionario" IS NULL OR "fechaInicioCuestionario" >= "fechaInicioExploracion")
);

CREATE INDEX ix_faseintento_idguion ON public."FaseIntento" ("idGuion");

-- Alta: solo en Exploracion, para un intento EnCurso, con un guion Publicado del mismo caso que la participacion
-- (prisaly_caso_participacion, V003). La fila Intento se bloquea para serializar con la finalizacion.
-- Cambios: solo Exploracion -> Cuestionario, una vez, mientras el intento siga EnCurso. Nunca retrocede ni cambia el
-- intento, el guion o la fecha de inicio de exploracion. Borrado: nunca (como tr_intento_no_delete, V005).
CREATE OR REPLACE FUNCTION public.prisaly_proteger_fase_intento_sim()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, public AS $fn$
DECLARE
  v_estado varchar(25);
  v_participacion uuid;
  v_guion_caso uuid;
  v_guion_estado varchar(25);
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'La fase de un intento no puede eliminarse'
      USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_intento_no_delete_sim';
  END IF;
  SELECT "estado", "idParticipacion" INTO v_estado, v_participacion
    FROM public."Intento" WHERE "idIntento" = NEW."idIntento" FOR UPDATE;
  IF TG_OP = 'INSERT' THEN
    IF NEW."fase" <> 'Exploracion' OR NEW."fechaInicioCuestionario" IS NOT NULL THEN
      RAISE EXCEPTION 'Un intento comienza siempre en Exploracion'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_intento_sim';
    END IF;
    IF v_estado IS DISTINCT FROM 'EnCurso' THEN
      RAISE EXCEPTION 'Solo un intento EnCurso puede tener fase'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_intento_sim';
    END IF;
    SELECT "idCaso", "estado" INTO v_guion_caso, v_guion_estado
      FROM public."GuionNarrativoCaso" WHERE "idGuion" = NEW."idGuion";
    IF v_guion_estado IS DISTINCT FROM 'Publicado' THEN
      RAISE EXCEPTION 'La fase debe referenciar un guion publicado'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_guion_coherente_sim';
    END IF;
    IF v_guion_caso IS DISTINCT FROM prisaly_caso_participacion(v_participacion) THEN
      RAISE EXCEPTION 'El guion no pertenece al caso del intento'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_guion_coherente_sim';
    END IF;
    RETURN NEW;
  END IF;
  -- UPDATE
  IF NEW."idIntento" IS DISTINCT FROM OLD."idIntento" OR NEW."idGuion" IS DISTINCT FROM OLD."idGuion"
     OR NEW."fechaInicioExploracion" IS DISTINCT FROM OLD."fechaInicioExploracion" THEN
    RAISE EXCEPTION 'Identidad de la fase inmutable'
      USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_intento_sim';
  END IF;
  IF NOT (OLD."fase" = 'Exploracion' AND NEW."fase" = 'Cuestionario') THEN
    RAISE EXCEPTION 'Transicion de fase no permitida'
      USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_no_retrocede_sim';
  END IF;
  IF v_estado IS DISTINCT FROM 'EnCurso' THEN
    RAISE EXCEPTION 'La fase de un intento finalizado no cambia'
      USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_intento_en_curso_sim';
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER tr_fase_intento_sim
BEFORE INSERT OR UPDATE OR DELETE ON public."FaseIntento"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_fase_intento_sim();

-- TRUNCATE no dispara triggers de fila: se impide con uno de sentencia.
CREATE OR REPLACE FUNCTION public.prisaly_impedir_truncate_fase_sim()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, public AS $fn$
BEGIN
  RAISE EXCEPTION 'La fase de un intento no puede eliminarse'
    USING ERRCODE = '23514', CONSTRAINT = 'tr_fase_intento_no_delete_sim';
END
$fn$;

CREATE TRIGGER tr_fase_intento_no_truncate_sim
BEFORE TRUNCATE ON public."FaseIntento"
FOR EACH STATEMENT EXECUTE FUNCTION public.prisaly_impedir_truncate_fase_sim();
