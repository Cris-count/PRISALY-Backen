-- DC-BE-SIM-INCREMENTO-A-15 (Incremento A, M3): guion narrativo del caso.
-- CANDIDATA. Ubicacion db/sim-propuesta: NO forma parte del Flyway productivo (db/migration) ni de las
-- 21 tablas de BD-005.2. Solo se aplica en QA con los contratos bd0052-v013-sim-a o bd0052-v013-dd02b-sim-a.
-- Su promocion productiva requiere la decision D-ESQ-1 (orden respecto de V014-V016 de DD-02 B).
-- Sin FK hacia ni desde las tablas de DD-02 B: ambas ampliaciones son ortogonales.

CREATE TABLE public."GuionNarrativoCaso" (
  "idGuion" uuid NOT NULL,
  "idCaso" uuid NOT NULL,
  "version" int NOT NULL,
  "estado" varchar(25) NOT NULL DEFAULT 'Borrador',
  "definicion" jsonb NOT NULL,
  "hashDefinicion" char(64) NOT NULL,
  "idDocenteAutor" uuid NOT NULL,
  "fechaCreacion" timestamptz NOT NULL DEFAULT clock_timestamp(),
  "fechaActualizacion" timestamptz NOT NULL DEFAULT clock_timestamp(),
  "fechaPublicacion" timestamptz,
  CONSTRAINT "pk_guionnarrativocaso" PRIMARY KEY ("idGuion"),
  CONSTRAINT "uq_guionnarrativocaso_caso_version" UNIQUE ("idCaso", "version"),
  CONSTRAINT "fk_guionnarrativocaso_idcaso" FOREIGN KEY ("idCaso") REFERENCES public."Caso" ("idCaso") ON DELETE RESTRICT,
  CONSTRAINT "fk_guionnarrativocaso_autor" FOREIGN KEY ("idDocenteAutor") REFERENCES public."Usuario" ("idUsuario") ON DELETE RESTRICT,
  CONSTRAINT ck_guion_version CHECK ("version" >= 1),
  CONSTRAINT ck_guion_estado CHECK ("estado" IN ('Borrador', 'Publicado', 'Archivado')),
  CONSTRAINT ck_guion_hash CHECK ("hashDefinicion" ~ '^[0-9a-f]{64}$'),
  CONSTRAINT ck_guion_publicacion CHECK (("estado" = 'Borrador') = ("fechaPublicacion" IS NULL)),
  CONSTRAINT ck_guion_definicion_objeto CHECK (jsonb_typeof("definicion") = 'object')
);

-- Como maximo un guion publicado y un borrador por caso.
CREATE UNIQUE INDEX ux_guion_un_publicado ON public."GuionNarrativoCaso" ("idCaso") WHERE "estado" = 'Publicado';
CREATE UNIQUE INDEX ux_guion_un_borrador ON public."GuionNarrativoCaso" ("idCaso") WHERE "estado" = 'Borrador';

-- Inmutabilidad y bloqueo academico. Reutiliza la senal de V007 (Caso.fechaBloqueoAcademico, fijada en la primera
-- programacion) y el mismo bloqueo FOR UPDATE sobre Caso que comparten programacion y edicion de contenido, de modo
-- que publicar un guion y programar una sesion quedan serializados.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_guion_sim()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, public AS $fn$
DECLARE
  v_caso uuid := COALESCE(NEW."idCaso", OLD."idCaso");
  v_bloqueo timestamptz;
BEGIN
  SELECT "fechaBloqueoAcademico" INTO v_bloqueo FROM public."Caso" WHERE "idCaso" = v_caso FOR UPDATE;
  IF v_bloqueo IS NOT NULL THEN
    RAISE EXCEPTION 'El guion narrativo de un caso programado es inmutable'
      USING ERRCODE = '23514', CONSTRAINT = 'tr_guion_bloqueo_sim';
  END IF;
  IF TG_OP = 'DELETE' THEN
    IF OLD."estado" <> 'Borrador' THEN
      RAISE EXCEPTION 'Un guion publicado o archivado no puede eliminarse'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_guion_inmutable_sim';
    END IF;
    RETURN OLD;
  END IF;
  IF TG_OP = 'UPDATE' THEN
    IF NEW."idGuion" IS DISTINCT FROM OLD."idGuion" OR NEW."idCaso" IS DISTINCT FROM OLD."idCaso"
       OR NEW."version" IS DISTINCT FROM OLD."version" OR NEW."fechaCreacion" IS DISTINCT FROM OLD."fechaCreacion" THEN
      RAISE EXCEPTION 'Identidad del guion inmutable'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_guion_inmutable_sim';
    END IF;
    -- Transiciones permitidas: Borrador->Borrador (edicion), Borrador->Publicado, Publicado->Archivado.
    IF NOT ((OLD."estado" = 'Borrador' AND NEW."estado" IN ('Borrador', 'Publicado'))
            OR (OLD."estado" = 'Publicado' AND NEW."estado" = 'Archivado')) THEN
      RAISE EXCEPTION 'Transicion de guion no permitida'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_guion_inmutable_sim';
    END IF;
    IF OLD."estado" <> 'Borrador' AND (NEW."definicion" IS DISTINCT FROM OLD."definicion"
        OR NEW."hashDefinicion" IS DISTINCT FROM OLD."hashDefinicion"
        OR NEW."idDocenteAutor" IS DISTINCT FROM OLD."idDocenteAutor"
        OR NEW."fechaPublicacion" IS DISTINCT FROM OLD."fechaPublicacion") THEN
      RAISE EXCEPTION 'Un guion publicado es inmutable'
        USING ERRCODE = '23514', CONSTRAINT = 'tr_guion_inmutable_sim';
    END IF;
  END IF;
  RETURN NEW;
END
$fn$;

CREATE TRIGGER tr_guion_bloqueo_sim
BEFORE INSERT OR UPDATE OR DELETE ON public."GuionNarrativoCaso"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_guion_sim();
