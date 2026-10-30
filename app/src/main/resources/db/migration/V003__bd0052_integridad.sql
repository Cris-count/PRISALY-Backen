-- BEBD3-002 V003: integridad transversal sin columnas ni tablas nuevas.
BEGIN;
CREATE OR REPLACE FUNCTION prisaly_caso_pregunta(p uuid) RETURNS uuid LANGUAGE sql STABLE AS $$
 SELECT e."idCaso" FROM "Pregunta" q JOIN "Escenario" e ON e."idEscenario"=q."idEscenario" WHERE q."idPregunta"=p
$$;
CREATE OR REPLACE FUNCTION prisaly_caso_participacion(p uuid) RETURNS uuid LANGUAGE sql STABLE AS $$
 SELECT s."idCaso" FROM "Participacion" a JOIN "Convocatoria" c ON c."idConvocatoria"=a."idConvocatoria" JOIN "Sesion" s ON s."idSesion"=c."idSesion" WHERE a."idParticipacion"=p
$$;
CREATE OR REPLACE FUNCTION prisaly_validar_respuesta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_part uuid;
BEGIN
 SELECT "idParticipacion" INTO v_part FROM "Intento" WHERE "idIntento"=NEW."idIntento";
 IF prisaly_caso_pregunta(NEW."idPregunta") IS DISTINCT FROM prisaly_caso_participacion(v_part) THEN
  RAISE EXCEPTION 'Pregunta no pertenece al caso de la participación' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_respuesta_caso BEFORE INSERT OR UPDATE OF "idIntento","idPregunta","idOpcionSeleccionada" ON "Respuesta" FOR EACH ROW EXECUTE FUNCTION prisaly_validar_respuesta();
CREATE OR REPLACE FUNCTION prisaly_validar_rubricapregunta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_caso uuid;
BEGIN
 SELECT "idCaso" INTO v_caso FROM "Rubrica" WHERE "idRubrica"=NEW."idRubrica";
 IF prisaly_caso_pregunta(NEW."idPregunta") IS DISTINCT FROM v_caso THEN
  RAISE EXCEPTION 'Pregunta no pertenece al caso de la rúbrica' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_rubricapregunta_caso BEFORE INSERT OR UPDATE OF "idRubrica","idPregunta" ON "RubricaPregunta" FOR EACH ROW EXECUTE FUNCTION prisaly_validar_rubricapregunta();
CREATE OR REPLACE FUNCTION prisaly_validar_calificacion() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_part uuid; v_caso uuid;
BEGIN
 SELECT "idParticipacion" INTO v_part FROM "Intento" WHERE "idIntento"=NEW."idIntento";
 SELECT "idCaso" INTO v_caso FROM "Rubrica" WHERE "idRubrica"=NEW."idRubrica";
 IF v_caso IS DISTINCT FROM prisaly_caso_participacion(v_part) THEN
  RAISE EXCEPTION 'Rúbrica no pertenece al caso evaluado' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_calificacion_caso BEFORE INSERT OR UPDATE OF "idIntento","idRubrica" ON "Calificacion" FOR EACH ROW EXECUTE FUNCTION prisaly_validar_calificacion();
-- Serializar altas de intentos sobre Participacion: la aplicación debe realizar
-- SELECT ... FOR UPDATE en esa fila antes de verificar historial y crear Intento.
CREATE OR REPLACE FUNCTION prisaly_validar_intento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_aut "AutorizacionRepeticion"%ROWTYPE; v_prev boolean; v_est uuid; v_caso uuid;
BEGIN
 -- Serializa inserciones incluso desde clientes que no siguen el protocolo API.
 PERFORM 1 FROM "Participacion" WHERE "idParticipacion"=NEW."idParticipacion" FOR UPDATE;
 SELECT c."idEstudiante",s."idCaso" INTO v_est,v_caso FROM "Participacion" p JOIN "Convocatoria" c ON c."idConvocatoria"=p."idConvocatoria" JOIN "Sesion" s ON s."idSesion"=c."idSesion" WHERE p."idParticipacion"=NEW."idParticipacion";
 SELECT EXISTS(SELECT 1 FROM "Intento" i JOIN "Participacion" p ON p."idParticipacion"=i."idParticipacion" JOIN "Convocatoria" c ON c."idConvocatoria"=p."idConvocatoria" JOIN "Sesion" s ON s."idSesion"=c."idSesion" WHERE c."idEstudiante"=v_est AND s."idCaso"=v_caso AND i."idIntento"<>NEW."idIntento") INTO v_prev;
 IF (NEW."numeroIntento">1 OR v_prev) AND NEW."idAutorizacion" IS NULL THEN
  RAISE EXCEPTION 'Repetición exige autorización expresa' USING ERRCODE='23514';
 END IF;
 IF NEW."idAutorizacion" IS NOT NULL THEN
  SELECT * INTO v_aut FROM "AutorizacionRepeticion" WHERE "idAutorizacion"=NEW."idAutorizacion" FOR UPDATE;
  IF NOT FOUND OR v_aut."idParticipacion"<>NEW."idParticipacion" OR v_aut."estadoAutorizacion"<>'Vigente' OR v_aut."fechaAutorizacion">clock_timestamp() OR (v_aut."fechaVencimiento" IS NOT NULL AND v_aut."fechaVencimiento"<=clock_timestamp()) THEN
   RAISE EXCEPTION 'Autorización inválida o vencida' USING ERRCODE='23514';
  END IF;
  IF EXISTS(SELECT 1 FROM "Intento" WHERE "idAutorizacion"=NEW."idAutorizacion" AND "idIntento"<>NEW."idIntento") THEN
   RAISE EXCEPTION 'Autorización ya consumida' USING ERRCODE='23505';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_intento_autorizacion BEFORE INSERT ON "Intento" FOR EACH ROW EXECUTE FUNCTION prisaly_validar_intento();
-- Las calificaciones calculadas no se sobrescriben: correcciones requieren decisión y procedimiento auditado.
CREATE OR REPLACE FUNCTION prisaly_calificacion_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Calificación histórica inmutable; corrección requiere procedimiento autorizado' USING ERRCODE='23514';
END $$;
CREATE TRIGGER tr_calificacion_no_update BEFORE UPDATE OR DELETE ON "Calificacion" FOR EACH ROW EXECUTE FUNCTION prisaly_calificacion_inmutable();
COMMIT;
-- PENDIENTE DD-08: alcance de inmutabilidad de Caso/Escenario/Pregunta/OpcionRespuesta/Rubrica.
-- PENDIENTE DD-03: fórmula y cobertura exacta de rúbrica al publicar.
-- Seguridad de permisos/propiedad y no exposición de resultados se implementan en backend.
