# Mapa de PRISALY-Backen

Generado: 2026-11-25 23:31 · rama `docs/mapa-de-datos` · SHA `32922bb`

> Si `git -C PRISALY-Backen rev-parse --short HEAD` no da `32922bb`, este mapa está desactualizado: regenéralo con `node .claude/mapa/generar-mapa.mjs` antes de usarlo.
> El mapa dice **dónde** está cada cosa. Para saber **cómo** funciona, lee solo los archivos que señala.

## Módulos Maven

| Módulo | Clases (main) | Paquetes principales |
|---|---|---|
| app | 49 | `com.prisaly.config` (14), `com.prisaly.config.m6` (11), `com.prisaly.config.dc92` (7), `com.prisaly.web.evaluacion` (6), `com.prisaly.web.dc92` (5), `com.prisaly.web.reporte` (4) |
| m1 | 100 | `com.prisaly.m1.application` (41), `com.prisaly.m1.infrastructure` (28), `com.prisaly.m1.presentation` (17), `com.prisaly.m1.domain` (11), `com.prisaly.m1.api` (2), `com.prisaly.m1` (1) |
| m2 | 1 | `com.prisaly.m2` (1) |
| m3 | 100 | `com.prisaly.m3.infrastructure` (26), `com.prisaly.m3.application` (25), `com.prisaly.m3.presentation` (23), `com.prisaly.m3.domain` (17), `com.prisaly.m3.api` (8), `com.prisaly.m3` (1) |
| m4 | 61 | `com.prisaly.m4.infrastructure` (21), `com.prisaly.m4.presentation` (12), `com.prisaly.m4.domain` (10), `com.prisaly.m4.application` (9), `com.prisaly.m4.api` (8), `com.prisaly.m4` (1) |
| m5 | 122 | `com.prisaly.m5.application` (65), `com.prisaly.m5.infrastructure` (26), `com.prisaly.m5.domain` (16), `com.prisaly.m5.presentation` (13), `com.prisaly.m5.api` (1), `com.prisaly.m5` (1) |
| m6 | 36 | `com.prisaly.m6.application` (20), `com.prisaly.m6.domain` (7), `com.prisaly.m6.infrastructure` (7), `com.prisaly.m6.api` (1), `com.prisaly.m6` (1) |
| m7 | 43 | `com.prisaly.m7.application` (18), `com.prisaly.m7.infrastructure` (17), `com.prisaly.m7.domain` (7), `com.prisaly.m7` (1) |

## Endpoints (prefijo de contexto `/api`)

| Método | Ruta | Entrada (cuerpo) | Salida | Controlador:línea | Condición (flag) |
|---|---|---|---|---|---|
| GET | `/health` | — | `Map<String, String>` | com/prisaly/web/ApiStatusController.java:11 | — |
| POST | `/v1/docente/convocatorias/{idConvocatoria}/ausencia` | `SolicitudClasificacion` | `ParticipacionesDocenteDc92.Clasificacion` | com/prisaly/web/dc92/AusenciasDocenteController.java:42 | `ParticipacionesDc92Config.PROPIEDAD_DD13` |
| GET | `/v1/docente/convocatorias/{idConvocatoria}/ausencia` | — | `Historial` | com/prisaly/web/dc92/AusenciasDocenteController.java:50 | `ParticipacionesDc92Config.PROPIEDAD_DD13` |
| GET | `/v1/docente/sesiones/{idSesion}/participaciones` | — | `Listado` | com/prisaly/web/dc92/ParticipacionesDocenteController.java:33 | `ParticipacionesDc92Config.PROPIEDAD_DD09` |
| POST | `/v1/docente/participaciones/{idParticipacion}/repeticion` | `SolicitudRepeticion` | `AutorizacionHttp` | com/prisaly/web/dc92/RepeticionDocenteController.java:50 | `M5RepeticionDd04Config.PROPIEDAD` |
| POST | `/v1/docente/participaciones/{idParticipacion}/intento-valido` | `SolicitudSeleccion` | `RepeticionDocenteDd04.Seleccion` | com/prisaly/web/dc92/RepeticionDocenteController.java:59 | `M5RepeticionDd04Config.PROPIEDAD` |
| GET | `/v1/docente/participaciones/{idParticipacion}/repeticion` | — | `RepeticionDocenteDd04.Historial` | com/prisaly/web/dc92/RepeticionDocenteController.java:68 | `M5RepeticionDd04Config.PROPIEDAD` |
| POST | `/v1/docente/convocatorias/{idConvocatoria}/retiro` | `SolicitudRetiro` | `RetiroConvocatoriaRf21.Retiro` | com/prisaly/web/dc92/RetiroConvocatoriaController.java:39 | `RetiroRf21Config.PROPIEDAD` |
| GET | `/v1/docente/convocatorias/{idConvocatoria}/retiro` | — | `?` | com/prisaly/web/dc92/RetiroConvocatoriaController.java:47 | `RetiroRf21Config.PROPIEDAD` |
| GET | `/v1/estudiantes/intentos/{idIntento}/calificacion` | — | `?` | com/prisaly/web/evaluacion/CalificacionEstudianteController.java:60 | `M6LiberacionConfig.PROPIEDAD` |
| POST | `/v1/evaluacion/intentos/{idIntento}` | `SolicitudEvaluacionHttp` | `CalificacionHttp` | com/prisaly/web/evaluacion/EvaluacionDocenteController.java:55 | `prisaly.m6.evaluacion.enabled` |
| GET | `/v1/evaluacion/intentos/{idIntento}` | — | `?` | com/prisaly/web/evaluacion/EvaluacionDocenteController.java:65 | `prisaly.m6.evaluacion.enabled` |
| POST | `/v1/evaluacion/intentos/{idIntento}/liberacion` | — | `LiberacionHttp` | com/prisaly/web/evaluacion/LiberacionCalificacionController.java:38 | `M6LiberacionConfig.PROPIEDAD` |
| POST | `/v1/docente/participaciones/{idParticipacion}/retroalimentacion` | `SolicitudRetroalimentacion` | `RetroalimentacionGeneral` | com/prisaly/web/evaluacion/RetroalimentacionDocenteController.java:42 | `M6RetroalimentacionDd12Config.PROPIEDAD` |
| PUT | `/v1/docente/participaciones/{idParticipacion}/retroalimentacion` | `SolicitudRetroalimentacion` | `RetroalimentacionGeneral` | com/prisaly/web/evaluacion/RetroalimentacionDocenteController.java:50 | `M6RetroalimentacionDd12Config.PROPIEDAD` |
| GET | `/v1/docente/participaciones/{idParticipacion}/retroalimentacion` | — | `RetroalimentacionGeneral` | com/prisaly/web/evaluacion/RetroalimentacionDocenteController.java:56 | `M6RetroalimentacionDd12Config.PROPIEDAD` |
| GET | `/v1/docente/sesiones/{idSesion}/reporte-participacion` | — | — | com/prisaly/web/reporte/ReporteParticipacionController.java:47 | `ReporteParticipacionController.PROPIEDAD` |
| POST | `/v1/admin/docentes/{idUsuario}/activacion` | — | `Void` | com/prisaly/m1/presentation/ActivacionDocenteController.java:47 | `prisaly.m1.activacion-docente.enabled` |
| DELETE | `/v1/admin/docentes/{idUsuario}/activacion` | — | `Void` | com/prisaly/m1/presentation/ActivacionDocenteController.java:53 | `prisaly.m1.activacion-docente.enabled` |
| POST | `/v1/personal/activar` | `ActivacionRequest` | `Void` | com/prisaly/m1/presentation/ActivacionDocenteController.java:59 | `prisaly.m1.activacion-docente.enabled` |
| POST | `/v1/admin/docentes` | `DocenteAltaRequest` | `UsuarioDocente` | com/prisaly/m1/presentation/AdministracionController.java:70 | — |
| GET | `/v1/admin/roles` | — | `Listado<RolResumen>` | com/prisaly/m1/presentation/AdministracionController.java:88 | — |
| GET | `/v1/admin/permisos` | — | `Listado<PermisoResumen>` | com/prisaly/m1/presentation/AdministracionController.java:93 | — |
| GET | `/v1/admin/usuarios/{idUsuario}/permisos` | — | `Listado<PermisoResumen>` | com/prisaly/m1/presentation/AdministracionController.java:98 | — |
| PUT | `/v1/admin/usuarios/{idUsuario}/permisos/{idPermiso}` | — | `AsignacionPermisoUsuario` | com/prisaly/m1/presentation/AdministracionController.java:103 | — |
| DELETE | `/v1/admin/usuarios/{idUsuario}/permisos/{idPermiso}` | — | `Void` | com/prisaly/m1/presentation/AdministracionController.java:109 | — |
| POST | `/v1/admin/estudiantes` | `EstudianteAltaRequest` | `EstudianteRegistrado` | com/prisaly/m1/presentation/AdministracionEstudiantesController.java:47 | `prisaly.m1.alta-estudiantes.enabled` |
| GET | `/v1/admin/estudiantes` | — | `Listado<EstudianteRegistrado>` | com/prisaly/m1/presentation/AdministracionEstudiantesController.java:53 | `prisaly.m1.alta-estudiantes.enabled` |
| POST | `/v1/acceso-estudiante/solicitar` | `Map<String, Object>` | `?` | com/prisaly/m1/presentation/EstudianteCuentaController.java:67 | — |
| POST | `/v1/acceso-estudiante/validar` | `Map<String, Object>` | `?` | com/prisaly/m1/presentation/EstudianteCuentaController.java:81 | — |
| POST | `/v1/acceso-estudiante/cerrar-sesion` | — | `?` | com/prisaly/m1/presentation/EstudianteCuentaController.java:103 | — |
| POST | `/v1/personal/autenticar` | `PersonalAutenticarRequest` | `PersonalAutenticarResponse` | com/prisaly/m1/presentation/PersonalAuthController.java:30 | — |
| POST | `/v1/personal/cerrar-sesion` | — | `Void` | com/prisaly/m1/presentation/PersonalAuthController.java:51 | — |
| GET | `/v1/personal/autorizacion/crear-caso` | — | `Map<String, Object>` | com/prisaly/m1/presentation/PersonalAuthorizationController.java:29 | — |
| PUT | `/v1/personal/contrasena` | `CambioContrasenaRequest` | `Void` | com/prisaly/m1/presentation/PersonalContrasenaController.java:32 | — |
| GET | `/v1/personal/me` | — | `PersonalAutenticarResponse` | com/prisaly/m1/presentation/PersonalSessionController.java:23 | — |
| GET | `/v1/casos/{idCaso}/contenido` | — | `CasoContenidoResponse` | com/prisaly/m3/presentation/CasoContenidoConsultaController.java:40 | — |
| GET | `/v1/casos/{idCaso}/escenarios` | — | `List<CasoContenidoResponse.EscenarioContenido>` | com/prisaly/m3/presentation/CasoContenidoConsultaController.java:45 | — |
| GET | `/v1/casos/{idCaso}/escenarios/{idEscenario}` | — | `CasoContenidoResponse.EscenarioContenido` | com/prisaly/m3/presentation/CasoContenidoConsultaController.java:50 | — |
| GET | `/v1/casos/{idCaso}/preguntas/{idPregunta}` | — | `CasoContenidoResponse.PreguntaContenido` | com/prisaly/m3/presentation/CasoContenidoConsultaController.java:57 | — |
| GET | `/v1/casos/{idCaso}/preguntas/{idPregunta}/opciones` | — | `List<CasoContenidoResponse.OpcionContenido>` | com/prisaly/m3/presentation/CasoContenidoConsultaController.java:64 | — |
| POST | `/v1/casos/{idCaso}/escenarios` | `String` | `Map<String, String>` | com/prisaly/m3/presentation/CasoContenidoController.java:36 | — |
| POST | `/v1/casos/{idCaso}/escenarios/{idEscenario}/preguntas` | `String` | `Map<String, String>` | com/prisaly/m3/presentation/CasoContenidoController.java:42 | — |
| POST | `/v1/casos/{idCaso}/preguntas/{idPregunta}/opciones` | `String` | `Map<String, String>` | com/prisaly/m3/presentation/CasoContenidoController.java:50 | — |
| POST | `/v1/casos` | `String` | `CrearCasoResponse` | com/prisaly/m3/presentation/CasoCreationController.java:27 | — |
| GET | `/v1/casos/{idCaso}` | — | `CasoDocenteConsultaResponse` | com/prisaly/m3/presentation/CasoGestionController.java:25 | — |
| PATCH | `/v1/casos/{idCaso}` | `String` | `Void` | com/prisaly/m3/presentation/CasoGestionController.java:36 | — |
| GET | `/v1/casos` | — | `Listado` | com/prisaly/m3/presentation/CasoListadoController.java:36 | `prisaly.m3.listado.enabled` |
| GET | `/v1/casos/{idCaso}/narrativa` | — | `NarrativaDocenteDto` | com/prisaly/m3/presentation/CasoNarrativaController.java:49 | `prisaly.m3.narrativa.publicacion.enabled` |
| PUT | `/v1/casos/{idCaso}/narrativa` | `String` | `GuionDto` | com/prisaly/m3/presentation/CasoNarrativaController.java:57 | `prisaly.m3.narrativa.publicacion.enabled` |
| POST | `/v1/casos/{idCaso}/narrativa/publicar` | — | `GuionDto` | com/prisaly/m3/presentation/CasoNarrativaController.java:62 | `prisaly.m3.narrativa.publicacion.enabled` |
| POST | `/v1/casos/{idCaso}/publicar` | — | `Map<String, String>` | com/prisaly/m3/presentation/CasoPublicacionController.java:30 | — |
| POST | `/v1/casos/{idCaso}/archivar` | — | `Map<String, String>` | com/prisaly/m3/presentation/CasoPublicacionController.java:35 | — |
| GET | `/v1/casos/{idCaso}/rubrica` | — | `RubricaResponse` | com/prisaly/m3/presentation/CasoRubricaController.java:38 | — |
| PUT | `/v1/casos/{idCaso}/rubrica` | `String` | `RubricaResponse` | com/prisaly/m3/presentation/CasoRubricaController.java:43 | — |
| POST | `/m4/sesiones/{sesionId}/convocatorias` | `ConvocarEstudianteRequest` | `ConvocatoriaResponse` | com/prisaly/m4/presentation/controller/ConvocatoriaController.java:32 | — |
| GET | `/m4/sesiones/{sesionId}/convocatorias` | — | `List<ConvocatoriaResponse>` | com/prisaly/m4/presentation/controller/ConvocatoriaController.java:46 | — |
| GET | `/v1/estudiantes/convocatorias` | — | `List<ConvocatoriaEstudianteResponse>` | com/prisaly/m4/presentation/controller/ConvocatoriasEstudianteController.java:41 | — |
| POST | `/m4/sesiones` | `CrearSesionRequest` | `SesionResponse` | com/prisaly/m4/presentation/controller/SesionController.java:49 | — |
| GET | `/m4/sesiones/{sesionId}` | — | `SesionResponse` | com/prisaly/m4/presentation/controller/SesionController.java:70 | — |
| GET | `/m4/sesiones` | — | `List<SesionResponse>` | com/prisaly/m4/presentation/controller/SesionController.java:79 | — |
| PUT | `/m4/sesiones/{sesionId}` | `ActualizarSesionRequest` | `SesionResponse` | com/prisaly/m4/presentation/controller/SesionController.java:92 | — |
| PATCH | `/m4/sesiones/{sesionId}/estado` | `CambiarEstadoRequest` | `SesionResponse` | com/prisaly/m4/presentation/controller/SesionController.java:110 | — |
| GET | `/m4/sesiones/{sesionId}/acceso/{estudianteId}` | — | `Boolean` | com/prisaly/m4/presentation/controller/SesionController.java:126 | — |
| GET | `/v1/estudiantes/intentos/{idIntento}/contenido` | — | `ContenidoIntento` | com/prisaly/m5/presentation/rest/ContenidoIntentoEstudianteControlador.java:20 | — |
| GET | `/v1/estudiantes/intentos/{idIntento}/narrativa` | — | `Narrativa` | com/prisaly/m5/presentation/rest/FasesIntentoEstudianteControlador.java:59 | `FasesIntentoEstudianteControlador.PROPIEDAD_HTTP` |
| GET | `/v1/estudiantes/intentos/{idIntento}/fase` | — | `Fase` | com/prisaly/m5/presentation/rest/FasesIntentoEstudianteControlador.java:65 | `FasesIntentoEstudianteControlador.PROPIEDAD_HTTP` |
| POST | `/v1/estudiantes/intentos/{idIntento}/cuestionario` | — | `Cuestionario` | com/prisaly/m5/presentation/rest/FasesIntentoEstudianteControlador.java:71 | `FasesIntentoEstudianteControlador.PROPIEDAD_HTTP` |
| GET | `/v1/estudiantes/intentos/{idIntento}/completitud` | — | `Completitud` | com/prisaly/m5/presentation/rest/FasesIntentoEstudianteControlador.java:77 | `FasesIntentoEstudianteControlador.PROPIEDAD_HTTP` |
| GET | `/v1/estudiantes/intentos/{idIntento}` | — | `Intento` | com/prisaly/m5/presentation/rest/IntentosEstudianteM5Controlador.java:49 | — |
| PUT | `/v1/estudiantes/intentos/{idIntento}/respuestas/{idPregunta}` | `Guardar` | `RespuestaGuardada` | com/prisaly/m5/presentation/rest/IntentosEstudianteM5Controlador.java:55 | — |
| POST | `/v1/estudiantes/sesiones/{idSesion}/intentos` | — | `Inicio` | com/prisaly/m5/presentation/rest/IntentosM5Controlador.java:54 | `IntentosM5Controlador.PROPIEDAD_PUBLICACION` |
| POST | `/v1/estudiantes/intentos/{idIntento}/finalizar` | — | `Finalizacion` | com/prisaly/m5/presentation/rest/IntentosM5Controlador.java:62 | `IntentosM5Controlador.PROPIEDAD_PUBLICACION` |

## Flags `enabled` de `app/src/main/resources/application.yml`

| Clave | Valor por defecto | Variable de entorno | Línea |
|---|---|---|---|
| `spring.flyway.enabled` | true | — | 19 |
| `prisaly.m1.student-access.enabled` | false | — | 35 |
| `prisaly.m1.alta-estudiantes.enabled` | false | `PRISALY_M1_ALTA_ESTUDIANTES_ENABLED` | 40 |
| `prisaly.m1.activacion-docente.enabled` | false | `PRISALY_M1_ACTIVACION_DOCENTE_ENABLED` | 58 |
| `prisaly.m3.narrativa.publicacion.enabled` | false | — | 78 |
| `prisaly.m3.listado.enabled` | false | — | 87 |
| `prisaly.m5.repeticion.enabled` | false | `PRISALY_M5_REPETICION_ENABLED` | 96 |
| `prisaly.m5.inicio-finalizacion.publicacion.enabled` | false | — | 102 |
| `prisaly.m5.exploracion.fases.enabled` | false | — | 108 |
| `prisaly.m5.exploracion.fases.http.enabled` | false | — | 113 |
| `prisaly.m4.consulta-bd0052.enabled` | true | — | 116 |
| `prisaly.m4.cierre-automatico.enabled` | false | `PRISALY_M4_CIERRE_AUTOMATICO_ENABLED` | 121 |
| `prisaly.m4.consulta-participaciones.enabled` | false | `PRISALY_M4_CONSULTA_PARTICIPACIONES_ENABLED` | 126 |
| `prisaly.m4.ausencias.enabled` | false | `PRISALY_M4_AUSENCIAS_ENABLED` | 129 |
| `prisaly.m4.retiro.enabled` | false | `PRISALY_M4_RETIRO_ENABLED` | 132 |
| `prisaly.m6.evaluacion.enabled` | false | — | 138 |
| `prisaly.m6.liberacion.enabled` | false | — | 144 |
| `prisaly.m6.retroalimentacion.enabled` | false | `PRISALY_M6_RETROALIMENTACION_ENABLED` | 148 |
| `prisaly.m7.worker.enabled` | false | — | 159 |
| `prisaly.m7.reportes.participacion.enabled` | false | — | 166 |
| `prisaly.m7.notificacion-calificacion.enabled` | false | — | 171 |

## Scripts SQL (`db/`)

- `app/src/main/resources/db/d05-propuesta/`: V020__m1_activacion_docente.sql
- `app/src/main/resources/db/dc92-propuesta/`: V021__dc92_dd04_seleccion_intento.sql, V022__dc92_dd13_clasificacion_ausencia.sql, V023__dc92_rf21_retiro_convocatoria.sql
- `app/src/main/resources/db/dd02-propuesta/`: V011__acceso_estudiante_cuenta.sql, V012__dd02_integridad_vinculo.sql
- `app/src/main/resources/db/dd02b-propuesta/`: V014__dd02b_credencial_y_sesion_estudiante.sql, V015__dd02b_integridad_vinculo.sql, V016__m7_entrega_codigo_acceso.sql
- `app/src/main/resources/db/m6-propuesta/`: V019__m6_liberacion_calificacion.sql
- `app/src/main/resources/db/migration/`: V001__bd0052_estructura.sql, V002__bd0052_restricciones.sql, V003__bd0052_integridad.sql, V004__bd0052_integridad_intentos.sql, V005__bd0052_cierre_integridad.sql, V006__bd0052_proteccion_relaciones.sql, V007__fase3_politicas_aprobadas.sql, V008__bloqueo_caso_al_reasignar_sesion.sql, V009__rf09_solo_casos_publicados.sql, V013__m1_inicializacion_administrador.sql
- `app/src/main/resources/db/sim-propuesta-b/`: V018__sim_fase_intento.sql
- `app/src/main/resources/db/sim-propuesta/`: V017__sim_guion_narrativo.sql
- `m1/src/test/resources/db/`: m1-bd0052-fixture.sql
- `m4/src/main/resources/db/migration/`: V010__m4_sesiones_convocatorias.sql
- `m5/src/test/resources/db/`: m5-cadena-convocatoria.sql, m5-cadena-intento-con-rubrica.sql, m5-cadena-intento-rubrica-completa.sql, m5-cadena-intento.sql
