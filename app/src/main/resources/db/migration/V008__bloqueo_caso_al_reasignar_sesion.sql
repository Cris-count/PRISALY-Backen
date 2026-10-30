-- PRISALY / BEBD Fase 4 / V008
-- Corrección incremental F3-03: reasignar una Sesion.idCaso también programa el caso destino.
-- Requiere V001..V007. No modifica tablas, columnas, estados ni funciones previas.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TRIGGER tr_sesion_bloqueo_reasignacion_f303
BEFORE UPDATE OF "idCaso" ON public."Sesion"
FOR EACH ROW
WHEN (NEW."idCaso" IS DISTINCT FROM OLD."idCaso")
EXECUTE FUNCTION public.prisaly_bloquear_caso_al_programar();

COMMIT;
