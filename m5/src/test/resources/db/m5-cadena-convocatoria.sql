-- Datos de prueba de M5 (solo pruebas de integración).
-- Igual que m5-cadena-intento.sql, pero termina en la Convocatoria: sin
-- Participacion ni Intento, para probar el primer inicio. Los marcadores ${...}
-- los reemplaza la prueba. No es contenido académico ni se usa fuera de pruebas.
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
INSERT INTO "OpcionRespuesta" ("idOpcion", "idPregunta", "texto", "orden") VALUES
  ('${opcion1a}', '${pregunta1}', 'Opción 1A', 1),
  ('${opcion1b}', '${pregunta1}', 'Opción 1B', 2),
  ('${opcion1c}', '${pregunta1}', 'Opción 1C', 3),
  ('${opcion2a}', '${pregunta2}', 'Opción 2A', 1);
INSERT INTO "Sesion" ("idSesion", "idCaso", "idDocenteProgramador", "nombreEspacioAcademico",
    "inicioPrevisto", "finPrevisto", "tiempoMaximoParticipacionSegundos") VALUES
  ('${sesion}', '${caso}', '${docente}', 'Espacio fixture M5', now() - interval '1 hour', now() + interval '1 hour', 3600);
INSERT INTO "Convocatoria" ("idConvocatoria", "idSesion", "idEstudiante") VALUES ('${convocatoria}', '${sesion}', '${estudiante}');
