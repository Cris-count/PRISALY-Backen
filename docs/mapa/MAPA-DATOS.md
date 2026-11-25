# Mapa de datos de PRISALY (Backend y Front)

Generado (Backend): 2026-11-25 23:31 · rama `docs/mapa-de-datos` · SHA `32922bb` · Front: rama `develop` · SHA `e23172b`

> Si `git -C PRISALY-Backen rev-parse --short HEAD` no da `32922bb`, este mapa está desactualizado: regenéralo con `node .claude/mapa/generar-mapa.mjs` antes de usarlo.
> El mapa dice **dónde** está cada cosa. Para saber **cómo** funciona, lee solo los archivos que señala.

Qué datos existen, dónde se guardan y cómo viajan: tablas de PostgreSQL (Flyway), entidades JPA, DTO y records de Spring, e interfaces del Front. Qué endpoint usa cada DTO está en el mapa del Backend (columnas Entrada y Salida).


## Modelo de datos: tablas

Columnas como `nombre tipo`; `?` = admite NULL; `(+Vxxx)` = columna añadida después. Las tablas de carpetas `*-propuesta` **no** están en Flyway productivo.

### ActivacionCuentaEstudiante  ·  V011 (dd02-propuesta)
- Columnas: `idActivacion uuid`, `idUsuario uuid`, `idEstudiante uuid`, `challengeId uuid`, `codigoHmac bytea`, `fechaCreacion timestamptz`, `fechaExpiracion timestamptz`, `fechaConsumo timestamptz?`, `fechaRevocacion timestamptz?`, `intentosFallidos int`
- PK: idActivacion
- FK: idUsuario → Usuario; idEstudiante → Estudiante
### ActivacionDocente  ·  V020 (d05-propuesta)
- Columnas: `hashToken char(64)`, `idUsuario uuid`, `idEmisor uuid`, `emitidoEn timestamptz`, `expiraEn timestamptz`, `consumidoEn timestamptz?`, `revocadoEn timestamptz?`
- PK: hashToken
- FK: idUsuario → Usuario
### AutorizacionRepeticion  ·  V001
- Columnas: `idAutorizacion uuid`, `idParticipacion uuid`, `idDocenteAutorizador uuid`, `motivo text?`, `fechaAutorizacion timestamptz`, `fechaVencimiento timestamptz?`, `estadoAutorizacion varchar(25)`
- PK: idAutorizacion
- FK: idParticipacion → Participacion; idDocenteAutorizador → Usuario
### Aviso  ·  V001  ·  entidad AvisoEntity (com/prisaly/m7/infrastructure/AvisoEntity.java)
- Columnas: `idAviso uuid`, `idUsuarioDestino uuid`, `idParticipacion uuid?`, `tipo varchar(50)`, `contenido text`, `claveEvento varchar(150)`, `fechaCreacion timestamptz`, `fechaLectura timestamptz?`
- PK: idAviso
- FK: idUsuarioDestino → Usuario; idParticipacion → Participacion
### Calificacion  ·  V001  ·  entidad CalificacionJpaEntity (com/prisaly/m6/infrastructure/persistence/jpa/CalificacionJpaEntity.java)
- Columnas: `idCalificacion uuid`, `idIntento uuid`, `idRubrica uuid`, `valor numeric(8,2)`, `fechaCalculo timestamptz`, `detalleEvaluacion jsonb`, `versionPolitica varchar(50)?`
- PK: idCalificacion
- FK: idIntento → Intento; idRubrica → Rubrica
### Caso  ·  V001  ·  entidad CasoJpaEntity (com/prisaly/m3/infrastructure/persistence/jpa/CasoJpaEntity.java)
- Columnas: `idCaso uuid`, `idDocente uuid`, `nombre varchar(150)`, `historia text?`, `desarrolloSituacional text?`, `tiempoEstimadoSegundos int`, `estado varchar(20)`, `activo boolean`, `fechaBloqueoAcademico timestamptz?`, `fechaCreacion timestamptz`, `fechaActualizacion timestamptz?`
- PK: idCaso
- FK: idDocente → Usuario
### Convocatoria  ·  V001  ·  entidad ConvocatoriaEntity (com/prisaly/m4/infrastructure/persistence/ConvocatoriaEntity.java)
- Columnas: `idConvocatoria uuid`, `idSesion uuid`, `idEstudiante uuid`, `fechaConvocatoria timestamptz`, `estadoAutorizacion varchar(25)`, `fechaRetiro timestamptz?`
- PK: idConvocatoria
- FK: idSesion → Sesion; idEstudiante → Estudiante
### convocatorias  ·  V010
- Columnas: `id uuid`, `sesion_id uuid`, `estudiante_id uuid`, `convocado_en timestamp`, `confirmada boolean`
- PK: id
- FK: sesion_id → sesiones_academicas
### CredencialAccesoEstudiante  ·  V014 (dd02b-propuesta)
- Columnas: `idCredencial uuid`, `idUsuario uuid`, `idEstudiante uuid`, `challengeId uuid`, `codigoHmac bytea`, `kidCodigo varchar(40)`, `fechaCreacion timestamptz`, `fechaExpiracion timestamptz`, `fechaConsumo timestamptz?`, `fechaRevocacion timestamptz?`, `intentosFallidos int`
- PK: idCredencial
- FK: idUsuario → Usuario; idEstudiante → Estudiante
### EntregaCodigoAcceso  ·  V016 (dd02b-propuesta)
- Columnas: `idEntrega uuid`, `challengeId uuid`, `claveIdempotencia varchar(80)`, `cargaCifrada bytea?`, `nonce bytea?`, `kidCifrado varchar(40)?`, `estado varchar(25)`, `intentos int`, `proximoIntento timestamptz`, `fechaCreacion timestamptz`, `fechaExpiracion timestamptz`, `fechaFinal timestamptz?`, `codigoError varchar(40)?`, `reclamadaHasta timestamptz?`
- PK: idEntrega
### EnvioCorreo  ·  V001  ·  entidad EnvioCorreoEntity (com/prisaly/m7/infrastructure/EnvioCorreoEntity.java)
- Columnas: `idEnvioCorreo uuid`, `idUsuarioDestino uuid`, `idConvocatoria uuid?`, `correoDestino varchar(255)`, `asunto varchar(255)`, `contenido text`, `estadoEnvio varchar(25)`, `numeroIntentos int`, `claveIdempotencia varchar(150)`, `ultimoError text?`, `fechaCreacion timestamptz`, `fechaUltimoIntento timestamptz?`, `proximoReintento timestamptz?`, `fechaEnvio timestamptz?`
- PK: idEnvioCorreo
- FK: idUsuarioDestino → Usuario; idConvocatoria → Convocatoria
### Escenario  ·  V001  ·  entidad EscenarioJpaEntity (com/prisaly/m3/infrastructure/persistence/jpa/EscenarioJpaEntity.java)
- Columnas: `idEscenario uuid`, `idCaso uuid`, `titulo varchar(200)?`, `contexto text?`, `instrucciones text?`, `orden int`, `activo boolean`
- PK: idEscenario
- FK: idCaso → Caso
### Estudiante  ·  V001
- Columnas: `idEstudiante uuid`, `idUsuario uuid`, `apodo varchar(80)?`, `avatarConfig jsonb?`, `fechaRegistro timestamptz`
- PK: idEstudiante
- FK: idUsuario → Usuario
### InicializacionAdministrador  ·  V013
- Columnas: `idInicializacion smallint`, `idUsuarioAdministrador uuid`, `fechaInicializacion timestamptz`, `origen varchar(40)`
- PK: idInicializacion
- FK: idUsuarioAdministrador → Usuario
### Intento  ·  V001  ·  entidad IntentoEntity (com/prisaly/m5/infrastructure/persistencia/IntentoEntity.java)
- Columnas: `idIntento uuid`, `idParticipacion uuid`, `numeroIntento int`, `inicio timestamptz`, `fin timestamptz?`, `limiteTemporal timestamptz`, `estado varchar(25)`, `estadoEvaluacion varchar(25)`, `motivoFinalizacion varchar(40)?`, `idAutorizacion uuid?`
- PK: idIntento
- FK: idParticipacion → Participacion; idAutorizacion, idParticipacion → AutorizacionRepeticion
### IntentoAccesoEstudiante  ·  V014 (dd02b-propuesta)
- Columnas: `idIntentoAcceso uuid`, `idEstudiante uuid?`, `evento varchar(20)`, `resultado varchar(30)`, `origen varchar(64)`, `fecha timestamptz`
- PK: idIntentoAcceso
### OpcionRespuesta  ·  V001  ·  entidad OpcionRespuestaJpaEntity (com/prisaly/m3/infrastructure/persistence/jpa/OpcionRespuestaJpaEntity.java)
- Columnas: `idOpcion uuid`, `idPregunta uuid`, `texto text`, `esCorrecta boolean`, `explicacion text?`, `orden int`
- PK: idOpcion
- FK: idPregunta → Pregunta
### Participacion  ·  V001
- Columnas: `idParticipacion uuid`, `idConvocatoria uuid`, `estado varchar(25)`, `fechaInicio timestamptz?`, `fechaFin timestamptz?`, `calificacionFinal numeric(8,2)?`, `detalleResultadoFinal jsonb?`
- PK: idParticipacion
- FK: idConvocatoria → Convocatoria
### Permiso  ·  V001  ·  entidad PermisoEntity (com/prisaly/m1/infrastructure/persistence/PermisoEntity.java)
- Columnas: `idPermiso integer?`, `codigo varchar(100)`, `nombre varchar(100)`, `descripcion text?`
- PK: idPermiso
### Pregunta  ·  V001  ·  entidad PreguntaJpaEntity (com/prisaly/m3/infrastructure/persistence/jpa/PreguntaJpaEntity.java)
- Columnas: `idPregunta uuid`, `idEscenario uuid`, `enunciado text`, `textoAyuda text?`, `estadoEdicion varchar(20)`, `visible boolean`, `orden int`, `fechaCreacion timestamptz`, `fechaActualizacion timestamptz?`
- PK: idPregunta
- FK: idEscenario → Escenario
### Respuesta  ·  V001  ·  entidad RespuestaEntity (com/prisaly/m5/infrastructure/persistencia/RespuestaEntity.java)
- Columnas: `idRespuesta uuid`, `idIntento uuid`, `idPregunta uuid`, `idOpcionSeleccionada uuid`, `revision int`, `fechaActualizacion timestamptz`
- PK: idRespuesta
- FK: idIntento → Intento; idPregunta → Pregunta; idOpcionSeleccionada, idPregunta → OpcionRespuesta
### RetroalimentacionGeneral  ·  V001  ·  entidad RetroalimentacionGeneralJpaEntity (com/prisaly/m6/infrastructure/persistence/jpa/RetroalimentacionGeneralJpaEntity.java)
- Columnas: `idRetroalimentacion uuid`, `idParticipacion uuid`, `idDocente uuid`, `contenido text`, `fechaRegistro timestamptz`
- PK: idRetroalimentacion
- FK: idParticipacion → Participacion; idDocente → Usuario
### Rol  ·  V001  ·  entidad RolEntity (com/prisaly/m1/infrastructure/persistence/RolEntity.java)
- Columnas: `idRol integer?`, `nombre varchar(30)`, `descripcion text`
- PK: idRol
### Rubrica  ·  V001  ·  entidad RubricaJpaEntity (com/prisaly/m3/infrastructure/persistence/jpa/RubricaJpaEntity.java)
- Columnas: `idRubrica uuid`, `idCaso uuid`, `nombre varchar(150)`, `descripcion text?`, `puntajeTotal numeric(8,2)?`, `definicionEvaluacion jsonb?`, `fechaCreacion timestamptz`
- PK: idRubrica
- FK: idCaso → Caso
### RubricaPregunta  ·  V001  ·  entidad RubricaPreguntaJpaEntity (com/prisaly/m3/infrastructure/persistence/jpa/RubricaPreguntaJpaEntity.java)
- Columnas: `idRubricaPregunta uuid`, `idRubrica uuid`, `idPregunta uuid`, `valorPregunta numeric(8,2)`, `criterio text?`
- PK: idRubricaPregunta
- FK: idRubrica → Rubrica; idPregunta → Pregunta
### Sesion  ·  V001  ·  entidad SesionAcademicaEntity (com/prisaly/m4/infrastructure/persistence/SesionAcademicaEntity.java)
- Columnas: `idSesion uuid`, `idCaso uuid`, `idDocenteProgramador uuid`, `nombreEspacioAcademico varchar(150)`, `inicioPrevisto timestamptz`, `finPrevisto timestamptz`, `fechaInicioReal timestamptz?`, `fechaFinReal timestamptz?`, `tiempoMaximoParticipacionSegundos int`, `lugarFisico varchar(200)?`, `mensajePersonalizado text?`, `codigoAutorizacionHash varchar(255)?`, `estado varchar(20)`, `fechaCreacion timestamptz`
- PK: idSesion
- FK: idCaso → Caso; idDocenteProgramador → Usuario
### sesiones_academicas  ·  V010
- Columnas: `id uuid`, `caso_id uuid`, `docente_id uuid`, `titulo varchar(200)`, `descripcion varchar(1000)?`, `fecha_inicio timestamp`, `fecha_fin timestamp`, `estado varchar(20)`, `creado_en timestamp`, `actualizado_en timestamp`, `version integer`
- PK: id
### SesionEstudiante  ·  V011 (dd02-propuesta), V014 (dd02b-propuesta)
- Columnas: `idSesionEstudiante uuid`, `idUsuario uuid`, `idEstudiante uuid`, `sesionTokenHmac bytea`, `fechaCreacion timestamptz`, `fechaExpiracion timestamptz`, `fechaRevocacion timestamptz?`, `idSesionEstudiante uuid`, `idUsuario uuid`, `idEstudiante uuid`, `sesionTokenHmac bytea`, `kidSesion varchar(40)`, `fechaCreacion timestamptz`, `fechaExpiracion timestamptz`, `fechaRevocacion timestamptz?`, `fechaUltimaActividad timestamptz`
- PK: idSesionEstudiante
- FK: idUsuario → Usuario; idEstudiante → Estudiante
### Usuario  ·  V001  ·  entidad UsuarioEntity (com/prisaly/m1/infrastructure/persistence/UsuarioEntity.java)
- Columnas: `idUsuario uuid`, `idRol int`, `nombre varchar(150)`, `correo varchar(255)`, `passwordHash varchar(255)?`, `estado varchar(20)`, `fechaCreacion timestamptz`, `fechaActualizacion timestamptz?`
- PK: idUsuario
- FK: idRol → Rol
### UsuarioPermiso  ·  V001  ·  entidad UsuarioPermisoEntity (com/prisaly/m1/infrastructure/persistence/UsuarioPermisoEntity.java)
- Columnas: `idUsuario uuid`, `idPermiso int`, `habilitado boolean`, `idAdministradorAsignador uuid?`, `fechaAsignacion timestamptz`
- PK: idUsuario, idPermiso
- FK: idUsuario → Usuario; idPermiso → Permiso; idAdministradorAsignador → Usuario

## Modelo de datos: DTO y records (por módulo)

**app** (28)
- `PoliticaLecturaCaso`(permiso: Optional<String>, estadosVisibles: Set<CasoEstado>, requiereActivo: boolean) — PoliticaLecturaCaso.java:16
- `Clasificacion`(idClasificacion: UUID, idConvocatoria: UUID, estado: String, idDocente: UUID, motivo: String, fechaRegistro: Instant) — ParticipacionesDocenteDc92.java:44
- `Seleccion`(idSeleccion: UUID, idParticipacion: UUID, idIntento: UUID, idDocente: UUID, motivo: String, fechaSeleccion: Instant) — RepeticionDocenteDd04.java:46
- `IntentoHistorial`(idIntento: UUID, numeroIntento: int, estado: String, inicio: Instant, fin: Instant, motivoFinalizacion: String, idAutorizacion: UUID) — RepeticionDocenteDd04.java:49
- `AutorizacionHistorial`(idAutorizacion: UUID, idDocenteAutorizador: UUID, motivo: String, fechaAutorizacion: Instant, estadoAutorizacion: String) — RepeticionDocenteDd04.java:52
- `Historial`(idParticipacion: UUID, intentos: List<IntentoHistorial>, autorizaciones: List<AutorizacionHistorial>, selecciones: List<Seleccion>, idIntentoValido: UUID) — RepeticionDocenteDd04.java:55
- `Retiro`(idConvocatoria: UUID, idDocente: UUID, motivo: String, resolucionIntento: String, idIntentoResuelto: UUID, fechaRetiro: Instant) — RetiroConvocatoriaRf21.java:40
- `Fila`(idConvocatoria: UUID, idEstudiante: UUID, nombreEstudiante: String, caso: String, sesion: String, fechaSesion: Instant, estadoConvocatoria: String, fechaRetiro: Instant, idPartici…) — VistaParticipacionDc92.java:32
- `Liberacion`(calificacion: Calificacion, idDocente: UUID, fechaLiberacion: Instant, nueva: boolean) — LiberacionCalificacionM6.java:30
- `CalificacionLiberada`(calificacion: Calificacion, fechaLiberacion: Instant) — LiberacionCalificacionM6.java:33
- `DetallePregunta`(idPregunta: UUID, enunciado: String, opcionSeleccionada: String, correcta: Boolean, explicacionSeleccionada: String, opcionCorrecta: String, explicacionCorrecta: String) — LiberacionCalificacionM6.java:120
- `SolicitudClasificacion`(estado: String, motivo: String) — AusenciasDocenteController.java:33
- `Historial`(items: List<ParticipacionesDocenteDc92.Clasificacion>) — AusenciasDocenteController.java:34
- `ErrorHttp`(code: String, message: String) — ErroresDc92.java:23
- `Listado`(items: List<VistaParticipacionDc92.Fila>) — ParticipacionesDocenteController.java:25
- `SolicitudRepeticion`(motivo: String) — RepeticionDocenteController.java:38
- `SolicitudSeleccion`(idIntento: UUID, motivo: String) — RepeticionDocenteController.java:39
- `AutorizacionHttp`(idAutorizacion: UUID, idParticipacion: UUID, idDocenteAutorizador: UUID, motivo: String, fechaAutorizacion: Instant, estadoAutorizacion: String) — RepeticionDocenteController.java:40
- `ErrorHttp`(code: String, message: String) — RepeticionDocenteController.java:42
- `SolicitudRetiro`(motivo: String, resolucionIntentoEnCurso: String) — RetiroConvocatoriaController.java:31
- `CalificacionEstudianteHttp`(idIntento: UUID, valor: BigDecimal, escala: String, caracter: String, fechaLiberacion: Instant, retroalimentacionGeneral: String, detallePorPregunta: java.util.List<LiberacionCali…) — CalificacionEstudianteController.java:39
- `ErrorHttp`(code: String, message: String) — CalificacionEstudianteController.java:43
- `ErrorEvaluacionHttp`(codigo: String, mensaje: String, motivo: String) — ErrorEvaluacionHttp.java:7
- `SolicitudEvaluacionHttp`(idRubrica: UUID) — EvaluacionDocenteController.java:33
- `CalificacionHttp`(idCalificacion: UUID, idIntento: UUID, idRubrica: UUID, valor: BigDecimal, escala: String, caracter: String, versionPolitica: String, fechaCalculo: Instant, liberada: boolean, det…) — EvaluacionDocenteController.java:36
- `LiberacionHttp`(idCalificacion: UUID, idIntento: UUID, valor: BigDecimal, escala: String, liberada: boolean, idDocenteLiberador: UUID, fechaLiberacion: Instant) — LiberacionCalificacionController.java:29
- `SolicitudRetroalimentacion`(contenido: String) — RetroalimentacionDocenteController.java:33
- `ErrorHttp`(code: String, message: String) — RetroalimentacionDocenteController.java:34

**m1** (22)
- `AuthenticatedStudent`(userId: UUID, studentId: UUID) — AuthenticatedStudent.java:9
- `AuthenticatedUser`(userId: UUID, email: String, displayName: String, roleName: String, accountStatus: String, permissionCodes: Set<String>) — AuthenticatedUser.java:9
- `CodigoAccesoDeliveryCommand`(challengeId: String, correoDestinatario: String, codigoTemporal: String, fechaExpiracion: Instant, claveIdempotencia: String) — CodigoAccesoDeliveryCommand.java:9
- `Resultado`(estado: Estado, idUsuarioAdministrador: UUID) — InicializacionAdministradorService.java:26
- `AuthResult`(accessToken: String, user: AuthenticatedUser) — PersonnelAuthenticationService.java:87
- `SesionEmitida`(token: String, expira: Instant) — ValidacionAccesoEstudianteService.java:107
- `ClavesAccesoEstudiante`(codigo: ConjuntoClavesHmac, sesion: ConjuntoClavesHmac) — ClavesAccesoEstudiante.java:7
- `Version`(kid: String, valor: byte[]) — ConjuntoClavesHmac.java:23
- `ActivacionRequest`(token: String, password: String) — ActivacionDocenteController.java:40
- `DocenteAltaRequest`(nombre: String, correo: String) — AdministracionController.java:52
- `UsuarioDocente`(idUsuario: UUID, nombre: String, correo: String, estado: String) — AdministracionController.java:55
- `RolResumen`(idRol: Integer, nombre: String) — AdministracionController.java:57
- `PermisoResumen`(idPermiso: Integer, codigo: String, nombre: String) — AdministracionController.java:60
- `Listado`(items: List<T>) — AdministracionController.java:66
- `AsignacionPermisoUsuario`(idUsuario: UUID, idPermiso: Integer, habilitado: boolean) — AdministracionController.java:68
- `EstudianteAltaRequest`(nombre: String, correo: String) — AdministracionEstudiantesController.java:42
- `Listado`(items: List<T>) — AdministracionEstudiantesController.java:45
- `SolicitudCodigoResponse`(challengeId: UUID, mensaje: String) — EstudianteCuentaController.java:64
- `CambioContrasenaRequest`(actual: String, nueva: String) — PersonalContrasenaController.java:30
- `ErrorResponse`(code: String, message: String) — ErrorResponse.java:3
- `PersonalAutenticarRequest`(correo: String, password: String) — PersonalAutenticarRequest.java:6
- `PersonalAutenticarResponse`(accessToken: String, tokenType: String, userId: UUID, correo: String, nombre: String, rol: String, permisos: Set<String>, authPrototype: String) — PersonalAutenticarResponse.java:6

**m3** (54)
- `ContextoAcademicoPersistido`(idCaso: UUID, idDocente: UUID, nombre: String, historia: String, desarrolloSituacional: String, tiempoEstimadoSegundos: int, estado: String, activo: boolean, fechaBloqueoAcademico…) — ContextoAcademicoPersistido.java:11
- `Escenario`(idEscenario: UUID, idCaso: UUID, titulo: String, contexto: String, instrucciones: String, orden: int, activo: boolean, preguntas: List<Pregunta>) — ContextoAcademicoPersistido.java:24
- `Pregunta`(idPregunta: UUID, idEscenario: UUID, enunciado: String, textoAyuda: String, estadoEdicion: String, visible: boolean, orden: int, fechaCreacion: Instant, fechaActualizacion: Instan…) — ContextoAcademicoPersistido.java:33
- `Opcion`(idOpcion: UUID, idPregunta: UUID, texto: String, esCorrecta: boolean, explicacion: String, orden: int) — ContextoAcademicoPersistido.java:46
- `Rubrica`(idRubrica: UUID, idCaso: UUID, nombre: String, descripcion: String, puntajeTotal: BigDecimal, definicionEvaluacion: String, fechaCreacion: Instant, preguntas: Set<RubricaPregunta>) — ContextoAcademicoPersistido.java:55
- `RubricaPregunta`(idRubricaPregunta: UUID, idRubrica: UUID, idPregunta: UUID, valorPregunta: BigDecimal, criterio: String) — ContextoAcademicoPersistido.java:67
- `CasoDocenteFicha`(idCaso: UUID, titulo: String, tiempoEstimadoSegundos: int, estado: CasoEstado) — CasoDocenteFicha.java:7
- `CasoDocenteFichaVersionada`(ficha: CasoDocenteFicha, fingerprint: String) — CasoDocenteFichaVersionada.java:6
- `VistaDocente`(idCaso: UUID, borrador: Optional<GuionGuardado>, publicado: Optional<GuionGuardado>) — GuionNarrativoService.java:60
- `CasoUpdateResult`(caso: Caso, fingerprint: String) — CasoUpdateResult.java:7
- `Caso`(id: String, titulo: String, docenteId: String, tiempoEstimadoSegundos: int, estado: CasoEstado, escenarios: List<Escenario>, rubrica: Rubrica, contenidoEvaluableBloqueado: boolean…) — Caso.java:12
- `CriterioRubrica`(id: String, descripcion: String, puntaje: int, ponderacion: BigDecimal) — CriterioRubrica.java:10
- `Escenario`(id: String, descripcion: String, preguntas: List<Pregunta>, titulo: String, contexto: String, instrucciones: String, orden: Integer, activo: boolean) — Escenario.java:10
- `OpcionRespuesta`(id: String, texto: String, esCorrecta: boolean, explicacion: String, orden: Integer) — OpcionRespuesta.java:7
- `Pregunta`(id: String, enunciado: String, opciones: List<OpcionRespuesta>, textoAyuda: String, estadoEdicion: String, visible: boolean, orden: Integer, fechaCreacion: Instant, fechaActualiza…) — Pregunta.java:11
- `Rubrica`(id: String, criterios: List<CriterioRubrica>, idCaso: String, nombre: String, descripcion: String, puntajeTotal: BigDecimal, definicionEvaluacion: String, fechaCreacion: Instant, …) — Rubrica.java:16
- `RubricaPregunta`(idRubricaPregunta: String, idRubrica: String, idPregunta: String, valorPregunta: BigDecimal, criterio: String) — RubricaPregunta.java:7
- `GuionNarrativo`(inicio: String, espacios: List<Espacio>, personajes: List<Personaje>, intervenciones: List<Intervencion>) — GuionNarrativo.java:17
- `Espacio`(idEspacio: String, titulo: String, descripcion: Optional<String>, recursoVisual: Optional<String>, informaciones: List<Informacion>, accesos: List<Acceso>) — GuionNarrativo.java:29
- `Informacion`(idInformacion: String, texto: String) — GuionNarrativo.java:37
- `Acceso`(destino: String, texto: String) — GuionNarrativo.java:39
- `Personaje`(idPersonaje: String, nombre: String, rol: Optional<String>, recursoVisual: Optional<String>, estadoInicial: Optional<String>, expresionInicial: Optional<Expresion>) — GuionNarrativo.java:41
- `Intervencion`(idIntervencion: String, idEspacio: String, idPersonaje: String, dialogos: List<Dialogo>, condicion: Optional<Condicion>, consecuencias: List<Consecuencia>) — GuionNarrativo.java:44
- `Dialogo`(idPersonaje: String, texto: String) — GuionNarrativo.java:52
- `Condicion`(marca: String, valor: boolean) — GuionNarrativo.java:54
- `ConsecuenciaMarca`(marca: String, valor: boolean) — GuionNarrativo.java:59
- `ConsecuenciaPersonaje`(idPersonaje: String, estado: String, expresion: Expresion) — GuionNarrativo.java:61
- `Error`(ruta: String, motivo: String) — GuionNarrativoInvalidoException.java:14
- `CasoJpaAggregateSnapshot`(caso: CasoRow, escenarios: List<EscenarioRow>, preguntas: List<PreguntaRow>, opciones: List<OpcionRespuestaRow>, rubrica: RubricaRow, rubricaPreguntas: Set<RubricaPreguntaRow>) — CasoJpaAggregateSnapshot.java:15
- `CasoRow`(idCaso: UUID, idDocente: UUID, nombre: String, historia: String, desarrolloSituacional: String, tiempoEstimadoSegundos: int, estado: String, activo: boolean, fechaBloqueoAcademico…) — CasoJpaAggregateSnapshot.java:31
- `EscenarioRow`(idEscenario: UUID, idCaso: UUID, titulo: String, contexto: String, instrucciones: String, orden: int, activo: boolean) — CasoJpaAggregateSnapshot.java:38
- `PreguntaRow`(idPregunta: UUID, idEscenario: UUID, enunciado: String, textoAyuda: String, estadoEdicion: String, visible: boolean, orden: int, fechaCreacion: Instant, fechaActualizacion: Instant) — CasoJpaAggregateSnapshot.java:43
- `OpcionRespuestaRow`(idOpcion: UUID, idPregunta: UUID, texto: String, esCorrecta: boolean, explicacion: String, orden: int) — CasoJpaAggregateSnapshot.java:49
- `RubricaRow`(idRubrica: UUID, idCaso: UUID, nombre: String, descripcion: String, puntajeTotal: BigDecimal, definicionEvaluacion: String, fechaCreacion: Instant) — CasoJpaAggregateSnapshot.java:54
- `RubricaPreguntaRow`(idRubricaPregunta: UUID, idRubrica: UUID, idPregunta: UUID, valorPregunta: BigDecimal, criterio: String) — CasoJpaAggregateSnapshot.java:59
- `ErrorResponse`(code: String, message: String) — CasoCreationExceptionHandler.java:17
- `ErrorResponse`(code: String, message: String) — CasoGestionExceptionHandler.java:12
- `Listado`(items: List<CasoDocenteConsultaResponse>) — CasoListadoController.java:27
- `ErrorResponse`(code: String, message: String) — CasoListadoController.java:28
- `GuionDto`(idGuion: UUID, version: int, estado: String, hashDefinicion: String, fechaActualizacion: Instant, fechaPublicacion: Instant, definicion: String) — CasoNarrativaController.java:39
- `NarrativaDocenteDto`(idCaso: UUID, borrador: GuionDto, publicado: GuionDto) — CasoNarrativaController.java:47
- `ErrorResponse`(code: String, message: String) — CasoNarrativaExceptionHandler.java:24
- `ErrorValidacion`(code: String, message: String, errores: List<GuionNarrativoInvalidoException.Error>) — CasoNarrativaExceptionHandler.java:26
- `CasoContenidoResponse`(idCaso: UUID, titulo: String, tiempoEstimadoSegundos: int, estado: String, historia: String, desarrolloSituacional: String, escenarios: List<EscenarioContenido>) — CasoContenidoResponse.java:15
- `EscenarioContenido`(idEscenario: String, titulo: String, contexto: String, instrucciones: String, orden: Integer, activo: boolean, preguntas: List<PreguntaContenido>) — CasoContenidoResponse.java:18
- `PreguntaContenido`(idPregunta: String, enunciado: String, textoAyuda: String, estadoEdicion: String, visible: boolean, orden: Integer, opciones: List<OpcionContenido>) — CasoContenidoResponse.java:27
- `OpcionContenido`(idOpcion: String, texto: String, esCorrecta: boolean, explicacion: String, orden: Integer) — CasoContenidoResponse.java:35
- `CasoDocenteConsultaResponse`(idCaso: UUID, titulo: String, tiempoEstimadoSegundos: int, estado: String) — CasoDocenteConsultaResponse.java:7
- `CasoPatchRequest`(fields: Map<String, Object>) — CasoPatchRequest.java:9
- `CriterioDto`(id: String, descripcion: String, puntaje: int, ponderacion: BigDecimal) — CasoRubricaDto.java:20
- `AsociacionDto`(idPregunta: String, valorPregunta: BigDecimal, criterio: String) — CasoRubricaDto.java:21
- `RubricaResponse`(idRubrica: String, nombre: String, descripcion: String, puntajeTotal: BigDecimal, completa: boolean, criterios: List<CriterioDto>, preguntas: List<AsociacionDto>) — CasoRubricaDto.java:22
- `CrearCasoRequest`(idCaso: UUID, titulo: String, tiempoEstimadoSegundos: Integer, historia: String, desarrolloSituacional: String) — CrearCasoRequest.java:13
- `CrearCasoResponse`(idCaso: UUID, estado: String) — CrearCasoResponse.java:5

**m4** (9)
- `ContextoProgramacion`(convocatoriaId: UUID, estudianteId: UUID, sesionId: UUID, casoId: UUID, estadoSesion: String, estadoAutorizacionConvocatoria: String, fechaRetiroConvocatoria: Optional<Instant>, e…) — ContextoProgramacion.java:19
- `ActualizarSesionRequest`(titulo: String, mensajePersonalizado: .fasterxml.jackson.annotation.JsonAlias("descripcion") String, fechaInicio: Instant, fechaFin: Instant, lugarFisico: String) — ActualizarSesionRequest.java:7
- `CambiarEstadoRequest`(nuevoEstado: EstadoSesion) — CambiarEstadoRequest.java:10
- `ConvocarEstudianteRequest`(estudianteId: UUID) — ConvocarEstudianteRequest.java:10
- `ConvocatoriaEstudianteResponse`(idConvocatoria: UUID, idSesion: UUID, idCaso: UUID, estadoSesion: String, estadoAutorizacion: String, inicioPrevisto: Instant, finPrevisto: Instant, fechaInicioReal: Instant, fech…) — ConvocatoriaEstudianteResponse.java:13
- `ConvocatoriaResponse`(id: UUID, sesionId: UUID, estudianteId: UUID, convocadoEn: Instant, estadoAutorizacion: String, fechaRetiro: Instant) — ConvocatoriaResponse.java:8
- `CrearSesionRequest`(casoId: UUID, titulo: String, mensajePersonalizado: .fasterxml.jackson.annotation.JsonAlias("descripcion") String, fechaInicio: Instant, fechaFin: Instant, tiempoMaximoParticipaci…) — CrearSesionRequest.java:8
- `ErrorResponse`(code: String, message: String) — ErrorResponse.java:4
- `SesionResponse`(id: UUID, casoId: UUID, docenteId: UUID, titulo: String, mensajePersonalizado: String, fechaInicio: Instant, fechaFin: Instant, estado: EstadoSesion, creadoEn: Instant, fechaInici…) — SesionResponse.java:8

**m5** (33)
- `ContenidoIntento`(idIntento: UUID, idSesion: UUID, caso: Caso) — ContenidoIntento.java:7
- `ReferenciaConvocatoria`(valor: String) — ReferenciaConvocatoria.java:10
- `ReferenciaIntento`(valor: String) — ReferenciaIntento.java:12
- `ReferenciaSesion`(valor: String) — ReferenciaSesion.java:10
- `ResultadoInicio`(intento: ResumenIntento, inicio: Instant, limiteTemporal: Instant, reanudado: boolean) — ResultadoInicio.java:16
- `ResumenIntento`(referencia: ReferenciaIntento, estado: EstadoIntento, motivoFinalizacion: Optional<MotivoFinalizacion>, respuestas: List<Respuesta>, tiempos: Optional<TiemposPersistidos>) — ResumenIntento.java:29
- `TiemposPersistidos`(inicio: Instant, limiteTemporal: Instant, fin: Instant) — ResumenIntento.java:37
- `IdentificadorEstudiante`(valor: String) — IdentificadorEstudiante.java:9
- `CompletitudIntento`(idIntento: UUID, preguntasPresentables: int, respondidas: int, sinResponder: List<UUID>) — CompletitudIntento.java:23
- `EntradaCuestionario`(idIntento: UUID, fechaInicioCuestionario: Instant, yaIniciado: boolean) — EntradaCuestionario.java:11
- `EstadoFase`(idIntento: UUID, estadoIntento: EstadoIntento, fase: FaseAcademica, fechaInicioExploracion: Optional<Instant>, fechaInicioCuestionario: Optional<Instant>, limiteTemporal: Instant,…) — EstadoFase.java:15
- `NarrativaIntento`(idIntento: UUID, idGuion: UUID, version: int, hashDefinicion: String, definicionJson: String) — NarrativaIntento.java:11
- `ReferenciaPregunta`(valor: String) — ReferenciaPregunta.java:10
- `Respuesta`(pregunta: ReferenciaPregunta, valor: ValorRespuesta, revision: int) — Respuesta.java:10
- `ValorRespuesta`(valor: String) — ValorRespuesta.java:10
- `FabricaConsultaIntentoEvaluacion`(autorizacion: AutorizacionEvaluacionPort, protegida: AccesoProtegidoParticipacion, repositorio: RepositorioIntentos, identidad: IdentidadEstudianteActual, programacion: Programaci…) — FabricaConsultaIntentoEvaluacion.java:14
- `FilaIntento`(estado: String, motivoFinalizacion: String) — MapeoIntento.java:44
- `FilaRespuesta`(idPregunta: UUID, idOpcionSeleccionada: UUID, revision: int) — MapeoIntento.java:48
- `CambioRespuesta`(idPregunta: UUID, idOpcionSeleccionada: UUID, nueva: boolean) — MapeoIntento.java:55
- `ErrorResponse`(code: String, message: String) — ErrorResponse.java:11
- `ErrorHttp`(estado: int, cuerpo: ErrorResponse) — TraductorErroresM5.java:44
- `IntentoVistaProvisional`(idIntento: String, estado: String, motivoFinalizacion: String, respuestas: List<RespuestaVista>) — IntentoVistaProvisional.java:18
- `RespuestaVista`(idPregunta: String, idOpcionSeleccionada: String) — IntentoVistaProvisional.java:25
- `SolicitudRespuestaProvisional`(idPregunta: String, idOpcionSeleccionada: String) — SolicitudRespuestaProvisional.java:18
- `Narrativa`(idIntento: UUID, idGuion: UUID, version: int, hashDefinicion: String, definicion: String) — FasesIntentoDto.java:22
- `Fase`(idIntento: UUID, estadoIntento: String, fase: String, fechaInicioExploracion: Instant, fechaInicioCuestionario: Instant, limiteTemporal: Instant, ahoraServidor: Instant) — FasesIntentoDto.java:33
- `Cuestionario`(idIntento: UUID, fase: String, fechaInicioCuestionario: Instant, yaIniciado: boolean) — FasesIntentoDto.java:49
- `Completitud`(idIntento: UUID, preguntasPresentables: int, respondidas: int, sinResponder: List<UUID>) — FasesIntentoDto.java:56
- `Guardar`(idOpcionSeleccionada: UUID, revisionEsperada: Integer) — IntentosM5Dto.java:21
- `RespuestaGuardada`(idPregunta: UUID, idOpcionSeleccionada: UUID, revision: int) — IntentosM5Dto.java:24
- `Intento`(idIntento: UUID, estado: String, inicio: Instant, limiteTemporal: Instant, motivoFinalizacion: String, respuestas: List<RespuestaGuardada>, ahoraServidor: Instant) — IntentosM5Dto.java:31
- `Inicio`(intento: Intento, reanudado: boolean) — IntentosM5Dto.java:46
- `Finalizacion`(idIntento: UUID, estado: String, fin: Instant, motivoFinalizacion: String) — IntentosM5Dto.java:48

**m6** (16)
- `SolicitudEvaluacion`(idIntento: UUID, idRubrica: UUID) — SolicitudEvaluacion.java:11
- `ContextoAcademico`(idCaso: UUID, idDocente: UUID, nombre: String, historia: String, desarrolloSituacional: String, tiempoEstimadoSegundos: int, estado: String, activo: boolean, fechaCreacion: Instan…) — ContextoAcademico.java:11
- `Escenario`(idEscenario: UUID, idCaso: UUID, titulo: String, contexto: String, instrucciones: String, orden: Integer, activo: boolean, preguntas: List<Pregunta>) — ContextoAcademico.java:27
- `Pregunta`(idPregunta: UUID, idEscenario: UUID, enunciado: String, textoAyuda: String, estadoEdicion: String, visible: boolean, orden: Integer, fechaCreacion: Instant, fechaActualizacion: In…) — ContextoAcademico.java:38
- `Opcion`(idOpcion: UUID, idPregunta: UUID, texto: String, esCorrecta: boolean, explicacion: String, orden: Integer) — ContextoAcademico.java:53
- `Rubrica`(idRubrica: UUID, idCaso: UUID, nombre: String, descripcion: String, puntajeTotal: BigDecimal, definicionEvaluacion: String, fechaCreacion: Instant, preguntas: Set<RubricaPregunta>) — ContextoAcademico.java:63
- `RubricaPregunta`(idRubricaPregunta: UUID, idRubrica: UUID, idPregunta: UUID, valorPregunta: BigDecimal, criterio: String) — ContextoAcademico.java:76
- `ContextoEvaluacionPreparado`(intento: IntentoEvaluacion, contextoAcademico: ContextoAcademico) — ContextoEvaluacionPreparado.java:6
- `IntentoEvaluacion`(idIntento: UUID, idParticipacion: UUID, idCaso: UUID, estado: EstadoIntento, respuestas: List<RespuestaReferencia>) — IntentoEvaluacion.java:8
- `RespuestaReferencia`(idPregunta: UUID, idOpcionSeleccionada: UUID) — IntentoEvaluacion.java:22
- `Calificacion`(idCalificacion: UUID, idIntento: UUID, idRubrica: UUID, valor: BigDecimal, fechaCalculo: Instant, detalleEvaluacion: String, versionPolitica: String) — Calificacion.java:13
- `RetroalimentacionGeneral`(idRetroalimentacion: UUID, idParticipacion: UUID, idDocente: UUID, contenido: String, fechaRegistro: Instant) — RetroalimentacionGeneral.java:8
- `EntradaEvaluacion`(solicitud: SolicitudEvaluacion, idCaso: UUID, preguntas: List<PreguntaEvaluable>, definicionEvaluacionRubrica: Optional<String>) — EntradaEvaluacion.java:27
- `PreguntaEvaluable`(idPregunta: UUID, idEscenario: UUID, escenarioActivo: boolean, visible: boolean, estadoEdicion: String, valorPregunta: Optional<BigDecimal>, opciones: List<OpcionEvaluable>, opcio…) — EntradaEvaluacion.java:44
- `OpcionEvaluable`(idOpcion: UUID, esCorrecta: boolean) — EntradaEvaluacion.java:85
- `ResultadoEvaluacion`(valor: BigDecimal, detalleEvaluacion: String) — ResultadoEvaluacion.java:11

**m7** (6)
- `NotificarConvocatoriaCommand`(sesionId: UUID, estudianteId: UUID) — NotificarConvocatoriaCommand.java:7
- `AvisoConvocatoria`(evento: ConvocatoriaNotificable, contenido: String) — AvisoConvocatoria.java:6
- `ConvocatoriaNotificable`(convocatoriaId: UUID, sesionId: UUID, estudianteId: UUID, usuarioDestino: UUID, correoDestino: String, nombreCaso: String, nombreSesion: String, inicioPrevisto: String, finPrevist…) — ConvocatoriaNotificable.java:10
- `EnvioCorreoConvocatoria`(evento: ConvocatoriaNotificable, asunto: String, contenido: String) — EnvioCorreoConvocatoria.java:8
- `EnvioReclamado`(idEnvio: UUID, correoDestino: String, asunto: String, contenido: String, numeroIntentos: int, leaseHasta: Instant) — EnvioReclamado.java:8
- `DeliveryRecord`(challengeId: String, correoDestinatario: String, codigoTemporal: String, fechaExpiracion: Instant, claveIdempotencia: String, estado: EstadoEntrega) — InMemoryStudentAccessDeliveryAdapter.java:75


## Modelo de datos del Front (interfaces y tipos)

De `*.models.ts`, `*.api.ts` y `*http*.ts`. Campos `?` = opcionales.

**features/academico/administracion-m1.api.ts**
- `DocenteAltaRequest`: nombre, correo
- `UsuarioDocente`: idUsuario, nombre, correo, estado
- `RolResumen`: idRol, nombre
- `PermisoResumen`: idPermiso, codigo, nombre
- `AsignacionPermisoUsuario`: idUsuario, idPermiso, habilitado
- `EstudianteAltaRequest`: nombre, correo
- `EstudianteRegistrado`: idUsuario, idEstudiante, nombre, correo, estado

**features/academico/casos-m3.api.ts**
- `CrearCasoRequest`: idCaso, titulo, tiempoEstimadoSegundos, historia?, desarrolloSituacional?
- `CrearCasoResponse`: idCaso, estado
- `CasoDocente`: idCaso, titulo, tiempoEstimadoSegundos, estado
- `Versionado`: datos, etag
- `CasoPatch`: titulo?, historia?, desarrolloSituacional?, tiempoEstimadoSegundos?
- `OpcionContenido`: idOpcion, texto, esCorrecta, explicacion, orden
- `PreguntaContenido`: idPregunta, enunciado, orden, textoAyuda?, estadoEdicion?, visible?, opciones?
- `EscenarioContenido`: idEscenario, titulo, orden, contexto?, instrucciones?, activo?, preguntas
- `AgregarEscenarioRequest`: idEscenario, titulo, contexto, instrucciones, orden
- `AgregarPreguntaRequest`: idPregunta, enunciado, textoAyuda, orden
- `AgregarOpcionRequest`: idOpcion, texto, esCorrecta, explicacion, orden
- `ElementoCreado`: id
- `CasoContenido` extends CasoDocente: historia, desarrolloSituacional, escenarios
- `CriterioRubrica`: id, descripcion, puntaje, ponderacion
- `AsociacionRubrica`: idPregunta, valorPregunta, criterio
- `Rubrica`: idRubrica, nombre, descripcion, puntajeTotal, completa, criterios, preguntas
- `GuardarRubrica`: nombre, descripcion, puntajeTotal, criterios, preguntas

**features/academico/participaciones-dc92.api.ts**
- `FilaParticipacion`: idConvocatoria, idEstudiante, nombreEstudiante, caso, sesion, fechaSesion, estadoConvocatoria, fechaRetiro, idParticipacion, fechaIngreso, numeroIntentos, estadoParticipacion, idIntentoConsiderado, i…
- `ReporteDescargado`: nombreArchivo, contenido

**features/academico/sesiones-m4.api.ts**
- `CrearSesionRequest`: casoId, titulo, mensajePersonalizado, fechaInicio, fechaFin, tiempoMaximoParticipacionSegundos, lugarFisico
- `ActualizarSesionRequest`: titulo, mensajePersonalizado, fechaInicio, fechaFin, lugarFisico
- `SesionAcademica`: id, casoId, docenteId, titulo, mensajePersonalizado, fechaInicio, fechaFin, estado, creadoEn, fechaInicioReal, fechaFinReal, tiempoMaximoParticipacionSegundos, lugarFisico
- `Convocatoria`: id, sesionId, estudianteId, convocadoEn, estadoAutorizacion, fechaRetiro
- tipo `EstadoSesion` = 'SinIniciar' | 'EnCurso' | 'Finalizado'

**features/autenticacion/m1-personal.models.ts**
- `PersonalAutenticarRequest`: correo, password
- `PersonalAutenticarResponse`: accessToken, tokenType, userId, correo, nombre, rol, permisos, authPrototype
- `PersonalMeResponse` extends Omit<PersonalAutenticarResponse, 'accessToken'>: accessToken
- `FalloSesionPersonal`: tipo, mensaje, reintentarEnSegundos?
- tipo `IdentidadPersonal` = Omit<PersonalAutenticarResponse, 'accessToken' | 'tokenType'>
- tipo `EstadoAccesoPersonal` = | { readonly tipo: | 'pendiente' | 'cargando' | 'denegado' | 'error' | 'consultando-sesion' | 'sesion-no-verificada' | 'cerrando-sesion' | …
- tipo `EstadoSesionPersonal` = | { readonly tipo: 'valida'

**features/simulador/exploracion/mapa.models.ts**
- `EspacioDemostrativo`: id, nombre, plantillaVisual, posicion, disponible
- `ConfiguracionMapa`: id, titulo, espacios
- `ContextoExterior`: seleccionado, visitas
- `ContextoInterior`: interaccionActiva, emitir
- `PlantillasEspacio`: exterior?, interior?
- tipo `FaseExploracion` = 'presentacion' | 'explorando' | 'confirmando' | 'cuestionario' | 'completado'
- tipo `EventoEspacioVisual` = | { readonly tipo: 'interaccion-iniciada'

**features/simulador/investigacion/investigacion.models.ts**
- `OrigenPista`: espacioId, interaccionId, personajeId?, lugar
- `PistaInvestigacion`: id, titulo, descripcion, origen, obtenidaEn
- `NotaInvestigacion`: id, texto, creadaEn, modificadaEn
- `EstadoLibreta`: contexto, pistas, notas, sincronizacion
- `EventoPistaObtenida`: tipo, contexto, pista
- tipo `ContextoInvestigacion` = | { readonly modo: 'demostracion'

**features/simulador/models/contenido-intento.models.ts**
- `ContenidoIntentoM5`: idIntento, idSesion, caso
- `CasoContenidoM5`: idCaso, titulo, historia, desarrolloSituacional, escenarios
- `EscenarioContenidoM5`: idEscenario, titulo, contexto, instrucciones, orden, preguntas
- `PreguntaContenidoM5`: idPregunta, enunciado, orden, opciones
- `OpcionContenidoM5`: idOpcion, texto, orden

**features/simulador/models/participacion.models.ts**
- `RespuestaGuardadaM5`: idPregunta, idOpcionSeleccionada, revision
- `IntentoM5`: idIntento, estado, inicio, limiteTemporal, motivoFinalizacion, respuestas, ahoraServidor
- tipo `EstadoIntentoM5` = 'EnCurso' | 'Finalizado'

**features/simulador/models/simulador.models.ts**
- `OpcionVista`: id, texto
- `PreguntaVista`: id, enunciado, opciones
- `ElementoEscena`: id, etiqueta, tipo, x, y
- `EscenarioVista`: titulo, contexto, elementos
- `CasoVista`: titulo, escenario, preguntas, duracionSegundos
- tipo `TipoElementoEscena` = 'persona' | 'objeto'
- tipo `FaseSimulador` = 'inicio' | 'cargando' | 'en-curso' | 'confirmando' | 'finalizado' | 'error'
- tipo `OrigenDatos` = 'demostracion' | 'm5'

**features/simulador/mundo-tecnico/mundo-tecnico.models.ts**
- `Punto`: x
- `Rectangulo` extends Punto: ancho
- `Nodo`: id
- `Camino`: desde
- `EdificioTecnico`: id
- `PuntoAccesoCuestionario`: id, nombre, nodo, posicion, radioInteraccion
- `MundoTecnico`: id, zonasTransitables, nodos, edificios, puntosSeguros, plazaCentral?
- tipo `EventoMundo` = | { readonly tipo: 'listo'
- tipo `EventoTransicion` = { readonly tipo: 'entrada-solicitada' | 'entrada-autorizada' | 'interior-abierto' | 'salida-solicitada' | 'regreso-mapa'

**features/simulador/narrativa/narrativa.models.ts**
- `PersonajeNarrativo`: id, nombre, estado, expresion
- `CondicionNarrativa`: marca, valor
- `DialogoNarrativo`: personajeId, texto, condicion?
- `RutaNarrativa`: destino, texto, condicion?
- `DecisionNarrativa`: id, texto, consecuencias, rutas
- `EscenaNarrativa`: id, escenario, dialogos, alEntrar, decisiones
- `GuionNarrativo`: id, inicio, personajes, escenas
- `EstadoNarrativo`: escenaId, dialogo, marcas, personajes, visitadas, transicion
- tipo `Expresion` = 'serena' | 'atenta' | 'alegre'
- tipo `ConsecuenciaNarrativa` = | { readonly tipo: 'marca'

