# M5 — Capa REST: dependencias y decisiones pendientes

Estado al 2026-10-31. Rama `feature/m5-participacion-intentos` (`f4c981c`) conciliada con develop (`2c96c91`), que ya incluía una copia de M5 `3fc823e` con la traducción 503 de M4. Responsable: Integrante 4. Evidencia de M4: [estado actual](../../m4/review/ESTADO_ACTUAL.md).

## Resumen

M5 **no expone endpoints REST todavía**. Las cuatro operaciones implementadas
(consultar, registrar respuesta, actualizar respuesta, finalizar) son
operaciones del estudiante, y exponerlas exige verificar su identidad y la
titularidad del intento. Ninguna de las dos cosas está disponible:

- **ARQ-008.3** no está en el repositorio. Solo se menciona en documentos de
  M1 y en un comentario de M4 ("las rutas son propuestas"). No hay OpenAPI ni
  rutas aprobadas para M5.
- **DD-02** (acceso estudiantil) no está ratificado. M1 reserva
  `POST /v1/acceso-estudiante/validar` y responde `501
  ESTUDIANTE_ACCESS_PENDING_CONTRACT`. `IdentityAccessPort` cubre solo al personal.

Lo que sí está listo: dominio, aplicación (`ParticipacionFacade`),
persistencia PostgreSQL con transacciones y bloqueo, y la traducción de
errores a HTTP (`m5/presentation`).

## Matriz de conciliación ARQ-008.3 ↔ M5

| Operación | Contrato existente | Dependencias | Estado en M5 |
|---|---|---|---|
| Consultar intento | No (ARQ-008.3 ausente) | Identidad del estudiante (M1, DD-02); titularidad (Convocatoria, M4) | Caso de uso y persistencia listos. **REST bloqueado** |
| Registrar respuesta | No | Las anteriores. La pertenencia de la pregunta al caso ya la valida la base (trigger `tr_respuesta_caso`) | Listo internamente. **REST bloqueado** |
| Actualizar respuesta | No | Las anteriores y la decisión académica de permitirla | Provisional y aislado. **REST bloqueado** |
| Finalizar intento | No | Identidad y titularidad; límite temporal (sesión, M4) para `TIEMPO_VENCIDO` | Listo internamente. **REST bloqueado** |
| Iniciar intento | No | Identidad (M1), convocatoria y ventana de sesión (M4), número de intento y autorización de repetición (DD-14) | **No implementado** |
| Reanudar intento | No | M1, M4 y límite temporal | **No implementado** |

## Matriz de operaciones entre módulos (recorrido estudiantil)

Leyenda: **Listo** = implementado y probado. **Provisional** = preparado sin
contrato aprobado. **Pendiente** = depende de otro módulo o de una decisión.

| Paso del recorrido | Angular (simulador) | M1 Identidad | M3 Casos | M4 Sesiones | M5 Participación | M6 Evaluación |
|---|---|---|---|---|---|---|
| Identificar al estudiante | Pendiente: sin pantalla de acceso | Pendiente: DD-02; la ruta de estudiante responde 501 | — | Pendiente: acceso por convocatoria | Listo: `IdentidadEstudianteDesdeM1` sobre `StudentAccessPort`; deniega mientras DD-02 siga abierta | — |
| Ver sesiones disponibles | Bloqueado: `consultarSesionesAutorizadas` falla con contrato pendiente | Pendiente | — | Consulta informativa disponible; elegibilidad pendiente | — | — |
| Iniciar intento | Bloqueado | Pendiente | Listo en develop: caso publicado y bloqueado (V007–V009) | Pendiente: ventana y límite temporal | Pendiente (DD-14) | — |
| Ver escenario y preguntas | Listo con datos simulados; contenido real pendiente | — | Pendiente: contrato de contenido para M5 (sin respuestas correctas) | — | — | — |
| Responder y cambiar respuesta | Listo en presentación; persistencia pendiente | Pendiente | Garantizado por la base (FK compuesta y trigger) | — | Listo internamente: registrar; Provisional: actualizar | — |
| Consultar o reanudar intento | Bloqueado | Pendiente | — | Pendiente: titularidad | Listo internamente: consultar. Listo: titularidad sobre `ProgramacionParticipacionPort` de M4 (`TitularidadIntentosDesdeM4`) y proxy `AccesoProtegidoParticipacion` | — |
| Tiempo restante | Listo, solo informativo | — | — | Pendiente: límite temporal | `TIEMPO_VENCIDO` listo como motivo | — |
| Finalizar | Listo en presentación (confirmación) | Pendiente | — | — | Listo internamente: se rechaza una segunda finalización sin efectos | Pendiente: aviso de intento finalizado |
| Errores HTTP | Listo: clasificación en el cliente | 401/403 de la cadena compartida | — | Formato común `{code, message}` | Listo: traducción (propuesta) | — |

## Piezas provisionales preparadas (sin exponer)

- `application.acceso.AccesoProtegidoParticipacion` (Protection Proxy):
  verifica identidad y titularidad con `ControlAccesoIntentos` antes de
  delegar en la fachada transaccional, y falla de forma cerrada. Es el único
  bean de operaciones de M5: `infrastructure.ConfiguracionParticipacion` lo
  ensambla y no publica la fachada sin control. Lo componen:
  - `IdentidadEstudianteDesdeM1` (M1, `StudentAccessPort`): con
    `DenyingStudentAccessService`, toda llamada se deniega;
  - `TitularidadIntentosDesdeM4` (M4, `ProgramacionParticipacionPort`): sin
    `prisaly.m4.consulta-bd0052.enabled=true`, M4 responde indisponible (503).
- `api.HistorialParticipacionPort`: contrato de M5 para M4 (¿hay
  participaciones en estas convocatorias?). Falta el adaptador de M4.
- `presentation.provisional`: `SolicitudRespuestaProvisional` (con Bean
  Validation: dos uuid) e `IntentoVistaProvisional` (estado, motivo y
  respuestas propias; sin calificación, rúbrica ni respuestas correctas).
  No hay DTO para finalizar: el motivo lo decide el servidor.
- No se crearon controladores: cualquier ruta sería una invención mientras
  falte ARQ-008.3, y sin DD-02 no hay identidad que verificar.

## Endpoints

| Endpoint | Estado |
|---|---|
| (ninguno) | Implementado: ninguno |
| Rutas de estudiante de M5 | Pendientes de ARQ-008.3. No se proponen nombres para no inventar el contrato |

## Contratos que M5 necesita

| De | Contrato | Para qué |
|---|---|---|
| Arquitectura | ARQ-008.3 con las rutas, verbos, DTO y códigos de error de M5 | Implementar los controladores sin inventar la API |
| M1 / Arquitectura | Decisión DD-02 y su implementación: identidad del estudiante en la petición, a través de un contrato de M1 (no de Spring Security) | Saber quién es el estudiante |
| M4 | Titularidad de la convocatoria: `com.prisaly.m4.api.ProgramacionParticipacionPort` y `ProgramacionAutenticadaPort`, integrados en develop. M5 los consume en `TitularidadIntentosDesdeM4`: resuelve Intento → Participacion en sus tablas y pregunta a M4 por la convocatoria. Ver `M5-M4-CONCILIACION-PROGRAMACION.md` | Verificar la titularidad sin leer tablas ni repositorios internos de M4; el contexto no autoriza iniciar ni reanudar |
| M4 | Elegibilidad, ventana de sesión y límite temporal. `ContextoProgramacion` entrega los datos persistidos, pero M4 declara que **no** autoriza iniciar, reanudar ni repetir | Iniciar, reanudar y finalizar por tiempo |
| M1 | Permisos de personal sobre intentos, si la consulta docente está en alcance | Consultas del docente (no definidas hoy) |
| M6 | Contrato para recibir un intento finalizado | Aviso después de persistir la finalización |
| Equipo | V010 histórica de M4: fuera del descubrimiento automático de Flyway (`db/m4-propuesta`); historial compartido pendiente de Dirección Técnica | Conciliar el despliegue sin alterar migraciones aplicadas |

Notas:

- La ruta heredada `GET /m4/sesiones/{id}/acceso/{estudianteId}` está retirada y
  devuelve 410. M5 consume puertos Java de M4, sin llamadas HTTP internas.
- M4 y sus contratos están en develop (`413c8bf`). Esta rama integra develop
  (`2c96c91`) en una rama local de resolución, sin publicar.

## Decisiones que requieren aprobación

| # | Decisión | Estado en M5 |
|---|---|---|
| 1 | ¿Se permite cambiar una respuesta antes de finalizar? | Implementado como provisional y aislado (`ActualizarRespuestaServicio`) |
| 2 | Catálogo definitivo de `motivoFinalizacion` (V007 no lo define) | Se guarda el nombre de la constante; `ENTREGA_ESTUDIANTE` es provisional |
| 3 | ¿Una segunda finalización debe responder como idempotente o como error? | El dominio la rechaza (409 propuesto) |
| 4 | Intento ajeno: ¿responder 404 (no revela su existencia) o 403? | Pendiente. Con el contrato de M4 la consulta devuelve vacío para "inexistente" y para "ajeno", así que el resultado sería 404. La traducción a 403 `FORBIDDEN` sigue siendo provisional |
| 7 | Identidad estudiantil no disponible (DD-02) frente a sin autenticación y sin permiso | Pendiente de Arquitectura. M1, M4 y M5 la traducen distinto (ver abajo) |
| 5 | DD-14: autorización de repetición | Fuera de M5 hasta tener el contrato |
| 6 | Formato común de errores HTTP | M1, M4 y M5 usan `{code, message}`; el catálogo de códigos (`UPPER_SNAKE` frente a `M4-…`) está pendiente de ratificación |

## Traducción de errores propuesta (pendiente de ARQ-008.3)

Implementada en `TraductorErroresM5` y `ManejadorErroresM5`, limitada al
paquete `com.prisaly.m5`. Los 401 y 403 los produce la cadena de seguridad
compartida.

| Excepción | HTTP | `code` |
|---|---|---|
| `EstudianteNoIdentificadoException` | 403 | `STUDENT_IDENTITY_UNAVAILABLE` (**provisional y en disputa**; ver decisión 7) |
| `AccesoIntentoDenegadoException` | 403 | `FORBIDDEN` (provisional; ver decisión 4) |
| `IntentoNoEncontradoException` | 404 | `INTENTO_NO_ENCONTRADO` |
| `DatoAplicacionInvalidoException`, `DatoRespuestaInvalidoException`, `MotivoFinalizacionRequeridoException` | 400 | `DATOS_INVALIDOS` |
| Validación de entrada (`MethodArgumentNotValidException`) | 400 | `VALIDATION_ERROR` (igual que M1) |
| `IntentoFinalizadoException` | 409 | `INTENTO_FINALIZADO` |
| `TransicionIntentoInvalidaException` | 409 | `TRANSICION_INVALIDA` |
| `RespuestaDuplicadaException` | 409 | `RESPUESTA_DUPLICADA` |
| `RespuestaNoRegistradaException` | 409 | `RESPUESTA_NO_REGISTRADA` |
| Violación de restricciones de la base | 409 | `CONFLICTO_DATOS` (mensaje genérico) |
| `PersistenciaIntentoException` | 500 | `ERROR_INTERNO` (mensaje genérico) |
| `ConsultaProgramacionNoDisponibleException` (API M4) | 503 | `M4-CONSULTA-PROGRAMACION-NO-DISPONIBLE` (mensaje genérico) |

## Validación desde Windows

```powershell
# Desde la raíz del backend; Docker disponible para las pruebas M4 transitivas
.\mvnw.cmd -pl m5 -am test                 # pruebas de M5 y dependencias M1/M3/M4
.\mvnw.cmd -pl m5 -am -Pdocker-it verify   # unitarias + integración PostgreSQL (Docker)
.\mvnw.cmd -pl app -am test                # arranque del ensamblado
```

### Decisión 7: tres situaciones que hoy se mezclan

| Situación | M1 (contrato M1–M5) | M4 (`9941085`) | M5 (esta rama) |
|---|---|---|---|
| Sin credenciales | 401, lo responde la cadena de seguridad antes de M5 | `UNAUTHENTICATED` → 401 `M4-NO-AUTENTICADO` | No llega a M5 |
| Autenticado sin contexto de estudiante (por ejemplo, personal) | 403 | `USER_INACTIVE`/`USER_NOT_FOUND` → 403 `M4-SIN-PERMISO` | 403 |
| Identidad estudiantil no disponible (DD-02 abierta, `STUDENT_IDENTITY_UNAVAILABLE`) | Código de dominio, "no es una regla HTTP global" | 503 `M4-PROVEEDOR-NO-DISPONIBLE` | 403 (antes 401) |

`EstudianteNoIdentificadoException` no distingue la causa: el adaptador
convierte cualquier `IdentityDomainException` en "sin identidad". Por eso la
traducción actual a 403 es correcta para el segundo caso, pero discutible para
el tercero, donde M4 responde 503. La copia de M5 incorporada en develop
(`413c8bf`) conserva el 401 `UNAUTHENTICATED` de `3fc823e`; esta resolución
mantiene el 403 del commit `5c7bd7b`. Ninguno de los dos está ratificado.

No se cambia el código hasta que Arquitectura ratifique la política.
Alternativa preparada para discutir (no implementada):

- Conservar el código de M1 en la excepción de M5.
- Responder 403 si es una denegación de un usuario autenticado.
- Responder 503 (o 501, como `/validar`) si el proveedor de identidad no está
  disponible.
- No conceder acceso en ningún caso.

