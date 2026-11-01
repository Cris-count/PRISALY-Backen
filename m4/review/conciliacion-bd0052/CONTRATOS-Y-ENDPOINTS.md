# Contratos, endpoints y diagramas M4
## Puertos internos
Se conservan sin cambio los records y firmas de com.prisaly.m4.api. ProgramacionParticipacionPort consulta contexto por convocatoria y estudiante persistidos; ProgramacionAutenticadaPort obtiene identidad mediante StudentAccessPort. ContextoProgramacion ofrece estado exacto de sesión/caso, casoActivo, ventana prevista, fechas reales opcionales, duración, autorización/retiro de convocatoria y observadoEn. No ofrece una decisión de inicio/reanudación/repetición.

M3: adaptador SQL con Caso FOR UPDATE, inserción Sesion y triggers oficiales dentro de la misma transacción. Requiere revisión del protocolo con el Integrante 2. M5: app ensambla ParticipacionesPort con HistorialParticipacionPort mediante HistorialM4M5Configuration. M4 obtiene los UUID de todas sus convocatorias con ConvocatoriaRepository; M5 consulta sus participaciones persistidas. No se accede a repositorios internos ajenos ni se crea una dependencia Maven M4→M5.

## Integración M4–M5 vigente

- M5 resuelve `Intento.idParticipacion → Participacion.idConvocatoria` desde sus datos persistidos. No autoriza usando una convocatoria enviada libremente por Angular.
- M1 debe aportar `studentId`, distinto de `userId`. `ProgramacionAutenticadaPort` no acepta un estudiante declarado por el cliente; DD-02 conserva denegación segura.
- `Optional.empty()` representa convocatoria inexistente o ajena, sin revelar titularidad. El control provisional M5 distingue inexistente (404) de ajena (403); la elección académica403/404 sigue pendiente de ratificación.
- `ConsultaProgramacionNoDisponibleException` significa indisponibilidad, nunca ausencia o permiso. M5 la traduce a503. Su control de titularidad ya está conectado a M4; las traducciones de identidad siguen pendientes de ratificación.
- El contexto no contiene hash/código de autorización, respuestas, calificaciones ni una autorización de repetición. La lectura es una instantánea SELECT; no reserva ni mantiene bloqueos después de devolver el DTO.
- `observadoEn` usa `clock_timestamp()` PostgreSQL. M5 ya persiste fin y actualización con clock_timestamp() PostgreSQL; falta ratificar una autoridad temporal común, bordes y tolerancia. El reloj del navegador no decide vigencia.
- Para reanudar, M5 necesita el mismo intento persistido, su inicio, límite temporal, estado y respuestas confirmadas, además de consultar nuevamente el contexto M4. No debe crear otro intento ni reiniciar el reloj por una reanudación.
- M5 dispone del motivo `TIEMPO_VENCIDO`, pero faltan la regla efectiva de vencimiento y su mecanismo de detección. Guardar una finalización y luego lanzar una excepción en la misma transacción puede revertirla: el caso de uso debe resolver esa frontera antes de habilitarse.
- Repetición y número de intento corresponden a M5/DD-14. Las reglas oficiales V005/V007 de historial y consumo atómico de autorización no se sustituyen por un booleano M4. M4 no ofrece `puedeRepetir`.

La resolución persistida, el retiro y el fallo real de consulta se verifican en las pruebas PostgreSQL M5; consultar [resultados actuales](../revision-m4-m5/evidence/RESULTADO.md). No demuestran inicio/reanudación productivos ni el control de acceso conectado a un endpoint.

### Protección de historial desde M4

El puente de app exige la transacción de escritura del llamador. SesionAcademicaService bloquea primero la fila de sesión y luego consulta el historial. Una participación basta para impedir edición o reasignación (409 M4-SESION-CON-PARTICIPACIONES); los intentos quedan cubiertos por su FK obligatoria a Participacion. Se incluyen convocatorias retiradas. Sin participaciones se conserva la validación normal de M4.

La ausencia del proveedor o el fallo de consulta provoca 503 M4-PROVEEDOR-NO-DISPONIBLE, mensaje público genérico y rollback. La consulta sin transacción de escritura se rechaza. M4 conserva su respaldo seguro para ensamblados sin app/M5.

El futuro inicio de participaciones debe adquirir el mismo bloqueo de sesión antes de insertar. Este puente no implementa inicio/reanudación ni permite afirmar que esa carrera futura esté resuelta. No hay cambios de esquema ni de identidad estudiantil.

## Diccionario HTTP
SesionController atiende /m4/sesiones; ConvocatoriaController atiende /m4/sesiones/{sesionId}/convocatorias. app agrega /api. La separación conserva las rutas existentes. Identidad del docente desde M1. X-Docente-Id es opcional, solo comprueba coincidencia con identidad autenticada.

| Método y ruta relativa | Entrada | Respuesta |
|---|---|---|
| POST / | casoId, titulo, mensajePersonalizado opcional, fechaInicio, fechaFin, tiempoMaximoParticipacionSegundos | 201 sesión |
| GET / | docenteId opcional, debe coincidir | 200 sesiones del docente |
| GET /{sesionId} | UUID | 200 sesión propia |
| PUT /{sesionId} | titulo, mensajePersonalizado, fechaInicio, fechaFin | 200; requiere historial M5 disponible y ausencia de participaciones |
| PATCH /{sesionId}/estado | nuevoEstado: EnCurso o Finalizado | 200; State valida transición |
| POST /{sesionId}/convocatorias | estudianteId | 201 convocatoria, requiere proveedor M7 |
| GET /{sesionId}/convocatorias | UUID | 200 convocatorias de sesión propia |
| GET /{sesionId}/acceso/{estudianteId} | UUID | 410; nunca autoriza |

No hay endpoint REST para cambiar caso: es operación interna de servicio. No se añaden endpoints abrir/cancelar/retiro/repetición.

Fechas ISO8601 con zona (por ejemplo 2026-11-09T13:00:00Z), convertidas a Instant. Fecha fin debe superar inicio. Duración entera positiva obligatoria al crear; no se deduce del rango. titulo máximo150. Entrada descripcion se acepta como alias de mensajePersonalizado; salida usa mensajePersonalizado. El cambio de nombres de estado y la duración obligatoria requieren adaptación del consumidor; no se aceptan silenciosamente estados anteriores.

Sesión de salida: id, casoId, docenteId, titulo, mensajePersonalizado, fechaInicio, fechaFin, estado, creadoEn, fechaInicioReal, fechaFinReal, tiempoMaximoParticipacionSegundos. Sin version, actualizadoEn ni hash de autorización.
Convocatoria de salida: id, sesionId, estudianteId, convocadoEn, estadoAutorizacion, fechaRetiro.

Errores: {"code":"M4-…","message":"…"}; 400 datos/UUID/fechas, 401 sin autenticación,403 sin permiso/ajeno,404 sesión ausente,409 conflicto académico/duplicado/bloqueo,410 acceso retirado,503 proveedor no disponible,500 inesperado. Spring conserva405/406/415 y cabeceras de protocolo. No se expone SQL en el cuerpo. El formato coincide estructuralmente con M1/M5, pendiente ratificación central.

## Clases
```mermaid
classDiagram
  SesionController --> SesionAcademicaService
  ConvocatoriaController --> SesionAcademicaService
  SesionAcademicaService --> SesionRepository
  SesionAcademicaService --> CasoAcademicoPort
  SesionAcademicaService --> IdentidadPort
  SesionAcademicaService --> NotificacionPort
  SesionRepositoryAdapter ..|> SesionRepository
  SesionRepositoryAdapter --> JpaSesionRepository
  SesionRepositoryAdapter --> ParticipacionesPort
  SesionAcademica --> SesionState
  SesionState <|.. SinIniciarState
  SesionState <|.. EnCursoState
  SesionState <|.. FinalizadoState
  SesionAcademica "1" --> "*" Convocatoria
  CasoAcademicoBd0052Adapter ..|> CasoAcademicoPort
  ProgramacionParticipacionPort --> ContextoProgramacion
```

## Programación
```mermaid
sequenceDiagram
  participant D as Docente
  participant C as Controller
  participant S as Servicio M4
  participant I as Identidad M1
  participant DB as PostgreSQL oficial
  D->>C: POST sesión, duración y fechas con zona
  C->>I: Resolver principal docente
  C->>S: crearSesion
  S->>I: Verificar permiso
  S->>DB: BEGIN, advisory lock caso/docente
  S->>DB: SELECT Caso FOR UPDATE
  DB-->>S: Publicado y activo
  S->>DB: Comprobar duplicación
  S->>DB: INSERT Sesion, flush
  Note over DB: V007/V009 validan RF09 y marcan F3-03
  S->>DB: Comprobar fechaBloqueoAcademico
  alt Cualquier fallo
    S->>DB: ROLLBACK sesión y marca
    S-->>C: Error seguro
  else Éxito
    S->>DB: COMMIT
    S-->>C: Sesión SinIniciar
    C-->>D: 201
  end
```
