-- BEBD3-002 V002: solo restricciones no controversiales.
BEGIN;
CREATE UNIQUE INDEX ux_intento_unico_encurso ON "Intento" ("idParticipacion") WHERE "estado" = 'EnCurso';
CREATE UNIQUE INDEX ux_opcion_max_una_correcta ON "OpcionRespuesta" ("idPregunta") WHERE "esCorrecta" = true;
CREATE UNIQUE INDEX ux_usuario_correo_insensible ON "Usuario" (lower("correo"));
CREATE INDEX ix_envio_pendiente ON "EnvioCorreo" ("estadoEnvio", "proximoReintento");
CREATE INDEX ix_convocatoria_estudiante ON "Convocatoria" ("idEstudiante");
CREATE INDEX ix_sesion_caso ON "Sesion" ("idCaso");
ALTER TABLE "Caso" ADD CONSTRAINT ck_caso_tiempo CHECK ("tiempoEstimadoSegundos" > 0), ADD CONSTRAINT ck_caso_estado CHECK ("estado" IN ('Borrador','Publicado','Archivado'));
ALTER TABLE "Sesion" ADD CONSTRAINT ck_sesion_fechas CHECK ("finPrevisto" > "inicioPrevisto" AND ("fechaInicioReal" IS NULL OR "fechaFinReal" IS NULL OR "fechaFinReal" >= "fechaInicioReal")), ADD CONSTRAINT ck_sesion_tiempo CHECK ("tiempoMaximoParticipacionSegundos" > 0), ADD CONSTRAINT ck_sesion_estado CHECK ("estado" IN ('SinIniciar','EnCurso','Finalizado'));
ALTER TABLE "Pregunta" ADD CONSTRAINT ck_pregunta_estado CHECK ("estadoEdicion" IN ('Borrador','Lista'));
ALTER TABLE "Intento" ADD CONSTRAINT ck_intento_tiempos CHECK ("limiteTemporal" > "inicio" AND ("fin" IS NULL OR "fin" >= "inicio")), ADD CONSTRAINT ck_intento_numero CHECK ("numeroIntento" >= 1), ADD CONSTRAINT ck_intento_repeticion CHECK ("numeroIntento" = 1 OR "idAutorizacion" IS NOT NULL);
ALTER TABLE "Respuesta" ADD CONSTRAINT ck_respuesta_revision CHECK ("revision" >= 1);
ALTER TABLE "RubricaPregunta" ADD CONSTRAINT ck_rubricapregunta_valor CHECK ("valorPregunta" > 0);
ALTER TABLE "Rubrica" ADD CONSTRAINT ck_rubrica_total CHECK ("puntajeTotal" IS NULL OR "puntajeTotal" > 0);
ALTER TABLE "EnvioCorreo" ADD CONSTRAINT ck_envio_intentos CHECK ("numeroIntentos" >= 0);
ALTER TABLE "Escenario" ADD CONSTRAINT ck_escenario_orden CHECK ("orden" >= 1);
ALTER TABLE "Pregunta" ADD CONSTRAINT ck_pregunta_orden CHECK ("orden" >= 1);
ALTER TABLE "OpcionRespuesta" ADD CONSTRAINT ck_opcion_orden CHECK ("orden" >= 1);
ALTER TABLE "AutorizacionRepeticion" ADD CONSTRAINT ck_autorizacion_vencimiento CHECK ("fechaVencimiento" IS NULL OR "fechaVencimiento" > "fechaAutorizacion");
COMMIT;
-- NO APLICADOS: CHECK de estados sin catálogo ratificado (Intento, Participacion,
-- Convocatoria, Usuario, EnvioCorreo, estadoEvaluacion, motivoFinalizacion).
-- NO APLICADO: Calificacion.versionPolitica NOT NULL (atributo original nullable).
-- NO APLICADO: suma de ponderaciones / escala DD-03 ni bloqueo total DD-08.
