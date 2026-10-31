# M5 — Capa REST: dependencias y decisiones pendientes

Estado al 2026-10-31. Rama `feature/m5-participacion-intentos`. Responsable: Integrante 4.

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
| M4 | Contrato Java (no HTTP) para el titular de un intento o de una participación: Intento → Participacion → Convocatoria.`idEstudiante` | Verificar la titularidad sin leer tablas de M4 desde M5 |
| M4 | Contrato de elegibilidad, ventana de sesión y límite temporal | Iniciar, reanudar y finalizar por tiempo |
| M1 | Permisos de personal sobre intentos, si la consulta docente está en alcance | Consultas del docente (no definidas hoy) |
| M6 | Contrato para recibir un intento finalizado | Aviso después de persistir la finalización |
| Equipo | Numeración de migraciones: M4 propone V010 | Evitar choques en Flyway |

Notas:

- M4 publica `GET /m4/sesiones/{id}/acceso/{estudianteId}` ("expuesto para
  M5"). Dentro del monolito modular, M5 debería consumir un puerto Java de M4,
  no una llamada HTTP.
- M4 todavía no está integrado en develop.

## Decisiones que requieren aprobación

| # | Decisión | Estado en M5 |
|---|---|---|
| 1 | ¿Se permite cambiar una respuesta antes de finalizar? | Implementado como provisional y aislado (`ActualizarRespuestaServicio`) |
| 2 | Catálogo definitivo de `motivoFinalizacion` (V007 no lo define) | Se guarda el nombre de la constante; `ENTREGA_ESTUDIANTE` es provisional |
| 3 | ¿Una segunda finalización debe responder como idempotente o como error? | El dominio la rechaza (409 propuesto) |
| 4 | Intento ajeno: ¿responder 404 (no revela su existencia) o 403? | Pendiente; recomendado 404 |
| 5 | DD-14: autorización de repetición | Fuera de M5 hasta tener el contrato |
| 6 | Formato común de errores: M1 y la seguridad usan `{code, message}`; M4 usa `{codigo, mensaje, timestamp}` | M5 sigue el de develop (`{code, message}`) |

## Traducción de errores propuesta (pendiente de ARQ-008.3)

Implementada en `TraductorErroresM5` y `ManejadorErroresM5`, limitada al
paquete `com.prisaly.m5`. Los 401 y 403 los produce la cadena de seguridad
compartida.

| Excepción | HTTP | `code` |
|---|---|---|
| `IntentoNoEncontradoException` | 404 | `INTENTO_NO_ENCONTRADO` |
| `DatoAplicacionInvalidoException`, `DatoRespuestaInvalidoException`, `MotivoFinalizacionRequeridoException` | 400 | `DATOS_INVALIDOS` |
| Validación de entrada (`MethodArgumentNotValidException`) | 400 | `VALIDATION_ERROR` (igual que M1) |
| `IntentoFinalizadoException` | 409 | `INTENTO_FINALIZADO` |
| `TransicionIntentoInvalidaException` | 409 | `TRANSICION_INVALIDA` |
| `RespuestaDuplicadaException` | 409 | `RESPUESTA_DUPLICADA` |
| `RespuestaNoRegistradaException` | 409 | `RESPUESTA_NO_REGISTRADA` |
| Violación de restricciones de la base | 409 | `CONFLICTO_DATOS` (mensaje genérico) |
| `PersistenciaIntentoException` | 500 | `ERROR_INTERNO` (mensaje genérico) |

## Validación desde Windows

```powershell
cd C:\Users\Heiner\Desktop\PRISALY\PRISALY-Backen
.\mvnw.cmd -pl m5 -am test                 # unitarias de M5
.\mvnw.cmd -pl m5 -am -Pdocker-it verify   # unitarias + integración PostgreSQL (Docker)
.\mvnw.cmd -pl app -am test                # arranque del ensamblado
```
