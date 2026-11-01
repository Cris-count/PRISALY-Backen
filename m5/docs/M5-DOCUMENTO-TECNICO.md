# M5 — Participación e Intentos | Documento técnico y plan de cierre

Estado al 2026-11-01, rama `trabajo/m5-avance-nocturno`. Responsable: Integrante 4.

**Estado: M5 no está terminado.**

- Las operaciones sobre un intento existente están implementadas, protegidas y probadas.
- Iniciar y reanudar intentos, la API REST y la identidad real del estudiante
  dependen de decisiones externas (DD-02, DD-14, ARQ-008.3) y de contratos de
  M4 todavía no ratificados.

Leyenda de estados: **Implementado**, **Probado** (Maven en Windows o, si se
indica, fuera de Maven), **Pendiente de integración**, **Bloqueado por
decisión** y **Fuera de alcance**.

## 1. Responsabilidad

M5 gestiona el intento de un estudiante sobre un caso:

- su estado (`EnCurso`, `Finalizado`);
- las respuestas (una por pregunta, con revisión);
- la finalización con motivo.

Persiste en las tablas de BD-005.2 `"Intento"`, `"Respuesta"` y
`"Participacion"`; esta última solo la lee.

No califica (M6), no programa sesiones ni convocatorias (M4), no gestiona
contenido (M3) y no autentica (M1).

## 2. Arquitectura en cuatro capas

| Capa | Paquete | Contenido |
|---|---|---|
| Dominio | `domain.intento`, `domain.respuesta` | `Intento` (agregado), `EstadoIntento`, `MotivoFinalizacion`, `Respuesta` y sus valores; excepciones de dominio. Sin Spring |
| Aplicación | `application`, `application.servicio`, `application.acceso`, `application.port`, `api` | `ParticipacionFacade` (Facade), cuatro servicios, `OperacionesParticipacion`, `AccesoProtegidoParticipacion` (Protection Proxy), `ControlAccesoIntentos`, puertos `RepositorioIntentos`, `IdentidadEstudianteActual`, `TitularidadIntentos`; contrato público `HistorialParticipacionPort` |
| Infraestructura | `infrastructure`, `.persistencia`, `.identidad`, `.programacion` | JPA (`IntentoEntity`, `RespuestaEntity`, repositorios de paquete), `RepositorioIntentosJpa`, `MapeoIntento`, `FachadaParticipacionTransaccional`, adaptadores de M1 y M4 (Adapter), `HistorialParticipacionJdbc`, `ConfiguracionParticipacion` |
| Presentación | `presentation`, `.provisional` | `TraductorErroresM5`, `ManejadorErroresM5`, `ErrorResponse`; DTO provisionales. **Sin controladores** |

**Dependencias Maven:** `m5 → m4 → m1, m3` y `m5 → m1`. Sin ciclos: M4 no
depende de M5.

**Patrones:**

- Facade: `ParticipacionFacade`.
- Protection Proxy: `AccesoProtegidoParticipacion`.
- Adapter: M1, M4 y JPA.
- Decorator transaccional: `FachadaParticipacionTransaccional`.
- Chain of Responsibility: **diferido**, porque la cadena de validación previa
  al inicio depende de M4 y DD-14.

## 3. Estados y reglas del intento

| Regla | Dónde se garantiza | Estado |
|---|---|---|
| Estados `EnCurso` → `Finalizado`, sin retorno | Dominio + V005 (`tr_intento_no_reabrir`) + V007 (CHECK) | Implementado, probado |
| Respuesta solo en `EnCurso` | Dominio (la base **no** lo impide) | Implementado, probado |
| Una respuesta por pregunta | Dominio + índice único (V002) | Implementado, probado |
| La opción pertenece a la pregunta | FK compuesta y `tr_respuesta_caso` (V003) | Garantizado por la base, probado en IT |
| Finalizar exige motivo; una segunda finalización se rechaza (409) | Dominio | Implementado, probado |
| `fin >= inicio` | `ck_intento_tiempos` (V002) + reloj de PostgreSQL (M5-INC-01) | Implementado, probado |
| Sin actualizaciones perdidas | Bloqueo `FOR UPDATE` de la fila del intento | Implementado, probado (IT de concurrencia) |
| Cambiar una respuesta antes de finalizar | `ActualizarRespuestaServicio` | Implementado como **provisional**; bloqueado por decisión |
| Catálogo de `motivoFinalizacion` | `ENTREGA_ESTUDIANTE` provisional, `TIEMPO_VENCIDO` | Bloqueado por decisión |

## 4. Seguridad

Cadena obligatoria para toda operación de estudiante:

1. **Identidad (M1):** `IdentidadEstudianteDesdeM1` sobre
   `StudentAccessPort.requireCurrentStudent()`.
   - Usa `studentId`, nunca `userId`.
   - Una respuesta nula, sin `studentId` o sin `userId` se trata como ausencia
     de identidad.
   - Cualquier `IdentityDomainException` es "sin identidad", y su mensaje no
     llega al cliente.
   - Otros errores se propagan y nunca conceden acceso.
   - Con `DenyingStudentAccessService` (DD-02 abierta), **toda operación se
     deniega**.
2. **Titularidad (M4):** `TitularidadIntentosDesdeM4` devuelve PROPIO, AJENO o
   INEXISTENTE (detalle en la sección 6).
3. **Proxy:** `AccesoProtegidoParticipacion` verifica antes de abrir la
   transacción y delega solo si el intento es PROPIO.

**Garantías estructurales** (M5-INC-02):

- El proxy es el **único bean** de `OperacionesParticipacion`. La fachada sin
  control no es un bean.
- Sin proveedor de identidad o de titularidad, el contexto **no arranca**.
- Una prueba de arquitectura impide que presentación dependa de servicios,
  puertos, repositorios, infraestructura, JPA, M1 o M4, y que se construya
  la fachada o el proxy fuera de `ConfiguracionParticipacion`.
- Los repositorios Spring Data de M5 no son públicos.

**Errores:**

- Formato `{code, message}`.
- Mensajes genéricos para persistencia, base de datos, M4 caído y entradas
  mal formadas.
- Sin SQL, trazas ni datos de titularidad (el 403 no revela al titular).

## 5. Integración M1–M5

| Aspecto | Estado |
|---|---|
| Contrato `StudentAccessPort` / `AuthenticatedStudent(userId, studentId)` | Integrado (`4e0b90a`, `aefb73b`) |
| Suplantación (id de otro estudiante, `userId` en lugar de `studentId`, token de personal) | Probado: deniega |
| Fallo del proveedor | Probado: se propaga, sin acceso |
| Autenticación real del estudiante | **Bloqueado por DD-02.** M5 no la simula |

## 6. Integración M4–M5

**Titularidad** (`TitularidadIntentosDesdeM4`):

1. M5 lee `Intento → Participacion."idConvocatoria"` en sus tablas.
2. Llama a
   `ProgramacionParticipacionPort.consultarConvocatoria(convocatoria, studentId)`.
3. Comprueba que el contexto devuelto coincide con lo consultado.

| Caso | Resultado |
|---|---|
| Intento inexistente o referencia no uuid | INEXISTENTE → 404 |
| Convocatoria de otro estudiante, estudiante inexistente, contexto incoherente o respuesta nula de M4 | AJENO → 403 provisional (decisión 4) |
| M4 caído (`ConsultaProgramacionNoDisponibleException`) | 503 sin causa SQL; sin escrituras |
| Fallo de la consulta propia | `PersistenciaIntentoException` → 500 genérico |
| Convocatoria retirada | **PROPIO.** La titularidad no evalúa elegibilidad (sección 9) |

**Carrera entre verificar y operar:** una vez existe la participación, la
cadena es inmutable. Comprobado en PostgreSQL 16.15: los triggers rechazan
cambiar `Convocatoria.idEstudiante`, `Participacion.idConvocatoria` e
`Intento.idParticipacion`, y `tr_intento_no_delete` impide borrar intentos.
Lo que **sí** puede cambiar entre verificar y operar es el estado académico
(sesión o retiro), que hoy M5 no evalúa.

**Configuración:** sin `prisaly.m4.consulta-bd0052.enabled=true`, M4 publica un
respaldo que lanza indisponibilidad. La titularidad responde 503 y nunca da
acceso (falla de forma segura).

### Contrato para el Integrante 3: `HistorialParticipacionPort`

```java
package com.prisaly.m5.api;
public interface HistorialParticipacionPort {
    /** true si alguna convocatoria tiene participación; vacío → false; null → IllegalArgumentException. */
    boolean existeParticipacion(Collection<UUID> convocatorias);
}
```

**Implementación:** `HistorialParticipacionJdbc`, un `@Component` siempre
presente en el ensamblado. Consulta
`SELECT EXISTS (… "Participacion" WHERE "idConvocatoria" = ANY (?))` y exige
la transacción del llamador (`Propagation.MANDATORY`).

**Semántica:** un Intento exige una Participacion (FK), así que "participaciones
o intentos" equivale a "participaciones".

**Adaptador sugerido en M4** (lo escribe M4; M5 no modifica M4):

```java
@Component
class ParticipacionesDesdeM5 implements ParticipacionesPort {
    // HistorialParticipacionPort historial; ConvocatoriaRepository convocatorias (de M4)
    public boolean tieneParticipacionesOIntentos(UUID sesionId) {
        return historial.existeParticipacion(idsDeConvocatoriasDe(sesionId)); // dentro de la transacción de M4
    }
}
```

**Concurrencia:** hoy M5 no crea participaciones. Cuando exista el inicio de
intentos, ambos módulos deben acordar un orden de bloqueo. Por ejemplo:

- M4 bloquea la fila de `"Sesion"` (`FOR UPDATE`) antes de consultar el historial;
- el inicio de M5 toma `FOR SHARE` sobre la misma fila antes de insertar la participación.

**Dirección de dependencias:** M4 tendría que depender de `m5` para usar el
contrato, mientras que M5 ya depende de `m4`. **Eso crearía un ciclo Maven.**
Opciones, a decidir por Arquitectura:

| Opción | Cómo funciona |
|---|---|
| a) Inversión en M4 | M5 implementa directamente `ParticipacionesPort` de M4 (M5 → M4, sin ciclo). M5 usa `HistorialParticipacionJdbc` y necesita las convocatorias de la sesión mediante un contrato de M4 |
| b) Contrato compartido | Mover el puerto a un módulo de contratos comunes. Implica un cambio estructural |
| c) Ensamblado | `app` (que depende de ambos) cablea un adaptador |

M5 no ejecuta ninguna de las tres: la a) pide a M4 un contrato de
convocatorias por sesión, y b) y c) tocan fuera de M5.

## 7. Contrato propuesto para el frontend (simulador)

**ARQ-008.3 no contiene rutas de M5 en el repositorio**: solo aparecen la ruta
de personal de M1 y rutas propuestas de M4. Por eso **no se implementa ningún
endpoint**. La tabla es una **propuesta**: las rutas, los códigos marcados como
pendientes y los nombres de los DTO necesitan ratificación.

| Operación | Método y ruta (propuesta) | Entrada | Salida | Autorización | Errores | Idempotencia | Persistencia | Dependencias |
|---|---|---|---|---|---|---|---|---|
| Consultar intento propio, con estado y respuestas | `GET /v1/intentos/{idIntento}` | uuid en la ruta | `IntentoVistaProvisional` (estado, motivo, respuestas propias) | Proxy: identidad + titularidad | 400 id mal formado; 401/403/503 identidad (decisión 7); 403/404 ajeno (decisión 4); 404; 503 M4; 500 | Sí (lectura) | Ninguna | M1, M4, ARQ-008.3 |
| Registrar respuesta | `POST /v1/intentos/{idIntento}/respuestas` | `SolicitudRespuestaProvisional` (idPregunta, idOpcionSeleccionada) | `IntentoVistaProvisional` | Proxy | 400 validación o FK; 409 `RESPUESTA_DUPLICADA` o `INTENTO_FINALIZADO`; los anteriores | No: el segundo intento da 409 | INSERT en `"Respuesta"` (revisión 1) | Las anteriores |
| Actualizar respuesta (provisional) | `PUT /v1/intentos/{idIntento}/respuestas/{idPregunta}` | `{idOpcionSeleccionada}` | `IntentoVistaProvisional` | Proxy | 409 `RESPUESTA_NO_REGISTRADA` o `INTENTO_FINALIZADO`; los anteriores | Sí: la misma opción no cambia la revisión | UPDATE de la opción y revisión + 1 | Además, la decisión de permitirla |
| Finalizar | `POST /v1/intentos/{idIntento}/finalizacion` | Sin cuerpo: el motivo lo fija el servidor (`ENTREGA_ESTUDIANTE`, provisional) | `IntentoVistaProvisional` | Proxy | 409 `INTENTO_FINALIZADO`; los anteriores | No: la segunda finalización da 409 (decisión 3) | UPDATE de estado, motivo y `fin` (reloj de PostgreSQL) | Catálogo de motivos |
| Iniciar o reanudar | — | — | — | — | — | — | — | **Bloqueado:** M4 (elegibilidad y ventana), DD-14 |
| Contenido del caso (preguntas y opciones) | — | — | — | — | — | — | — | **Bloqueado:** contrato de M3 sin respuestas correctas |

Componentes listos para reutilizar cuando se ratifique el contrato:

- `OperacionesParticipacion` (único bean, protegido);
- `SolicitudRespuestaProvisional` (Bean Validation);
- `IntentoVistaProvisional`;
- `ManejadorErroresM5` y `TraductorErroresM5`.

Un controlador debe inyectar `OperacionesParticipacion` y nada más; la prueba
de arquitectura lo verifica.

### Matriz para el simulador

| Necesidad del simulador | Hoy | Cuando se desbloquee |
|---|---|---|
| Identificarse | **Bloqueado** (DD-02; M1 responde 501) | Token de M1 |
| Ver sesiones y convocatorias | **Bloqueado** (M4, elegibilidad) | Contrato de M4 |
| Iniciar o reanudar un intento | **Bloqueado** (M4, DD-14) | Caso de uso de M5 pendiente |
| Ver escenario y preguntas | **Bloqueado** (M3) | Contrato de contenido |
| Responder o cambiar respuesta | Lógica lista; **sin API** | Endpoint según ARQ-008.3 |
| Ver estado y respuestas propias | Lógica lista; **sin API** | Endpoint según ARQ-008.3 |
| Finalizar | Lógica lista; **sin API** | Endpoint según ARQ-008.3 |
| Tiempo restante | Solo informativo en el frontend | Reloj académico (decisión pendiente) |
| Errores | `{code, message}` y códigos estables | Ratificación del catálogo |

## 8. Pruebas disponibles

| Grupo | Clases | Tipo |
|---|---|---|
| Dominio | `IntentoTest`, `EstadoIntentoTest`, `IntentoRespuestasTest`, `IntentoActualizacionRespuestasTest`, `RespuestaTest` | Unitarias |
| Aplicación | Servicios (4), `ParticipacionFacadeTest`, `ResumenIntentoTest`, `ControlAccesoIntentosTest`, `AccesoProtegidoParticipacionTest` | Unitarias |
| Identidad y titularidad | `IdentidadEstudianteDesdeM1Test`, `TitularidadIntentosDesdeM4Test` | Unitarias |
| Configuración y arquitectura | `ConfiguracionParticipacionTest` (contexto Spring), `ArquitecturaAccesoProtegidoTest` | Unitarias |
| Presentación | `TraductorErroresM5Test`, `ManejadorErroresM5Test`, `ProgramacionM4ErroresHttpTest` (MockMvc), DTO | Unitarias |
| Persistencia | `MapeoIntentoTest` (unitaria); `RepositorioIntentosJpaIT` (21) | IT PostgreSQL 16.15 |
| M1–M4–M5 | `AccesoEstudianteM4M5IT` (9) | IT PostgreSQL 16.15 |

Cifras en `M5-INFORME-ENTREGA.md`.

## 9. Decisiones pendientes

| Decisión | Quién decide | Qué bloquea | Propuesta técnica (no es decisión) | Alternativas | Pruebas que faltarán |
|---|---|---|---|---|---|
| **DD-02**: autenticación del estudiante | Dirección Central, M1 | Todo acceso de estudiante | Que M1 implemente `StudentAccessPort` con el mecanismo ratificado. M5 no cambia | — | IT con el proveedor real; HTTP de extremo a extremo |
| **DD-14**: repetición | Dirección Central | Iniciar un segundo intento | Usar `AutorizacionRepeticion` (V007) en el inicio | — | Inicio con y sin autorización; consumo único |
| **ARQ-008.3**: rutas y DTO de M5 | Arquitectura | Endpoints | La tabla de la sección 7 | Otras rutas o nombres | MockMvc y HTTP por operación |
| Decisión 4: intento ajeno | Arquitectura | Código HTTP | 404 (no revela existencia; M4 ya filtra así) | 403 (actual, provisional) | Ajustar `TraductorErroresM5Test` |
| Decisión 7: identidad no disponible | Arquitectura | Código HTTP | Distinguir denegación (403) de indisponibilidad (503 o 501) conservando el código de M1 | Un solo código (403 actual, 401 o 503) | Traductor y adaptador M1 |
| Catálogo de `motivoFinalizacion` | Dirección Central | Valores persistidos | `ENTREGA_ESTUDIANTE`, `TIEMPO_VENCIDO` | Otros nombres | Mapeo y persistencia |
| Cambio de respuesta antes de finalizar | Dirección Central | Endpoint de actualización | Mantener provisional | Prohibirlo | Retirar o confirmar las pruebas provisionales |
| Efecto del retiro de una convocatoria o del estado de la sesión | Dirección Central, M4 | Elegibilidad durante el intento | Contrato de elegibilidad de M4 evaluado en el proxy | Solo al iniciar | Retiro a mitad del intento |
| Reloj académico (límite temporal, `TIEMPO_VENCIDO`) | Dirección Central | Finalización por tiempo | `clock_timestamp()` de PostgreSQL, como ya usa la persistencia | Reloj de la aplicación | Vencimiento, carrera con una respuesta |
| Dependencia para `HistorialParticipacionPort` | Arquitectura | Uso por M4 | Opción a) de la sección 6 | b), c) | IT de M4 con el proveedor real |

## 10. Riesgos

- **Elegibilidad:** la titularidad no la evalúa. Con DD-02 abierta no es
  explotable, pero debe resolverse antes de exponer endpoints.
- **`HistorialParticipacionPort`** no tiene consumidor y su cableado exige una
  decisión de dependencias.
- **404 con la referencia del cliente:** `IntentoNoEncontradoException` incluye
  la referencia enviada (`M5-INCIDENCIAS.md`, sección 4).
- **Validación en Windows:** los cambios de esta rama solo se validaron fuera
  de Maven. Los repositorios de paquete y el contexto de `app` deben
  confirmarse con `-Pdocker-it`.

## 11. Trazabilidad

**El catálogo V27, los 21 casos de uso y ARQ-008.2 no están en el
repositorio.** No se asignan números de requisito ni de caso de uso sin esa
evidencia. Solo se citan identificadores que aparecen en el código o las
migraciones.

| Capacidad de M5 | Identificador con evidencia | Implementado | Probado | Pendiente de integración | Bloqueado | Fuera de alcance |
|---|---|---|---|---|---|---|
| Consultar intento propio | — | Sí | Sí | API | ARQ-008.3, DD-02 | — |
| Registrar respuesta | `tr_respuesta_caso` (V003) | Sí | Sí | API | ARQ-008.3, DD-02 | — |
| Actualizar respuesta | — | Provisional | Sí | API | Decisión académica | — |
| Finalizar intento | `ck_intento_tiempos` (V002), V005, V007 | Sí | Sí | API, M6 | Catálogo de motivos | — |
| Iniciar o reanudar intento | `tr_intento_autorizacion` (V003–V005), F3-02 (V007) | No | — | M4 | DD-14, elegibilidad | — |
| Identidad del estudiante | DD-02 | Adaptador | Sí | — | DD-02 | — |
| Titularidad | — | Sí | Sí | Propiedad de M4 | — | — |
| Historial para M4 | `ParticipacionesPort` (M4) | Sí | Sí (IT) | M4 | Dependencias | — |
| Calificación | — | — | — | — | — | M6 |
| PC-001 | — | — | — | — | — | Fuera de alcance |

La asignación V27 y CU de cada fila queda **pendiente** de que el catálogo se
incorpore al repositorio o lo aporte Dirección Central.

## 12. Plan de cierre

**A. Completables solo por M5**

1. Validar esta rama en Windows (sección 5 de `M5-INFORME-ENTREGA.md`).
2. Cuando se ratifique ARQ-008.3: controladores con MockMvc y pruebas HTTP sobre
   `OperacionesParticipacion`.
3. Cuando se decidan 4 y 7: ajustar `TraductorErroresM5` y sus pruebas.
4. Cuando se ratifique el contrato de elegibilidad de M4: evaluarlo en el proxy.
5. Caso de uso de inicio (después de B, D y F), con `inicio` y
   `limiteTemporal` tomados del reloj de PostgreSQL.

**B. M1:** proveedor real de `StudentAccessPort` (DD-02). M5 ya lo consume y
solo necesita una IT de extremo a extremo.

**C. M3:** contrato de lectura del contenido del caso para el intento
(escenarios, preguntas y opciones, sin respuestas correctas ni rúbrica).

**D. M4:**

- elegibilidad, ventana y límite temporal;
- activar `consulta-bd0052`;
- consumir `HistorialParticipacionPort`;
- protocolo de bloqueo con el futuro inicio;
- resolver las 17 IT omitidas (EXT-M4-01).

**E. M6:** contrato de "intento finalizado", publicado después de confirmar la
transacción. M5 no invoca M6 hoy.

**F. Arquitectura y Dirección Central:** DD-02, DD-14, ARQ-008.3, decisiones 4
y 7, catálogo de motivos, cambio de respuesta, retiro y elegibilidad, reloj
académico y dependencia del historial.
