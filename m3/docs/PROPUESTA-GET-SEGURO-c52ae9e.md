# M3 — Preparación contractual de GET seguro

Fecha: 5 de noviembre de 2026, America/Bogota. **Documento de propuesta; no es aprobación de contrato ni implementación del endpoint.** Inspección realizada exclusivamente en m3-sincronizado. No se modificaron Java, pruebas, POM, configuración, migraciones ni contratos existentes. No se ejecutó Maven en esta preparación documental.

## 1. SHA real del repositorio

- Rama: feature/m3-gestion-casos.
- HEAD: `c52ae9e101087cde5ea921f71e1245bfa1e9e015`.
- Ruta: `C:\Users\57316\OneDrive\Escritorio\PRISALY\m3-sincronizado`.
- Se ejecutaron status, fetch origin --prune y rev-parse de HEAD, origin/develop y origin/feature/m3-gestion-casos antes de preparar el documento.

## 2. Estado de sincronización

Las tres referencias coinciden en c52ae9e; divergencia HEAD/origin/develop: 0/0. Árbol limpio al iniciar. No se efectuó reset, merge, rebase ni push. Este informe será el único archivo nuevo sin seguimiento; no se agrega al índice ni se crea commit.

Los resultados previos de 15 unitarias y 9 IT en c52ae9e son evidencia histórica de la regresión de sincronización, no pruebas nuevas de GET. GET continúa sin implementar; toda su matriz de aceptación está NOT RUN.

## 3. Contratos internos reutilizables

Los nombres solicitados existen sin cambios. Las rutas siguientes son relativas a la raíz del repositorio.

| Componente / responsabilidad | Métodos y datos devueltos | Dependencias / autorización | Pruebas existentes inspeccionadas | Reutilización propuesta |
| --- | --- | --- | --- | --- |
| m3/api/ContextoAcademicoConsultaPort, bajo src/main/java/com/prisaly/m3: consulta interna protegida | consultar(UUID idCaso, String capacidad) → Optional<ContextoAcademicoPersistido>; agregado estructural completo, no DTO REST | Contrato de capacidad opaca; Optional vacío significa ausencia solo tras verificación institucional | ContextoAcademicoProtectedReaderTest y ContextoAcademicoProducerReadIT | Conservar sin cambios. No usar como endpoint personal ni convertir Bearer de usuario en capacidad |
| m3/application/port/AutorizacionContextoAcademicoPort: verificación institucional requerida | verificar(String, UUID, Operacion) → AUTORIZADO/DENEGADO/NO_DISPONIBLE; operación CONSULTAR_CONTEXTO_EVALUABLE | Su contrato exige autenticidad, consumidor M6_EVALUACION, caso exacto, operación, expiración y revocación; no implementa esas garantías localmente | ProtectedReaderTest simula decisiones; no prueba un emisor/verificador criptográfico real | No reutilizar como política docente ni ampliar consumidor/operación en este incremento; M6 está fuera de alcance |
| m3/infrastructure/persistence/jpa/ContextoAcademicoProtectedReader: orquesta verificación y lectura | Implementa consultar; devuelve también esCorrecta, explicacion y rúbrica | Verificador anterior + CasoJpaAggregateReader. Sin bean automático; capacidad/verificador ausentes, decisión nula/no disponible o excepción del verificador deniegan antes de leer. Fallos de persistencia/integridad se propagan | Seis métodos ProtectedReaderTest; cuatro ProducerReadIT, incluidos autorización antes de lectura, ausencia autorizada y snapshot posterior a verificación | Conservar la garantía fail-closed como referencia de diseño; no exponer este resultado íntegro ni registrar el lector para GET |
| m3/application/port/CasoRepositoryPort: persistencia del agregado | findById(String) → Optional<Caso>; insert(Caso), update(Caso). Datos de caso y seis tablas relacionadas | Implementado por CasoPersistenceAdapter; lectura consistente por CasoJpaAggregateReader. El puerto no autoriza al usuario; update(Caso) está deshabilitado en el adaptador | CasoJpaAggregateReadIT, CasoJpaAggregateTransactionIT, CasoPersistenceAdapterIT; POST IT usa lectura independiente | **Reutilización principal para GET**, detrás de servicio de consulta autorizado. No crear repositorio, entidad o SQL duplicado |
| m3/application/service/CasoApplicationService: coordinación actual | createDraft, publish transitorio, replaceEvaluableContent transitorio, lockAfterFirstSessionScheduled, createIndependentCopy transitoria, isAvailableForScheduling | CaseAuthorizationPort + CasoRepositoryPort. Crear exige canCreateCase; modificar/publicar exigen sus capacidades. No tiene método de consulta protegido | CasoApplicationServiceTest; M3CasoCreationTest/IT para creación | Conservar creación y operaciones existentes. Un servicio específico de consulta no duplicaría una consulta de aplicación que hoy no existe; no llamar publish/copy/replace para leer |
| app/config/M3CaseAuthorizationAdapter: puente M1–M3 vigente | currentUserId() → UUID; canCreateCase(String,String) → boolean; canModifyCase/canPublishCase → false | IdentityAccessPort; cuenta activa, UUID autenticado igual a caller/propietario y hasPermission(CREAR_CASO). No consulta hasRole para crear ni dispone de autorización de lectura | Nueve M3CaseAuthorizationAdapterTest; M3CasoCreationTest; M3CasoCreationIT verifica proveedor único | Reutilizar identidad y único bean. Solo ampliar con un puerto local de consulta si Dirección ratifica su política; no alterar CaseAuthorizationPort ni reutilizar CREAR_CASO tácitamente |
| m1/application/port/IdentityAccessPort: identidad institucional | currentUser()/requireUser(UUID) → AuthenticatedUser; hasPermission(UUID,String), hasRole(UUID,String) → boolean | IdentityAccessService obtiene UUID de SecurityContext y carga usuario activo, rol y asignaciones habilitadas. No proporciona contenido académico ni política de recursos | Ocho IdentityAccessServiceTest; pruebas actuales APP/M1 de login y creación | Consumir el contrato existente desde APP; no modificar M1 ni interpretar la existencia de hasPermission(String) como aprobación de un código nuevo |

Apoyo de persistencia ya disponible: CasoJpaAggregateReader usa transacción read-only REQUIRES_NEW/REPEATABLE_READ, ignora cachés de Hibernate y arma un snapshot de seis tablas con orden físico. No ve cambios sin commit de una transacción exterior y puede necesitar una conexión adicional. CasoPersistenceMapper devuelve un Caso separado de JPA. No crear un segundo lector para este GET ni usar lecturas sucesivas para autorizar un snapshot y devolver otro.

Pruebas del lector protegido: absentOrBlankCapabilityOrMissingVerifierNeverReads; deniedFabricatedExpiredRevokedWrongCaseOrOperationNeverReads; unavailableNullDecisionOrVerifierFailureDeniesWithoutLeakingCapability; successfulVerificationPrecedesExactlyOneCompleteRead; onlyAuthorizedMissingCaseReturnsEmptyAndSecurityDoesNotDiscloseExistence; persistenceAndIntegrityErrorsAreNotConvertedToSecurityOrEmpty. Los escenarios de capacidad expirada/revocada son dobles de resultados institucionales, no una validación real de emisión. No se ejecutaron nuevamente en esta etapa.

## 4. Endpoints existentes y evidencia REST

| Elemento | Estado comprobado |
| --- | --- |
| POST /api/v1/casos | Existe: CasoCreationController.create; context-path /api + @RequestMapping /v1/casos + @PostMapping. DTO CrearCasoRequest/CrearCasoResponse. Crea borrador y confirma commit antes de 201 |
| GET de caso | No hay @GetMapping de casos, controlador ni DTO GET. La ausencia de mapping no define política 403/404 |
| Ruta documentada de GET | DC-M3-REST-GET-PATCH.md:4–8 y DC-M3-REST-03.md:221 reconocen GET /api/v1/casos/{idCaso} aprobado por instrucción de DT. No constituyen la especificación completa ARQ-008.3 |
| UUID | Persistencia usa UUID; CasoRepositoryPort recibe String y CasoPersistenceMapper valida representación canónica completa. El formato HTTP y error deben ratificarse; no aceptar identidad docente desde parámetros |
| Autorización, DTO, estados y reserva GET | Sin contrato completo aprobado localizado; no se infieren del POST ni del contrato de capacidades |
| Manejador actual | CasoCreationExceptionHandler está limitado por assignableTypes a CasoCreationController y usa errores CASE_CREATION; no cubre automáticamente un controlador GET nuevo |

Búsqueda: inventario de archivos y contenido en docs, m3/docs y fuentes; términos ARQ-008.3, OpenAPI, Swagger, GET/casos y visibilidad. **No se localizó el texto completo vigente de ARQ-008.3** en este checkout. docs/ALINEACION-M4-INTEGRACIONES-2026-11-04.md:23 también registra esa ausencia documental. No se afirma inexistencia institucional: Arquitectura debe aportar la versión o ratificar el contrato concreto. No se inventa otra ruta.

Las respuestas actuales del POST son 201, 400, 401, 403, 409 y 500 según sus controladores/manejadores; no se trasladan como contrato del GET. HttpApiConfig exige autenticación para rutas no públicas y configura 401/403 generales, pero no autoriza lectura de casos por permiso, propietario o estado.

## 5. Autorizaciones verificadas por inspección

Cadena efectiva: Bearer → ProvisionalAccessTokenStore → ProvisionalBearerAuthenticationFilter → IdentityAccessService.requireUser → principal UUID en SecurityContext → IdentityAccessPort.currentUser. M1 recarga cuenta activa y asignaciones habilitadas; hasPermission vuelve a consultar la identidad. Los permissionCodes de un DTO suministrado/caché no sustituyen la consulta efectiva.

No cambiar el filtro ni el esquema de autenticación de M1. No convertir un usuario registrado con rol Estudiante en lector de casos por tener sesión; tampoco inferir una política docente del rol o de CREAR_CASO. El adaptador de creación verifica dueño del borrador, no dueño de un caso existente.

| Consumidor / recurso | Garantía actual | Condición propuesta para GET, sin aprobación implícita |
| --- | --- | --- |
| Docente propietario autorizado | M1 identifica y resuelve permisos; no hay autorización GET | Exigir elegibilidad institucional ratificada, propiedad y estado visible. Solo entonces proyectar datos |
| Docente no propietario | La propiedad no se resuelve como permiso de consulta | Denegación por defecto; ninguna excepción sin decisión explícita. HTTP del caso existente no visible: pendiente |
| Estudiante | Identidad/participación no aprueban consulta docente ni respuestas correctas | Propuesta: excluir del primer GET docente; ratificar consumidor. No ampliar rutas estudiantiles ni usar capacidades M6 |
| Sin autenticación / Bearer inválido | Spring Security no autentica y responde 401 en rutas protegidas | Mantener la cadena activa; no permitir que UUID, cabecera de docente o rol enviado por cliente la sustituyan |
| Sin permiso efectivo / revocado | M1 no concede permiso por defecto | No devolver contenido; no inventar código de permiso ni contrato de error del recurso |
| Caso inexistente | Optional.empty en persistencia, sin política HTTP propia | Determinar tratamiento tras elegibilidad de consulta; distinguir internamente ausencia de fallo técnico |
| Caso no visible por estado | Estados almacenados Borrador/Publicado/Archivado y activo; no política GET | Denegar sin exponer datos hasta ratificar; **no se elige 403 o 404** |

La respuesta de denegación no debe filtrar existencia, propietario, respuestas, rúbrica, tokens ni errores SQL. Las diferencias de autenticación y de autorización deben preservarse sin fijar en este documento el código para recursos ocultos. Las decisiones de estado de caso y de activo/visible de sus hijos son distintas y requieren definición.

## 6. Campos disponibles en persistencia

Fuente: CasoJpaAggregateSnapshot y CasoJpaAggregateReader, mapeados por CasoPersistenceMapper. Su disponibilidad no autoriza su exposición.

| Tabla / estructura | Campos actuales | Consideración para proyección |
| --- | --- | --- |
| Caso | idCaso, idDocente, nombre, historia, desarrolloSituacional, tiempoEstimadoSegundos, estado, activo, fechaBloqueoAcademico, fechaCreacion, fechaActualizacion | Dominio llama titulo a nombre; representación JSON y enum por ratificar. Propietario/flags sirven para política, no tienen que exponerse |
| Escenario | idEscenario, idCaso, titulo, contexto, instrucciones, orden, activo | Mantener orden físico, sin renumerar; definir visibilidad. No inventar descripcion a partir del campo legado sin mapping |
| Pregunta | idPregunta, idEscenario, enunciado, textoAyuda, estadoEdicion, visible, orden, fechaCreacion, fechaActualizacion | estadoEdicion/visible no son por sí solos autorización del consumidor; definir inclusión de borradores/ocultas |
| OpcionRespuesta | idOpcion, idPregunta, texto, esCorrecta, explicacion, orden | esCorrecta y explicacion pueden revelar evaluación; excluir de propuesta inicial |
| Rubrica | idRubrica, idCaso, nombre, descripcion, puntajeTotal, definicionEvaluacion JSONB, fechaCreacion | Mantener fuera del DTO inicial propuesto. No interpretar JSONB ni decidir completitud DD-03 |
| RubricaPregunta | idRubricaPregunta, idRubrica, idPregunta, valorPregunta BigDecimal, criterio | Mantener fuera del DTO inicial propuesto; no convertir a CriterioRubrica |

Casos sin escenarios/preguntas y rúbrica ausente son representables; conservar la diferencia null/lista vacía. Las asociaciones de rúbrica son un Set sin orden contractual. La lectura actual puede devolver Archivado y datos reservados: no aplicar autorización de presentación por simple existencia de un agregado.

## 7. Contratos faltantes para Arquitectura

| ID | Falta ratificar | Responsable |
| --- | --- | --- |
| G-01 | Versión vigente y conformidad de ruta GET reconocida con ARQ-008.3; UUID y formato de respuesta/error | Arquitectura |
| G-02 | Consumidor inicial (propuesto: personal docente propietario), política institucional o código existente aplicable; excepciones y rol si corresponde | Dirección + M1/M3 |
| G-03 | Estados visibles de caso y efecto de activo, F3-03, escenario activo, pregunta visible/estadoEdicion; política de Archivado sin implementar transiciones | Dirección académica + Arquitectura |
| G-04 | DTO exacto, campos reservados, nombres JSON, nulidad y orden; si el primer GET devuelve contenido o solo ficha | Arquitectura + M3 |
| G-05 | Tratamiento de existente no visible, inexistente y permiso insuficiente; cuerpo de error y política antienumeración. 403/404 no decidido aquí | Dirección + Arquitectura |
| G-06 | Frontera de autorización de lectura en M3 y ensamblaje APP coordinado; interfaz local propuesta sin cambiar CaseAuthorizationPort ni M1 | M3 + Cristian/APP + Arquitectura |

Si no existe una política de consulta aprobada expresable con contratos/capacidades institucionales actuales, no se solventa creando un permiso nuevo ni devolviendo true como configuración provisional: el incremento permanece bloqueado.

## 8. Propuesta mínima de GET — no implementada

### Reutilización y flujo

Recomendación para ratificación: consulta de caso por personal docente autorizado, sobre la ruta GET ya reconocida documentalmente. No usar ContextoAcademicoConsultaPort ni su verificador para este consumidor; sus garantías son de contexto evaluable M6. No duplicar CasoJpaAggregateReader, CasoPersistenceAdapter ni entidades.

Flujo propuesto:

1. Spring Security autentica con el mecanismo actual; CasoCurrentUserPort obtiene UUID institucional. No aceptar docenteId, permisos ni capacidades desde la petición como acreditación.
2. Una política local de consulta, respaldada en APP por IdentityAccessPort existente, verifica elegibilidad general de consulta antes de cargar el agregado. Un verificador ausente, error o decisión indeterminada no habilita acceso.
3. Validar el UUID conforme al contrato ratificado y llamar una vez a CasoRepositoryPort.findById. Optional vacío conserva semántica interna de ausencia; fallos técnicos no se convierten en vacío.
4. Comprobar propiedad, estado y proyección aplicable con los metadatos del mismo Caso cargado. No releer para devolver otro snapshot ni suponer que una transacción exterior cambia REQUIRES_NEW del lector. La semántica ante revocación concurrente debe quedar documentada; no se promete atomicidad entre BD M1 y snapshot M3 inexistente hoy.
5. Solo después de autorizar, construir el DTO por lista explícita de campos y responder. Ningún Caso, snapshot, entidad JPA o ContextoAcademicoPersistido se serializa directamente. Denegación y ausencia se traducen conforme a G-05.

### Archivos propuestos, sujetos a ratificación

Todos los nombres nuevos son propuestas, no componentes existentes ni firmas aprobadas:

| Archivo | Cambio mínimo previsto |
| --- | --- |
| m3/src/main/java/com/prisaly/m3/application/port/CasoConsultaAuthorizationPort.java — nuevo | Política de elegibilidad y evaluación de metadatos del recurso para consulta. Diseñar firmas después de G-02/G-03/G-06; sin dependencia de Spring Security/M1 ni decisión permisiva predeterminada |
| m3/src/main/java/com/prisaly/m3/application/service/ConsultarCasoService.java — nuevo | Coordinar identidad, autorización y lectura usando los puertos existentes. No duplica createDraft ni implementa persistencia. Puede devolver el Caso ya autorizado internamente para su proyección de presentación; nunca es respuesta HTTP directa |
| m3/src/main/java/com/prisaly/m3/presentation/CasoQueryController.java — nuevo | Adaptador GET de la ruta ratificada, UUID y DTO; sin SQL ni lógica institucional en el controlador |
| m3/src/main/java/com/prisaly/m3/presentation/dto/CasoConsultaResponse.java — nuevo | DTO explícito con registros anidados y conversión desde el Caso autorizado; opcional mapper de presentación separado si el tamaño lo justifica |
| m3/src/main/java/com/prisaly/m3/presentation/CasoQueryExceptionHandler.java — nuevo | Errores de consulta conforme G-05; no reutilizar mensajes CASE_CREATION ni ampliar tácitamente el advice de POST |
| app/src/main/java/com/prisaly/config/M3CaseAuthorizationAdapter.java — podría modificarse | Implementar el puerto local adicional solo con política ratificada usando M1 existente. Conservar único bean y capacidades de creación/modificación/publicación; no añadir proveedor competidor de CaseAuthorizationPort |
| app/src/main/java/com/prisaly/config/M3CasoCreationConfig.java — podría modificarse | Registrar el servicio de consulta y reutilizar la instancia institucional; coordinar con Cristian. No crear fallback permisivo ni usar Primary para ocultar ambigüedades |
| Pruebas nuevas M3/APP | Las de la sección 9. Conservar pruebas históricas; actualizar únicamente la expectativa de rutas cuando GET esté autorizado e implementado |

No modificar IdentityAccessPort, CaseAuthorizationPort, el lector por capacidades, M1, M6, HttpApiConfig ni persistencia/migraciones. Cualquier necesidad de modificar un contrato aprobado debe volver a Arquitectura. No se requieren dependencias Maven adicionales.

### DTO candidato para discusión, no contrato oficial

Para consulta de contenido, propuesta inicial por lista explícita:

- Caso: idCaso, titulo (mapeado de nombre), historia, desarrolloSituacional, tiempoEstimadoSegundos, estado y escenarios.
- Escenario: idEscenario, titulo, contexto, instrucciones, orden y preguntas.
- Pregunta: idPregunta, enunciado, textoAyuda, orden y opciones.
- Opción: idOpcion, texto y orden.

Excluir inicialmente esCorrecta, explicacion, rúbrica completa, valorPregunta, criterio, definicionEvaluacion, UUID del docente, credenciales y metadatos técnicos. No añadir huella m3-v2/ETag ni contrato de PATCH a este incremento. La inclusión/filtrado de elementos ocultos y estados no se decide aquí. Si Arquitectura aprueba solo ficha básica o exige otra proyección, ajustar esta lista antes de programar. Omitir campos de corrección no sustituye autorizar al consumidor del contenido narrativo.

HTTP: éxito 200 y UUID inválido 400 son propuestas para ratificación; 401 ya proviene de la cadena de autenticación actual. Permiso insuficiente, ajeno, estado no visible e inexistente requieren G-05. Error técnico debe ser respuesta de servidor sanitizada conforme contrato; no 404 por fallo de BD. No se asigna 403 o 404 al caso existente no visible.

## 9. Matriz de pruebas diseñada

**Todas las pruebas GET siguientes están NOT RUN y aún no están programadas.** Las expectativas condicionadas se cerrarán después de G-01–G-06, sin convertir un caso bloqueado en PASS.

| ID / nivel | Escenario | Aceptación propuesta |
| --- | --- | --- |
| U-01 unitaria aplicación | Identidad ausente, inválida o UUID incoherente | Denegar antes de consultar contenido; sin respuesta parcial |
| U-02 unitaria política | Propietario activo y elegibilidad institucional aprobada | Una lectura, autorización del snapshot y resultado solo tras aprobación |
| U-03 unitaria política | Permiso ausente/revocado; permissionCodes aparente, pero M1 deniega | Sin autorización ni consulta de contenido después del rechazo general |
| U-04 unitaria política | Docente ajeno aun con permiso general; estudiante con rol/token | Denegar por defecto según G-02; no inferir permiso por rol o sesión |
| U-05 unitaria política | Cada combinación ratificada de estado/activo/F3-03 y flags de hijos | Solo contenido expresamente visible; Archivado queda según G-03 |
| U-06 unitaria aplicación | Repositorio vacío frente a error técnico/integridad | Semánticas distintas, sin conversión de excepción a ausencia; traducción HTTP pendiente |
| U-07 unitaria proyección | Agregado completo con opciones correctas, explicación y rúbrica | DTO contiene exactamente la lista aprobada; ausencia de propiedades reservadas aun si valen false/null |
| U-08 unitaria proyección | Listas vacías, textos null, Unicode suplementario, órdenes físicos con huecos | Preservar valores y orden sin fabricar datos, filtrar conforme política ratificada |
| U-09 unitaria ensamblaje | Política ausente, NO_DISPONIBLE, error técnico o decisión no reconocida | Fallar cerrado; cero contenido y sin datos sensibles en error/log |
| H-01 MVC/HTTP | Método/ruta/UUID/cuerpo JSON de salida | GET ratificado, content-type y nombres exactos; no serialización directa de entidades |
| H-02 MVC/HTTP | UUID malformado, incompleto y formato no canónico | Resultado conforme G-01; no consulta persistente para identificador rechazado |
| H-03 MVC/HTTP | Propietario visible, inexistente, ajeno y estado no visible | Afirmaciones de HTTP/cuerpo solo tras G-05; no fijar 403/404 ahora |
| H-04 MVC/HTTP | Respuesta denegada y excepción SQL/integridad | No filtra respuestas correctas, propietario, tokens, SQL o existencia no autorizada |
| P-01 PostgreSQL | Lectura del caso real y sus seis tablas, nulos y orden físico | Mismos lectores existentes, DTO exacto de snapshot confirmado, sin asociaciones ajenas |
| P-02 PostgreSQL | Actualización concurrente mientras se lee | Snapshot consistente REPEATABLE_READ; no mezclar versiones de raíz/hijos entre autorización y respuesta |
| P-03 PostgreSQL | Caso existente vacío frente a UUID ausente | Diferencia interna correcta, sin exigir rúbrica completa DD-03 para leer la proyección aprobada |
| P-04 PostgreSQL | GET repetido de caso bloqueado y controles de solo lectura | No altera estado, fechas, huellas, F3-03, filas ni triggers. SQL read-only sin escrituras de negocio |
| P-05 PostgreSQL | Lector REQUIRES_NEW y pool limitado | Conservar suspensión/reanudación y fallo técnico acotado; no disfrazar timeout como caso inexistente |
| S-01 seguridad integrada | Sin token, token inválido/revocado, cuenta inexistente/inactiva | M1 y Spring Security reales; 401 de autenticación según cadena; sin contenido |
| S-02 seguridad integrada | Token válido, permiso efectivo revocado entre peticiones | Nueva petición denegada, sin usar caché o CREAR_CASO como autorización GET |
| S-03 seguridad integrada | Identidad/rol/propietario falsificados en cabeceras o parámetros | Solo identidad M1 acredita; no se evade G-02/G-03 |
| S-04 seguridad integrada | Caso ajeno y no visible frente a inexistente | Política antienumeración acordada, sin diferencias de cuerpo/metadatos prohibidas; código pendiente |
| S-05 seguridad integrada | Contexto completo y proveedores | Un proveedor de consulta efectivo; conservar uno de CaseAuthorizationPort y misma identidad institucional; no activar lector M6/publicación |
| R-01 regresión POST | Creación válida, denegada, UUID duplicado y concurrencia | Conservar 15 unitarias/9 IT actuales como selección de regresión; ejecutarlas nuevamente sobre el SHA futuro |
| R-02 regresión rutas | onlyPostIsMappedAndPublicationHasNoBean | Hoy debe permanecer intacta. Tras autorizar GET, adaptar exclusivamente la expectativa obsoleta de ausencia de GET y conservar ausencia de PATCH/publicación; prueba explícita del mapping GET nuevo |

Reutilizar arneses existentes de ContextoAcademicoProducerReadIT, CasoJpaAggregateReadIT/TransactionIT y M3CasoCreationIT sin sustituir M1 por un mock en la aceptación integrada ni añadir defaults permisivos. Las unitarias seguirán usando dobles para probar decisiones de forma aislada. MockMvc no se presentará como ejecución HTTP externa; si se exige cliente real, diseñar una prueba con servidor y puerto de test separado.

Nombres futuros sugeridos: ConsultarCasoServiceTest, CasoConsultaResponseTest, CasoQueryControllerTest, M3CasoQueryIT; sujetos a aprobación. No se crearon clases ni se alteraron pruebas históricas durante esta preparación.

## 10. Decisiones requeridas y dictamen

Dirección/Arquitectura deben ratificar explícitamente:

1. Fuente/versionado de ARQ-008.3 y conformidad de la ruta de consulta ya documentada.
2. Consumidor inicial y autorización de lectura expresable con política/capacidades existentes; no un nombre de permiso inventado. Propiedad sola y CREAR_CASO no bastan.
3. Acceso de propietario, ajeno y estudiante; excepciones, si las hubiera, y condiciones de rol.
4. Estados visibles, activo y flags de hijos; tratamiento de Archivado sin nuevas transiciones.
5. DTO exacto: campos, reserva, nulidad, orden y formato de estado/UUID; ficha básica o contenido.
6. Política de inexistente/no visible/sin permiso y contratos de errores. Ratificar 403/404 sin decisión unilateral del desarrollador.
7. Puerto local de autorización de consulta y ensamblaje APP coordinado con Cristian, conservando contratos M1 y M3 aprobados.
8. Alcance de aceptación y autorización posterior para implementar y publicar. No autorizar implícitamente PATCH ni M6.

**Dictamen: BLOQUEADO PARA IMPLEMENTACIÓN**, por G-01–G-06; preparación contractual completada y técnicamente fundamentada. La persistencia y la identidad son reutilizables y GET no necesita resolver DD-03 si mantiene fuera la semántica evaluativa de rúbrica. Sin política/DTO/errores ratificados, no existe todavía una implementación GET segura que pueda habilitarse por cuenta del equipo técnico.

No se creó endpoint, contrato Java, prueba nueva, commit funcional, push ni PR. Fuera de alcance: PATCH, publicación académica, copia persistida, Archivado, M6, cambios M1, migraciones V001–V009, tablas, fórmulas y reglas nuevas. Se mantienen cuatro capas, M1–M7 y patrones aprobados.
