-- Datos de prueba de M6 (DC-CLAUDE-BE-EJECUCION-04, B): la cadena con rúbrica y pesos COMPLETOS para la
-- estrategia ponderada candidata. 2.00 y 1.00 son datos de prueba, no pesos académicos ratificados. Cada
-- pregunta tiene exactamente una opción correcta (ux_opcion_max_una_correcta). La rúbrica y sus asociaciones
-- se insertan antes de programar la sesión, porque el contenido evaluable de un caso programado es inmutable (V007).
-- Datos de prueba de M5 (solo pruebas de integración). Variante con rúbrica (DC-BE-24H-01): la rúbrica se
-- inserta ANTES de programar la sesión, porque el contenido evaluable de un caso programado es inmutable (V007).
-- Crea, con identificadores únicos por prueba, la cadena mínima que exige
-- BD-005.2 (V001-V009) para que exista un Intento EnCurso: rol, usuarios,
-- estudiante, caso publicado con un escenario, dos preguntas y sus opciones,
-- sesión, convocatoria, participación e intento. Los marcadores ${...} los
-- reemplaza la prueba. No es contenido académico ni se usa fuera de pruebas.
INSERT INTO "Rol" ("idRol", "nombre", "descripcion") VALUES
  (${rolDocente}, 'M5IT-Doc-${sufijo}', 'Fixture de pruebas M5'),
  (${rolEstudiante}, 'M5IT-Est-${sufijo}', 'Fixture de pruebas M5');
INSERT INTO "Usuario" ("idUsuario", "idRol", "nombre", "correo") VALUES
  ('${docente}', ${rolDocente}, 'Docente fixture M5', 'm5it-doc-${sufijo}@prisaly.test'),
  ('${usuarioEstudiante}', ${rolEstudiante}, 'Estudiante fixture M5', 'm5it-est-${sufijo}@prisaly.test');
INSERT INTO "Estudiante" ("idEstudiante", "idUsuario") VALUES ('${estudiante}', '${usuarioEstudiante}');
INSERT INTO "Caso" ("idCaso", "idDocente", "nombre", "tiempoEstimadoSegundos", "estado") VALUES
  ('${caso}', '${docente}', 'Caso fixture M5 ${sufijo}', 600, 'Publicado');
INSERT INTO "Escenario" ("idEscenario", "idCaso", "orden") VALUES ('${escenario}', '${caso}', 1);
INSERT INTO "Pregunta" ("idPregunta", "idEscenario", "enunciado", "estadoEdicion", "orden") VALUES
  ('${pregunta1}', '${escenario}', 'Pregunta fixture 1', 'Lista', 1),
  ('${pregunta2}', '${escenario}', 'Pregunta fixture 2', 'Lista', 2);
INSERT INTO "OpcionRespuesta" ("idOpcion", "idPregunta", "texto", "orden", "esCorrecta") VALUES
  ('${opcion1a}', '${pregunta1}', 'Opción 1A', 1, false),
  ('${opcion1b}', '${pregunta1}', 'Opción 1B', 2, true),
  ('${opcion1c}', '${pregunta1}', 'Opción 1C', 3, false),
  ('${opcion2a}', '${pregunta2}', 'Opción 2A', 1, true);
INSERT INTO "Rubrica" ("idRubrica", "idCaso", "nombre", "definicionEvaluacion") VALUES
  ('${rubrica}', '${caso}', 'Rúbrica fixture M5 ${sufijo}', '{"fixture": "C2", "criterios": []}');
INSERT INTO "RubricaPregunta" ("idRubricaPregunta", "idRubrica", "idPregunta", "valorPregunta") VALUES
  (gen_random_uuid(), '${rubrica}', '${pregunta1}', 2.00),
  (gen_random_uuid(), '${rubrica}', '${pregunta2}', 1.00);
INSERT INTO "Sesion" ("idSesion", "idCaso", "idDocenteProgramador", "nombreEspacioAcademico",
    "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos") VALUES
  ('${sesion}', '${caso}', '${docente}', 'Espacio fixture M5', now() - interval '1 hour', now() + interval '1 hour', 3600);
INSERT INTO "Convocatoria" ("idConvocatoria", "idSesion", "idEstudiante") VALUES ('${convocatoria}', '${sesion}', '${estudiante}');
INSERT INTO "Participacion" ("idParticipacion", "idConvocatoria") VALUES ('${participacion}', '${convocatoria}');
INSERT INTO "Intento" ("idIntento", "idParticipacion", "numeroIntento", "inicio", "limiteTemporal", "estado") VALUES
  ('${intento}', '${participacion}', 1, now(), now() + interval '1 hour', 'EnCurso');
