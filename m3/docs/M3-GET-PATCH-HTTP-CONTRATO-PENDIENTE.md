# GET/PATCH M3: entrega de composición y propuesta pendiente

Los controladores M3 registran `/v1/casos/{idCaso}`; APP añade el contexto `/api`.
No se modifican M1 ni APP. Las pruebas positivas usan políticas controladas.

## Contratos que debe conectar Cristian

- Identidad existente: `CasoCurrentUserPort.currentUserId(): UUID`, acreditada por
  `M3CaseAuthorizationAdapter` mediante `IdentityAccessPort.currentUser()` de M1.
  Ningún payload acepta usuario o propietario.
- Lectura: `AutorizacionConsultaCasoPort.verificarAcceso(UUID usuario, UUID caso): Decision`
  y `verificarVisibilidad(UUID usuario, UUID caso, CasoEstado estado, boolean activo): Decision`.
  Solo `AUTORIZADO` permite continuar. Falta proveedor institucional y política expresa
  de estados/actividad visibles. No se infiere permiso de CREAR_CASO ni de la propiedad.
- Escritura: `CaseAuthorizationPort.canModifyCase(String userId, String caseId): boolean`.
  El adaptador APP vigente devuelve `false`; requiere política efectiva ratificada,
  incluidos estados editables. No se propone un código de permiso nuevo.
- Titularidad: ambos recorridos exigen UUID autenticado igual al `docenteId` persistido.
  No hay excepciones para casos ajenos. La persistencia conserva propietario e identidad.
- Concurrencia HTTP: `CasoPatchConcurrencyContract.expectedFingerprint(HttpHeaders): String`.
  No hay implementación predeterminada ni encabezado obligatorio instalado. APP solo
  debe aportar este proveedor tras ratificar el transporte; debe rechazar valores
  ausentes, múltiples, ambiguos o malformados. M3 exige `m3-v2:[0-9a-f]{64}` y compara
  la huella en la transacción bloqueada antes de escribir.

## Propuesta mínima de contrato HTTP para ratificación

GET 200: `{ "idCaso": "UUID", "titulo": "Caso", "tiempoEstimadoSegundos": 300, "estado": "BORRADOR" }`.
Solo estos cuatro campos. El UUID debe ser completo.

PATCH `application/json`: objeto parcial con `titulo`, `tiempoEstimadoSegundos`,
`historia`, `desarrolloSituacional`. Omisión conserva; null explícito solo borra
historia/desarrollo. No se aceptan campos desconocidos, duplicados, estructuras
académicas, identificadores, dueño, actividad, estado ni fechas. Respuesta 204
sin cuerpo solo después de confirmar la transacción.

Propuesta de transporte a coordinar: `If-Match` con una única ETag fuerte cuyo
valor sea la huella m3-v2 entre comillas. **No está implementada ni ratificada.**
Debe acordarse también cómo obtiene el cliente esa ETag sin ampliar el JSON GET,
los códigos de precondición y la configuración CORS de APP. Las pruebas usan
exclusivamente `X-Test-Fingerprint`, que no es un contrato productivo ni un bean.
ARQ-008.3 completo no está disponible en este checkout; las referencias REST de
M3 registran expresamente que no hay transporte de huella aprobado.

Los controladores implementados devuelven: 400 entrada inválida, 401 identidad
ausente, 403 permiso/propiedad/visibilidad denegados, 404 ausencia tras autorización,
409 huella obsoleta o bloqueo/estado archivado, 500 fallo técnico sanitizado.
Sin contrato de concurrencia, PATCH autorizado retorna 503 y no escribe.
Esta correspondencia HTTP y la propuesta de payload requieren conciliación con
Arquitectura antes de declarar operación institucional; no sustituye su aprobación.

## Pruebas requeridas para la composición institucional

Repetir con M1 real: identidad ausente/inactiva, permiso denegado o indisponible,
docente ajeno, estados/actividad no visibles, recurso inexistente, proveedor que
falla, UUID inválido y JSON seguro sin respuestas/rúbricas. PATCH además debe
probar huella ausente/ambigua/obsoleta, campos protegidos, bloqueo académico,
rollback, un único ganador concurrente y relectura PostgreSQL tras HTTP.

Estado: endpoints implementados y verificables con composición controlada.
Operación institucional pendiente de los proveedores anteriores y de ratificación
HTTP; no se declaran habilitados para usuarios reales ni se habilita publicación.
