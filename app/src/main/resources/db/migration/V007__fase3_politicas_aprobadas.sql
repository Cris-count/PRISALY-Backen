-- PRISALY / Fase 4 / V007 / PostgreSQL 16
-- Requiere V001..V006. NO crea tablas ni columnas.
-- F3-01 estados, F3-02 consumo, F3-03 bloqueo del contenido evaluable.
-- F3-04 no requiere migracion: los tiempos positivos ya estan validados en V002.
-- IMPORTANTE: ejecutar en una base aislada; revisar datos existentes antes del despliegue.
BEGIN;
SET LOCAL lock_timeout = '10s';
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM public."Intento" WHERE "estado" NOT IN ('EnCurso','Finalizado')) THEN
   RAISE EXCEPTION 'V007: Intento contiene estados fuera del catálogo aprobado; corregir datos bajo decisión explícita';
 END IF;
 IF EXISTS (SELECT 1 FROM public."AutorizacionRepeticion" WHERE "estadoAutorizacion" NOT IN ('Vigente','Utilizada','Revocada','Vencida')) THEN
   RAISE EXCEPTION 'V007: autorizaciones contienen estados no aprobados; revisión previa obligatoria';
 END IF;
 IF EXISTS (SELECT 1 FROM public."Sesion" s JOIN public."Caso" c ON c."idCaso"=s."idCaso" WHERE c."fechaBloqueoAcademico" IS NULL) THEN
   RAISE EXCEPTION 'V007: existen casos previamente programados sin fechaBloqueoAcademico; conciliar historial antes de migrar';
 END IF;
END $$;
ALTER TABLE public."Intento" ADD CONSTRAINT ck_intento_estado_f301 CHECK ("estado" IN ('EnCurso','Finalizado'));
ALTER TABLE public."AutorizacionRepeticion" ADD CONSTRAINT ck_autorizacion_estado_f302 CHECK ("estadoAutorizacion" IN ('Vigente','Utilizada','Revocada','Vencida'));

-- Al insertar un intento repetido, V005 ya valida y bloquea la autorización.
-- Este AFTER INSERT ejecuta el consumo en la misma transacción; cualquier error revierte ambos.
CREATE OR REPLACE FUNCTION public.prisaly_consumir_autorizacion_intento()
RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, public AS $fn$
BEGIN
  IF NEW."idAutorizacion" IS NOT NULL THEN
    UPDATE public."AutorizacionRepeticion"
       SET "estadoAutorizacion"='Utilizada'
     WHERE "idAutorizacion"=NEW."idAutorizacion"
       AND "idParticipacion"=NEW."idParticipacion"
       AND "estadoAutorizacion"='Vigente'
       AND "fechaAutorizacion"<=clock_timestamp()
       AND ("fechaVencimiento" IS NULL OR "fechaVencimiento">clock_timestamp());
    IF NOT FOUND THEN
      RAISE EXCEPTION 'La autorización no pudo consumirse de manera atómica'
        USING ERRCODE='23514', CONSTRAINT='tr_autorizacion_consumo_f302';
    END IF;
  END IF;
  RETURN NEW;
END $fn$;
CREATE TRIGGER tr_autorizacion_consumo_f302
AFTER INSERT ON public."Intento"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_consumir_autorizacion_intento();

-- Una autorización consumida no puede revocarse, reabrirse ni pasar a vencida;
-- el control incluye cambios directos, no solo los originados en Intento.
CREATE OR REPLACE FUNCTION public.prisaly_validar_transicion_autorizacion()
RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog, public AS $fn$
BEGIN
 IF NEW."estadoAutorizacion" IS DISTINCT FROM OLD."estadoAutorizacion"
    AND (OLD."estadoAutorizacion" <> 'Vigente' OR NEW."estadoAutorizacion" NOT IN ('Utilizada','Revocada','Vencida')) THEN
   RAISE EXCEPTION 'Transición de autorización no permitida'
     USING ERRCODE='23514',CONSTRAINT='tr_autorizacion_transicion_f302';
 END IF;
 RETURN NEW;
END $fn$;
CREATE TRIGGER tr_autorizacion_transicion_f302
BEFORE UPDATE OF "estadoAutorizacion" ON public."AutorizacionRepeticion"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_validar_transicion_autorizacion();

-- Cierre de estado: actualización permitida EnCurso->Finalizado, no a la inversa.
-- La consistencia fin/motivo y las evaluaciones son responsabilidad del servicio
-- de finalización; no se inventa un catálogo de otros motivos.

-- DD-08: la primera programación confirmada bloquea el contenido académico.
-- Comparten lock FOR UPDATE sobre Caso la programación y toda edición de contenido.
CREATE OR REPLACE FUNCTION public.prisaly_bloquear_caso_al_programar()
RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $fn$
DECLARE v_fecha timestamptz;
BEGIN
 SELECT "fechaBloqueoAcademico" INTO v_fecha
   FROM public."Caso" WHERE "idCaso"=NEW."idCaso" FOR UPDATE;
 IF NOT FOUND THEN RETURN NEW; END IF; -- deja el diagnóstico a la FK
 IF v_fecha IS NULL THEN
   UPDATE public."Caso" SET "fechaBloqueoAcademico"=clock_timestamp()
    WHERE "idCaso"=NEW."idCaso" AND "fechaBloqueoAcademico" IS NULL;
 END IF;
 RETURN NEW;
END $fn$;
CREATE TRIGGER tr_sesion_bloqueo_caso_f303
BEFORE INSERT ON public."Sesion" FOR EACH ROW
EXECUTE FUNCTION public.prisaly_bloquear_caso_al_programar();

-- La fecha de bloqueo no puede borrarse ni adelantarse/alterarse manualmente.
-- Únicamente transición NULL->fecha realizada por programación.
CREATE OR REPLACE FUNCTION public.prisaly_proteger_caso_f303()
RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $fn$
BEGIN
 IF TG_OP='DELETE' THEN
   IF OLD."fechaBloqueoAcademico" IS NOT NULL THEN
     RAISE EXCEPTION 'Caso bloqueado: eliminación prohibida'
       USING ERRCODE='23514',CONSTRAINT='tr_caso_contenido_f303';
   END IF;
   RETURN OLD;
 END IF;
 IF OLD."fechaBloqueoAcademico" IS NOT NULL THEN
   IF NEW."fechaBloqueoAcademico" IS DISTINCT FROM OLD."fechaBloqueoAcademico" OR
      (NEW."nombre",NEW."historia",NEW."desarrolloSituacional",NEW."tiempoEstimadoSegundos",NEW."idDocente")
      IS DISTINCT FROM
      (OLD."nombre",OLD."historia",OLD."desarrolloSituacional",OLD."tiempoEstimadoSegundos",OLD."idDocente") THEN
      RAISE EXCEPTION 'Contenido académico del caso bloqueado'
        USING ERRCODE='23514',CONSTRAINT='tr_caso_contenido_f303';
   END IF;
 ELSIF NEW."fechaBloqueoAcademico" IS NOT NULL THEN
   IF pg_trigger_depth() < 2 THEN
     RAISE EXCEPTION 'La fecha de bloqueo solo puede establecerse al programar sesión'
       USING ERRCODE='23514',CONSTRAINT='tr_caso_contenido_f303';
   END IF;
   -- La marca se establece por el trigger de Sesion, en cuyo contexto se permite
   -- pasar de NULL a fecha; otros clientes deben carecer de privilegio UPDATE directo.
   IF (NEW."nombre",NEW."historia",NEW."desarrolloSituacional",NEW."tiempoEstimadoSegundos",NEW."idDocente")
      IS DISTINCT FROM (OLD."nombre",OLD."historia",OLD."desarrolloSituacional",OLD."tiempoEstimadoSegundos",OLD."idDocente") THEN
      RAISE EXCEPTION 'No se permite modificar contenido simultáneamente con el bloqueo'
        USING ERRCODE='23514',CONSTRAINT='tr_caso_contenido_f303';
   END IF;
 END IF;
 RETURN NEW;
END $fn$;
CREATE TRIGGER tr_caso_contenido_f303 BEFORE UPDATE OR DELETE ON public."Caso"
FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_caso_f303();

-- Obtiene el caso afectado de cada tabla de contenido. Bloquea la fila de Caso
-- para serializar la edición con el alta de Sesion.
CREATE OR REPLACE FUNCTION public.prisaly_caso_contenido_f303(p_tabla text,p_id uuid)
RETURNS uuid LANGUAGE plpgsql STABLE SET search_path=pg_catalog,public AS $fn$
DECLARE v uuid;
BEGIN
 CASE p_tabla
 WHEN 'Escenario' THEN SELECT "idCaso" INTO v FROM public."Escenario" WHERE "idEscenario"=p_id;
 WHEN 'Pregunta' THEN SELECT e."idCaso" INTO v FROM public."Pregunta" q JOIN public."Escenario" e ON e."idEscenario"=q."idEscenario" WHERE q."idPregunta"=p_id;
 WHEN 'OpcionRespuesta' THEN SELECT e."idCaso" INTO v FROM public."OpcionRespuesta" o JOIN public."Pregunta" q ON q."idPregunta"=o."idPregunta" JOIN public."Escenario" e ON e."idEscenario"=q."idEscenario" WHERE o."idOpcion"=p_id;
 WHEN 'Rubrica' THEN SELECT "idCaso" INTO v FROM public."Rubrica" WHERE "idRubrica"=p_id;
 WHEN 'RubricaPregunta' THEN SELECT r."idCaso" INTO v FROM public."RubricaPregunta" rp JOIN public."Rubrica" r ON r."idRubrica"=rp."idRubrica" WHERE rp."idRubricaPregunta"=p_id;
 END CASE;
 RETURN v;
END $fn$;
CREATE OR REPLACE FUNCTION public.prisaly_proteger_contenido_f303()
RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,public AS $fn$
DECLARE v_old uuid; v_new uuid; v_caso uuid; v_bloqueo timestamptz;
BEGIN
 IF TG_TABLE_NAME='Escenario' THEN
  IF TG_OP<>'INSERT' THEN v_old=OLD."idCaso"; END IF;
  IF TG_OP<>'DELETE' THEN v_new=NEW."idCaso"; END IF;
 ELSIF TG_TABLE_NAME='Pregunta' THEN
  IF TG_OP<>'INSERT' THEN v_old=public.prisaly_caso_contenido_f303('Pregunta',OLD."idPregunta"); END IF;
  IF TG_OP<>'DELETE' THEN SELECT "idCaso" INTO v_new FROM public."Escenario" WHERE "idEscenario"=NEW."idEscenario"; END IF;
 ELSIF TG_TABLE_NAME='OpcionRespuesta' THEN
  IF TG_OP<>'INSERT' THEN v_old=public.prisaly_caso_contenido_f303('OpcionRespuesta',OLD."idOpcion"); END IF;
  IF TG_OP<>'DELETE' THEN SELECT e."idCaso" INTO v_new FROM public."Pregunta" q JOIN public."Escenario" e ON e."idEscenario"=q."idEscenario" WHERE q."idPregunta"=NEW."idPregunta"; END IF;
 ELSIF TG_TABLE_NAME='Rubrica' THEN
  IF TG_OP<>'INSERT' THEN v_old=OLD."idCaso"; END IF;
  IF TG_OP<>'DELETE' THEN v_new=NEW."idCaso"; END IF;
 ELSIF TG_TABLE_NAME='RubricaPregunta' THEN
  IF TG_OP<>'INSERT' THEN v_old=public.prisaly_caso_contenido_f303('RubricaPregunta',OLD."idRubricaPregunta"); END IF;
  IF TG_OP<>'DELETE' THEN SELECT "idCaso" INTO v_new FROM public."Rubrica" WHERE "idRubrica"=NEW."idRubrica"; END IF;
 END IF;
 FOR v_caso IN SELECT DISTINCT u.id FROM unnest(ARRAY[v_old,v_new]) AS u(id) WHERE u.id IS NOT NULL ORDER BY u.id LOOP
  SELECT "fechaBloqueoAcademico" INTO v_bloqueo FROM public."Caso" WHERE "idCaso"=v_caso FOR UPDATE;
  IF v_bloqueo IS NOT NULL THEN
   RAISE EXCEPTION 'Contenido evaluable de caso programado es inmutable'
     USING ERRCODE='23514',CONSTRAINT='tr_contenido_bloqueo_f303';
  END IF;
 END LOOP;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $fn$;
CREATE TRIGGER tr_escenario_bloqueo_f303 BEFORE INSERT OR UPDATE OR DELETE ON public."Escenario" FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_contenido_f303();
CREATE TRIGGER tr_pregunta_bloqueo_f303 BEFORE INSERT OR UPDATE OR DELETE ON public."Pregunta" FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_contenido_f303();
CREATE TRIGGER tr_opcion_bloqueo_f303 BEFORE INSERT OR UPDATE OR DELETE ON public."OpcionRespuesta" FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_contenido_f303();
CREATE TRIGGER tr_rubrica_bloqueo_f303 BEFORE INSERT OR UPDATE OR DELETE ON public."Rubrica" FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_contenido_f303();
CREATE TRIGGER tr_rubricapregunta_bloqueo_f303 BEFORE INSERT OR UPDATE OR DELETE ON public."RubricaPregunta" FOR EACH ROW EXECUTE FUNCTION public.prisaly_proteger_contenido_f303();
COMMIT;
-- Seguridad: restringir el acceso directo a tablas a roles de aplicación;
-- la fecha de bloqueo debe ser establecida exclusivamente por el flujo autorizado.
-- NOTA: la clonación y el cálculo de notas requieren servicios de backend;
-- no están implementados por esta migración.
