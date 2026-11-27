# DC-118 — Cierre de trazabilidad V27 y preparación para el jurado

Fecha: 2026-11-26. QA: `prisaly-dc105-local` (Angular/API http://localhost:18175 · Mailpit http://localhost:18030).

**Fuente oficial:** `docs/integration/v27-trazabilidad.json` (27 RF, texto V27 y CU históricos), en `develop` `7961b86`.

**SHA**

| Repositorio | SHA | Rama | Base |
|---|---|---|---|
| Backend | `0453b02` | `trabajo/dc115-acceso-rapido-qa` | `develop` `7961b86` (PR #102) |
| Frontend | `fd8812f` | `trabajo/dc-demo-112` | `develop` `7ad2a2d` |

Todo es local: sin push ni merge. `main` y `develop` no se tocaron.

**Evidencias** (todas en `C:\Users\Heiner\Desktop\PRISALY-ENTREGA`):

| Id | Evidencia | Archivo |
|---|---|---|
| E1 | Recorrido HTTP real M5, 41/41 | `DC-107/recorrido-m5.json` |
| E2 | Recorrido HTTP real M5 + M6 + resultado, 63/63 | `DC-107/recorrido-m6.json` |
| E3 | Recorrido HTTP real solo por HTTP con publicación DC-111, 81/81 (incluye el rechazo de un caso incompleto) | `DC-111/recorrido-dc111.json` |
| E4 | Navegador real, docente: activación por correo → caso → escenario → pregunta → 3 opciones → rúbrica con valor → publicación → sesión → convocatoria | DC-DEMO-112 |
| E5 | Navegador real: acceso rápido QA de los tres roles; la sesión del personal sobrevive a la recarga | DC-115 / DC-117 |
| E6 | HTTP DC-118: DD-09 200, CSV 200, rutas de ausencia, retiro, repetición y retroalimentación activas; worker M7 con 8/8 correos `Enviado` a Mailpit | este documento |
| IT | Regresión oficial 1980/1980 (DC-96) y CI `verify` del PR #102 en verde | Solo se cuentan como pruebas, no como demostración |

## 1. Matriz V27 (27 RF)

**Estados:**

| Estado | Significado |
|---|---|
| **CD** | Completo y demostrado (navegador o HTTP real contra el QA dc105) |
| **ISD** | Implementado sin demostración |
| **P** | Parcial |

| RF | Descripción (V27, resumida) | Actor | Módulo | CU | Pantalla Angular | Endpoint | Backend | Persistencia | Evidencia | Estado | Dependencia / bloqueo | Acción pendiente |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| RF-01 | Autenticar estudiantes con correo y código | Estudiante | M1 | CU-01 | `/estudiante/acceso` → `/estudiante/portal` | `POST /v1/acceso-estudiante/solicitar`, `/validar`, `/cerrar-sesion` | `EstudianteCuentaController`, DD-02 B | `CredencialAccesoEstudiante`, `SesionEstudiante`, `IntentoAccesoEstudiante` (V014–V015) | E1–E3, E5 | **CD** | Código individual por correo (DD-02 B), no un código por convocatoria | Prueba manual en navegador |
| RF-02 | Restringir funciones por rol y permiso | Todos | M1 + APP | CU-03, CU-05, CU-11 | Guards de `/dashboard` | 401/403 en todas las rutas | `HttpApiConfig`, `IdentityAccessService` | `Usuario`, `Rol`, `UsuarioPermiso` | E2 (403 `ADMIN_REQUIRED`, 401 al estudiante) | **CD** | — | — |
| RF-03 | Administrar docentes, roles y permisos | Administrador | M1 | CU-02, CU-03 | Dashboard → Administración | `POST /v1/admin/docentes`, `…/activacion`, `PUT …/permisos/{id}`, `POST /v1/personal/activar` | `AdministracionController`, `ActivacionDocenteController` | `Usuario`, `UsuarioPermiso`, `ActivacionDocente` (V020) | E4 (activación en navegador), HTTP DC-106 | **CD** | — | Alta en navegador por el administrador (Ingrid) |
| RF-04 | Asociar espacio académico y estudiantes | Docente / Administrador | M4 + M1 | CU-11, CU-12 | `/dashboard/sesiones`, `/dashboard/convocatorias` | `POST /m4/sesiones`, `…/convocatorias`, `POST /v1/admin/estudiantes` | `SesionController`, `ConvocatoriaController`, `AltaEstudianteService` | `Sesion`, `Convocatoria`, `Estudiante` | E4 | **CD** | La convocatoria pide el UUID del estudiante | Selector de estudiantes para el docente (requiere contrato) |
| RF-05 | Estructura académica del caso | Docente | M3 | CU-05, CU-06 | `/dashboard/casos-academicos` | `POST /v1/casos` | `CasoCreationController` | `Caso` | E4 | **CD** | — | — |
| RF-06 | Contextos y preguntas por escenario | Docente | M3 | CU-06, CU-07 | `/dashboard/casos-academicos` (contenido) | `POST …/escenarios`, `…/preguntas` | `CasoContenidoController` | `Escenario`, `Pregunta` | E4 | **CD** | — | — |
| RF-07 | Preguntas y respuestas | Docente | M3 | CU-07 | Ídem | `POST …/preguntas/{id}/opciones` | `CasoContenidoController` | `OpcionRespuesta` | E4 | **CD** | — | — |
| RF-08 | Justificar respuestas | Docente | M3 | CU-07 | Ídem (campo Explicación) | Ídem | Ídem | `OpcionRespuesta.explicacion` | E4 | **CD** | — | — |
| RF-09 | Validar estructura y rúbrica; Borrador → Publicado | Docente | M3 + M4 + BD | CU-05, CU-09–11 | `/dashboard/casos-academicos` → «Solicitar publicación» | `POST /v1/casos/{id}/publicar` | `CasoPublicationAdapter` (DC-111: preguntas a `Lista`) | `Caso`, `Pregunta.estadoEdicion`, triggers V007/V009 | E3 (409 caso incompleto), E4 | **CD** | — | — |
| RF-10 | Responder y modificar respuestas | Estudiante | M5 | CU-15 | `/estudiante/portal` (intento) | `GET …/intentos/{id}/contenido`, `PUT …/respuestas/{p}` | `IntentosEstudianteM5Controlador` | `Respuesta` (con revisión) | E1–E3 | **CD** (HTTP) | — | Prueba manual del portal en navegador |
| RF-11 | Registrar respuestas y mostrar progreso | Estudiante | M5 | CU-15 | Portal: barra de progreso | `GET /v1/estudiantes/intentos/{id}` | Ídem | `Respuesta` | E1–E3 | **CD** (HTTP) | — | Ídem |
| RF-12 | Inicio y fin de la participación | Estudiante | M5 + M6 | CU-15, CU-16 | Portal: iniciar o continuar / finalizar | `POST …/sesiones/{id}/intentos`, `POST …/intentos/{id}/finalizar` | `IntentosM5Controlador` | `Participacion`, `Intento` | E1–E3 (201/200, finalización idempotente) | **CD** (HTTP) | — | Ídem |
| RF-13 | Calcular la nota con la rúbrica | Docente | M6 | CU-08, CU-16 | Sesiones → Participaciones → «Evaluar» | `POST /v1/evaluacion/intentos/{id}` | `EvaluacionDocenteM6`, `EstrategiaPonderadaS1` | `Calificacion` | E2, E3 (5.00; 409 inmutable) | **CD** (HTTP) | — | Prueba manual del botón |
| RF-14 | Mostrar el resultado y la retroalimentación final | Estudiante | M6 | CU-16, CU-17 | Portal → «Ver resultado» | `GET /v1/estudiantes/intentos/{id}/calificacion` | `CalificacionEstudianteController`, N2 | `LiberacionCalificacion` (V019) | E2, E3 (404 antes de liberar, 200 después) | **P** | El detalle por pregunta (aciertos y explicaciones) está implementado pero apagado por configuración DD-12 | DC decide si se activa `PRISALY_M6_RESULTADO_DETALLE_POR_PREGUNTA` |
| RF-15 | Consultar participaciones | Docente | M4 / M5 / M6 | CU-19 | Sesiones → detalle → Participaciones | `GET /v1/docente/sesiones/{id}/participaciones` | `ParticipacionesDocenteDc92` | Vista sobre `Convocatoria`, `Participacion`, `Intento`, `Calificacion` | E6 (200) | **CD** (HTTP) | — | Prueba manual |
| RF-16 | Descargar reporte de participación | Docente | M7 | CU-20 | Participaciones → «Descargar CSV» | `GET …/reporte-participacion` | `ReporteParticipacionService` (DD-11) | Lectura | E6 (200) | **CD** (HTTP) | — | Prueba manual de la descarga |
| RF-17 | Registrar y asociar la rúbrica | Docente | M3 (+M6) | CU-08 | `/dashboard/rubricas` | `PUT /v1/casos/{id}/rubrica` | `CasoRubricaController` | `Rubrica`, `RubricaPregunta` | E4 (con valor por pregunta) | **CD** | El peso por pregunta no es obligatorio al publicar (DD-03) | — |
| RF-18 | Agendar fecha, horario y condiciones | Docente | M4 | CU-11 | `/dashboard/sesiones` | `POST /m4/sesiones` | `SesionAcademicaService` | `Sesion` | E4 | **CD** | — | — |
| RF-19 | Estados de la sesión (Sin iniciar / En curso / Finalizado) | Docente / sistema | M4 | CU-13, CU-14 | Sesiones → cambiar estado | `PATCH /m4/sesiones/{id}/estado` | `SesionAcademicaService`, `M4CierreAutomaticoConfig` (DD-01) | `Sesion.estado` | E1–E3 (paso a `EnCurso`) | **P** | El cierre automático DD-01 está implementado pero apagado en QA: cerraría sesiones QA existentes | Activarlo en un QA limpio |
| RF-20 | Asignar y validar el código de autorización | Sistema | M1 / M7 | CU-01, CU-11 | `/estudiante/acceso` | `/v1/acceso-estudiante/*` | Worker de códigos M7 | `EntregaCodigoAcceso` (V016) | E1–E3 | **CD** | Ídem RF-01 | — |
| RF-21 | Notificar la convocatoria y los cambios | Sistema / docente | M7 + M4 | CU-11, CU-12 | Participaciones → «Retirar» | Worker M7; `POST /v1/docente/convocatorias/{id}/retiro` | `NotificarConvocatoriaService`, `RetiroConvocatoriaRf21` | `Aviso`, `EnvioCorreo`, `RetiroConvocatoria` (V023) | E6 (8 correos `Enviado` en Mailpit) | **P** | El retiro con avisos (DC-118) está implementado y activo, pero sin recorrido | Ejecutar un retiro desde el panel |
| RF-22 | Restringir el ingreso tardío | Sistema | M5 | CU-01 | Portal: mensaje 403 | `POST …/intentos` | Políticas E1 / L1 / RF-22 | `Intento` | IT `PoliticasInicioVencimientoM5HttpIT` 13/13; E1 (E1 = 403) | **ISD** | Demostrarlo requiere una sesión con más de la mitad del tiempo transcurrido | Sesión de demo con hora pasada |
| RF-23 | Controlar el tiempo máximo | Sistema | M5 | CU-15, CU-16 | Portal: «Tienes hasta…» | Ídem | V1-c `TIEMPO_VENCIDO` | `Intento.limiteTemporal` | IT 13/13 | **ISD** | Requiere esperar a que venza | — |
| RF-24 | Intentos incompletos y ausencias | Docente / sistema | M6 / M4 | CU-16, CU-19, CU-20 | Participaciones → «Gestionar» → ausencia | `POST …/convocatorias/{id}/ausencia` | `ParticipacionesDocenteDc92` (DD-13), S1 | `ClasificacionAusencia` (V022) | IT DC-92; E6 (ruta activa) | **ISD** | — | Clasificar una ausencia desde el panel |
| RF-25 | Repetición excepcional | Docente | M5 + BD | CU-01, CU-14, CU-18 | Participaciones → «Autorizar repetición» | `POST /v1/docente/participaciones/{id}/repeticion`, `…/intento-valido` | `RepeticionDocenteDd04` | `AutorizacionRepeticion`, `SeleccionIntento` (V021) | IT DC-92; E6 (ruta activa) | **ISD** | La selección del intento válido no tiene pantalla | Recorrido de autorización e intento 2 |
| RF-26 | Retroalimentación general docente | Docente | M6 | CU-21 | Participaciones → «Gestionar» → «Guardar retroalimentación» | `POST/PUT /v1/docente/participaciones/{id}/retroalimentacion` | `RetroalimentacionDocenteDd12` | `RetroalimentacionGeneral` | E2, E3 (201, y el estudiante la ve) | **CD** (HTTP) | — | Prueba manual del botón |
| RF-27 | Notificar las calificaciones | Sistema | M7 | CU-17, CU-22 | — (correo en Mailpit) | Liberación N2 → worker M7 | `NotificacionCalificacionM7` | `Aviso`, `EnvioCorreo` | IT RF-27 (3/3 SMTP); activado en dc105 (DC-118) | **ISD** | — | Liberar una nota y verla en Mailpit |

## 2. Recuento

| Estado | RF | Total |
|---|---|---|
| Completo y demostrado | 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 15, 16, 17, 18, 20, 26 | **19** |
| Parcial | 14, 19, 21 | **3** |
| Implementado sin demostración | 22, 23, 24, 25, 27 | **5** |
| Bloqueado / no implementado | — | **0** |

**No se declara 27/27 demostrado.**

## 3. Correcciones implementadas (DC-114 a DC-118)

| Commit | Repositorio | Cambio |
|---|---|---|
| `c867df0` | Frontend | Portal del estudiante (M5 + resultado) y botones Evaluar / Publicar nota |
| `03c2cd9` | Frontend | Sesión del personal restaurable tras recargar (revalidada con `/me`), pantalla `/acceso` y entrada real al simulador |
| `3387301` | Frontend | Acceso rápido QA |
| `c6ccef4` | Frontend | Login sin presentación al llegar desde `/acceso` |
| `fd8812f` | Frontend | Gestión de participaciones: retroalimentación (RF-26), ausencias (RF-24), retiro (RF-21), repetición (RF-25) y CSV (RF-16) en QA |
| `0453b02` | Backend | Acceso rápido QA: solo login y código oficiales, desactivado por defecto |
| `d91419b` / PR #102 | Backend | Publicación pasa las preguntas a `Lista` |

**Configuración QA, solo dc105:** `DC-118/dc118-qa.yaml` activa retiro, ausencias, repetición, CSV, la notificación RF-27 y el worker M7.

## 4. Simulador: trazabilidad y límite real

| Pieza | Estado |
|---|---|
| Caso y escenario (M3) | Integrado |
| Guion narrativo (`GuionNarrativoCaso`, V017; `CasoNarrativaController`) | Implementado; interruptor apagado |
| Sesión y convocatoria (M4) | Integrado |
| Intento y exploración (`FaseIntento` V018, rutas P1–P4) | Implementado; apagado |
| Información y decisiones (investigación `notas` / `pistas` / `edificios` / `zonas`, V024 dc116, PR #101) | Implementado; apagado |
| Evaluación (M6) y resultado (N2) | Integrados |
| Angular | El intento activo comprueba la señalización real de Pixel Streaming (`ws://127.0.0.1:8898`). Solo abre la vista 3D en el entorno autorizado |

**No hay integración 3D funcional en dc105.**

## 5. Bloqueos reales (por impacto)

1. **Simulador.** Faltan tres cosas:
   - renumerar V017/V018 (V024 ya lo usa dc116; hay que usar números libres) y crear el contrato M4 `integral-dc92-sim`;
   - el laboratorio Pixel Streaming;
   - las decisiones académicas sobre el guion y las pistas.
2. **Demostraciones pendientes de RF-21, RF-24, RF-25 y RF-27.** El código está activo en QA; falta ejecutar sus recorridos (prueba manual).
3. **RF-14, detalle por pregunta.** Está apagado por configuración DD-12; DC decide si se activa.
4. **RF-19, cierre automático DD-01.** Sin activar en este QA para no cerrar sesiones existentes.
5. **Producción.** D-ESQ-1 y D-78-2: las migraciones candidatas siguen fuera de `db/migration`, y faltan SMTP institucional y claves por entorno.

## 6. Recorrido recomendado para el jurado (≈ 10 min, http://localhost:18175)

1. `/acceso` → **Administrador QA** → Administración. RF-02, RF-03, RF-04.
2. `/acceso` → **Docente QA**:
   1. Casos → crear caso, escenario, pregunta y opciones con explicación. RF-05–08.
   2. Rúbricas → criterio y valor por pregunta. RF-17.
   3. Publicar. RF-09.
   4. Sesiones → programar y pasar a «En curso». RF-18, RF-19.
   5. Convocatorias → convocar al estudiante QA. RF-04.
3. Otra pestaña → **Estudiante QA** → Portal: iniciar, responder y finalizar. RF-01, RF-10–12, RF-20.
4. Docente → Sesiones → detalle → Participaciones:
   1. «Evaluar», que calcula la nota. RF-13.
   2. «Gestionar» → retroalimentación. RF-26.
   3. «Publicar nota». RF-14, RF-27 (el correo aparece en Mailpit).
   4. «Descargar CSV». RF-15, RF-16.
5. Estudiante → «Ver resultado». RF-14.

**Respaldo ya preparado:** el caso «Conflicto en el aula» y la sesión «Psicología Social · Grupo A (demostración)», con la estudiante QA convocada.
