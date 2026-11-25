# Matriz de trazabilidad V27: línea base DC-92 (candidato DC-96)

Este documento **sustituye**, para el candidato, a la tabla provisional de `V27-MATRIZ.md`. Esa tabla se conserva como histórica (`bc8e378`).

**Fuentes y autoridad:**
- DC-90 contiene las propuestas.
- DC-92 contiene la ratificación académica y **prevalece**. Las ocho decisiones están aprobadas.

**Árbol del candidato:** `develop` `5d61972d` + A–F (integral) + G (RF-27) + R13 + H1–H6 (DC-92) + DC-96.
- DC-96 añade pruebas RF-27/SMTP, la prueba de la cadena de migraciones V014–V023, este documento y `docs/api/openapi-candidatos-dc92.yaml`.
- La regresión oficial y su resultado están en `DC-92-RESULTADO.md`.

**Columnas:**
- **develop:** lo activo hoy en `develop`.
- **Candidato:** lo que añade la cadena.
- **Interruptor:** todo lo nuevo está **apagado por defecto**. La excepción es D-80-1, que es seguridad.
- **Evidencia:** pruebas automatizadas con la APP real y PostgreSQL 16.15. «QA» = verificado en un QA real con Angular o Mailpit.

| RF | Nombre | develop | Candidato (incremento) | Interruptor / prerrequisito | Evidencia automatizada | Pendiente para el cumplimiento literal |
|---|---|---|---|---|---|---|
| RF-01 | Autenticar estudiantes | DD-02 B tras interruptor | = | `prisaly.m1.acceso-estudiante…` + V014–V016 + contrato | `AccesoEstudianteBHttpIT` (R13 corregido) | D-78-2 (activación), D-ESQ-1, E2E en QA |
| RF-02 | Control de acceso por rol | Hueco D-80-1 | **D-80-1 cerrado (B), activo** | — | `EstudianteSinPermisosD801HttpIT` | — |
| RF-03 | Docentes, roles y permisos | Alta sin credencial | Activación D-05 (A) | `PRISALY_M1_ACTIVACION_DOCENTE_ENABLED` + V020 | `ActivacionDocente*HttpIT` | D-ESQ-1 |
| RF-04 | Espacio académico y estudiantes | Sin alta de estudiantes | Alta P-78 (B) | `PRISALY_M1_ALTA_ESTUDIANTES_ENABLED` + PD-01 | `AltaEstudiantesHttpIT` | Pantalla Angular |
| RF-05 – RF-09 | Autoría del caso | ✔ | = | — | QA + IT | — |
| RF-10, RF-11 | Responder y ver el progreso | Tras identidad DD-02 | = | DD-02 B + M5 | IT | D-78-2; E2E en QA |
| RF-12 | Inicio y fin | Tras interruptor | = | M5 publicación | IT | Activar M5; E2E en QA |
| RF-13 | Calcular la nota | Tras interruptor | = | `prisaly.m6.evaluacion.enabled` | IT | Activar M6 |
| RF-14 | Resultado y retroalimentación | ✘ | N2 (F): nota liberada. **DD-12 (H6):** retroalimentación general y detalle por pregunta, configurables y solo tras la publicación | `prisaly.m6.liberacion.enabled` + V019; `PRISALY_M6_RETROALIMENTACION_ENABLED`; `PRISALY_M6_RESULTADO_DETALLE_POR_PREGUNTA` | `LiberacionN2QaHttpIT`, `ResultadosDd12QaHttpIT` | D-ESQ-1; pantalla Angular |
| RF-15 | Consultar participaciones | Detalle docente tras M6 | **DD-09 (H4):** consulta JSON limitada a sesiones propias y a convocados; resultado solo si está publicado | `PRISALY_M4_CONSULTA_PARTICIPACIONES_ENABLED` | `ParticipacionesAusenciasDc92HttpIT` | Pantalla Angular |
| RF-16 | Reporte de participación | ✘ | CSV (C) **ajustado a DD-11 (H4):** 10 columnas DC-90, sin correo, nota solo publicada | `prisaly.reportes.participacion.enabled` | `ReporteParticipacion*HttpIT`, `ReporteParticipacionCsvTest` | Activación |
| RF-17, RF-18 | Rúbrica; agendar | ✔ | = | — | QA + IT | — |
| RF-19 | Estados de sesión | Solo cierre manual | **DD-01 (H1):** cierre automático de sesiones vencidas (margen `P1D`) que respeta los intentos en curso | `PRISALY_M4_CIERRE_AUTOMATICO_ENABLED` | `CierreAutomaticoDd01Test`, `CierreAutomaticoDd01QaHttpIT` | Activación |
| RF-20 | Código de autorización | Tras interruptor | = | DD-02 B | IT | D-78-2 |
| RF-21 | Notificar la convocatoria y los cambios | Convocatoria (worker apagado) | Zona horaria (D). **Retiro (H5)** con historial, resolución explícita del intento y avisos | `PRISALY_M4_RETIRO_ENABLED` + V023 | `RetiroRf21QaHttpIT` | D-ESQ-1; SMTP institucional |
| RF-22, RF-23 | Ingreso tardío; tiempo máximo | Tras interruptor | = | M5 | IT | Activar M5 |
| RF-24 | Incompletos y ausencias | Incompletos (S1) | **DD-13 (H4):** ausencias diferenciadas, sin cero automático, con historial | `PRISALY_M4_AUSENCIAS_ENABLED` + V022 | `ParticipacionesAusenciasDc92HttpIT` | D-ESQ-1 |
| RF-25 | Repetición excepcional | Solo BD | **DD-04 (H3):** una repetición autorizada y justificada, intento 2, selección docente del intento válido con historial | `prisaly.m5.repeticion.enabled` + V021 | `RepeticionDd04QaHttpIT` | D-ESQ-1 |
| RF-26 | Retroalimentación general | Solo lectura | **Escritura docente (H6)** | `PRISALY_M6_RETROALIMENTACION_ENABLED` | `ResultadosDd12QaHttpIT` | Pantalla Angular |
| RF-27 | Notificar las calificaciones | ✘ | Aviso y correo en la primera liberación, idempotentes y sin la nota (G). **DC-96:** fallo SMTP transitorio → reintento con espera → una sola entrega; rechazo permanente → Fallido sin reintento; 5 fallos → Fallido; liberaciones repetidas sin duplicados | `prisaly.m7.notificacion-calificacion.enabled` + N2 + worker M7 | `NotificacionCalificacionRf27QaHttpIT`, `NotificacionCalificacionRf27SmtpQaHttpIT` | SMTP institucional en QA |

**Esquema candidato** (todo fuera de `db/migration`):
- V014–V016 (DD-02 B), V019 (N2), V020 (D-05) y V021–V023 (DC-92).
- Contrato `bd0052-v013-integral-dc92`.
- Prueba: `MigracionesDc92PostgreSqlIT` (instalación limpia, actualización con datos, conflicto QA FE-60, D-SIM).
- La promoción depende de **D-ESQ-1** y **D-SIM**.

**Implementación Backend completa en el candidato:** las 27 RF tienen código y pruebas automatizadas. **No se declara el cumplimiento 27/27**, porque aún falta:
- la regresión oficial de cada árbol publicado;
- D-ESQ-1, D-SIM y D-78-2;
- la activación por entorno;
- el E2E en un QA real con Angular.
