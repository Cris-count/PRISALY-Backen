-- PRISALY RF-09. Nueva migracion, no cambia V001-V008 ni las 21 tablas.
-- Requiere V001-V008. Validar en base aislada antes de despliegue.
BEGIN;
SET LOCAL lock_timeout='10s';
CREATE FUNCTION public.prisaly_validar_caso_programable_rf09()
RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $fn$
DECLARE v_estado text; v_activo boolean;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF NEW."idCaso" IS NOT DISTINCT FROM OLD."idCaso" THEN RETURN NEW; END IF;
 END IF;
 -- Mismo bloqueo de fila que V007/V008: serializa cambios de estado/activo.
 SELECT "estado","activo" INTO v_estado,v_activo FROM public."Caso"
 WHERE "idCaso"=NEW."idCaso" FOR UPDATE;
 IF NOT FOUND THEN RETURN NEW; END IF; -- diagnostico referencial por FK historica
 IF v_estado IS DISTINCT FROM 'Publicado' OR v_activo IS DISTINCT FROM true THEN
  RAISE EXCEPTION 'RF-09: solo se permite programar un caso Publicado y activo'
   USING ERRCODE='23514',CONSTRAINT='tr_sesion_rf09_caso_programable';
 END IF;
 RETURN NEW;
END $fn$;
-- El orden alfabetico ejecuta esta comprobacion antes de los triggers de bloqueo V007/V008.
CREATE TRIGGER tr_sesion_00_rf09_caso_programable
BEFORE INSERT OR UPDATE OF "idCaso" ON public."Sesion"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_validar_caso_programable_rf09();
COMMIT;
