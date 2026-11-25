-- N2 (M6-R10, DC-43) CANDIDATE — DC-BE-CIERRE-DEFINITIVO-87. Content of the B2 proposal of DC-BE-M6-DESBLOQUEO-IMPLEMENTACION-55
-- (propuesta-B2/V019__m6_liberacion_calificacion.sql.propuesta) without changes to its DDL. NOT in db/migration: Flyway
-- applies it only where SPRING_FLYWAY_LOCATIONS adds classpath:db/m6-propuesta. Its number and promotion stay subject
-- to D-ESQ-1. Not authorized for shared environments.
CREATE TABLE public."LiberacionCalificacion" (
  "idCalificacion"  uuid        NOT NULL,
  "idDocente"       uuid        NOT NULL,
  "fechaLiberacion" timestamptz NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT "pk_liberacioncalificacion" PRIMARY KEY ("idCalificacion"),
  CONSTRAINT "fk_liberacion_calificacion" FOREIGN KEY ("idCalificacion") REFERENCES public."Calificacion" ("idCalificacion"),
  CONSTRAINT "fk_liberacion_docente" FOREIGN KEY ("idDocente") REFERENCES public."Usuario" ("idUsuario")
);

-- Liberar es irreversible: ni UPDATE ni DELETE (mismo criterio que tr_calificacion_no_update de V003).
CREATE OR REPLACE FUNCTION prisaly_liberacion_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'La liberacion de una calificacion es inmutable' USING ERRCODE = '55000';
END $$;
CREATE TRIGGER tr_liberacion_no_update BEFORE UPDATE OR DELETE ON public."LiberacionCalificacion"
  FOR EACH ROW EXECUTE FUNCTION prisaly_liberacion_inmutable();

-- Integridad mínima: solo libera el docente creador del caso de la calificación (M6-R6, defensa en profundidad;
-- la aplicación lo verifica antes con M1 + M3).
CREATE OR REPLACE FUNCTION prisaly_validar_liberacion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE autor uuid;
BEGIN
  SELECT c."idDocente" INTO autor
  FROM public."Calificacion" k
  JOIN public."Rubrica" r ON r."idRubrica" = k."idRubrica"
  JOIN public."Caso" c ON c."idCaso" = r."idCaso"
  WHERE k."idCalificacion" = NEW."idCalificacion";
  IF autor IS NULL OR autor <> NEW."idDocente" THEN
    RAISE EXCEPTION 'Solo el docente propietario del caso libera la calificacion' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tr_liberacion_propietario BEFORE INSERT ON public."LiberacionCalificacion"
  FOR EACH ROW EXECUTE FUNCTION prisaly_validar_liberacion();
