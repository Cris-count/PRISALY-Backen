# M3 — Continuación REST: GET y PATCH aprobados, seguridad pendiente

## Alcance y estado comprobado

La instrucción de Dirección Técnica 3.0 comunicada por Integrante 2 aprueba las
rutas POST /api/v1/casos, GET /api/v1/casos/{idCaso} y PATCH
/api/v1/casos/{idCaso}. Esto actualiza el alcance de rutas; no establece por sí
solo permisos institucionales, visibilidad, DTO ni códigos HTTP adicionales.

Inspección directa, sin Git, Maven, Docker, servidores ni pruebas:

- CasoApplicationService.createDraft llama buildDraft, canCreateCase con el
  docente del borrador e insert; no usa canModifyCase para crear.
- CasoCreationController usa CasoCurrentUserPort y no recibe identidad acreditada
  desde JSON. Confirma su transacción antes de retornar 201.
- app/M3CaseAuthorizationAdapter implementa los dos puertos locales de identidad
  y autorización mediante IdentityAccessPort de M1. Comprueba identidad activa,
  coincidencia del UUID autenticado con userId/docenteId y CREAR_CASO.
- app/M3CasoCreationConfig declara explícitamente el adaptador y el servicio.
  El registro existe en código. No se vuelve a certificar aquí el arranque del
  contexto ni se extrapolan pruebas históricas a esta inspección.
- canModifyCase y canPublishCase retornan false en el proveedor productivo.
  CaseAuthorizationPort no tiene capacidad específica de consulta.
- La presentación M3 inspeccionada contiene únicamente el controlador POST y su
  manejador de errores; no se encontraron métodos GET/PATCH.
- CasoUpdatePort ya ofrece findById(UUID) y update(UUID, String,
  List<CasoUpdateCommand>), con huella y comandos parciales. Es infraestructura
  interna, no una frontera HTTP autorizada.

## Bloqueos precisos

| Punto | Definición necesaria | Motivo |
| --- | --- | --- |
| Lectura | Permiso real o política institucional explícita y alcance por propietario | No existe canReadCase/canViewCase en el contrato actual; no reutilizar CREAR_CASO ni inferir autorización de posesión |
| Modificación | Política real para canModifyCase y estados editables | El proveedor actual deniega; aprobar la ruta no autoriza a cambiar esa denegación |
| Propiedad | Casos propios, ajenos y posibles excepciones | No asumir que igualdad de propietario basta o que todo personal puede leer respuestas correctas |
| No visibilidad | Tratamiento contractual de inexistente frente a no autorizado | Evitar inventar 403/404 o revelar la existencia del caso |
| PATCH HTTP | DTO, discriminadores de comandos, ubicación de m3-v2 y respuesta | El contrato Java no define por sí mismo JSON ni If-Match ni códigos de conflicto |
| GET HTTP | Proyección autorizada de datos, metadatos y huella | El agregado incluye respuestas correctas y datos de rúbrica; no exponer el record completo por defecto |
| Ensamblaje | Autorización para el cambio exacto en app | El puente real M1–M3 está en app, fuera del alcance de escritura actual |

No se encontraron códigos institucionales de lectura/modificación en las fuentes
M1/config inspeccionadas. Esto no prueba que no puedan existir en una base de
datos; su existencia y significado requieren confirmación institucional.

## Continuación técnica cuando se ratifiquen los puntos anteriores

1. Incorporar en M3 la capacidad de consulta que corresponda al contrato aprobado,
   sin dependencia directa de M1 ni proveedor permisivo.
2. Crear servicios de aplicación protegidos para consulta y actualización. Aplicar
   identidad, permiso y propiedad según la política, sin usar CREAR_CASO.
3. Reutilizar CasoUpdatePort y sus comandos, manteniendo m3-v2, pertenencia,
   SELECT FOR UPDATE, F3-03, rollback y ausencia de eliminaciones implícitas.
   El puerto update(Caso) permanece deshabilitado.
4. Encapsular DTO HTTP y errores según la resolución; no serializar Caso/JPA
   directamente ni interpretar ausencia como fallo de persistencia o viceversa.
5. Solicitar el cambio autorizado del proveedor/configuración en app. No activar
   CasoPublicationAdapter ni ampliar permisos de publicación.

Antes de implementar PATCH debe concretarse dónde ocurre su autorización efectiva
respecto del bloqueo y la escritura. Una lectura previa con REQUIRES_NEW no debe
presentarse como una comprobación atómica de propiedad/estado durante el UPDATE.

## Matriz de pruebas a preparar tras conciliación

| Grupo | Escenarios | Estado |
| --- | --- | --- |
| POST | Trece escenarios existentes | Colección conservada; resultados históricos Bruno/Newman no modificados |
| GET | Consulta permitida, no autenticado, sin permiso, ajeno, UUID inválido, inexistente/no visible | Pendiente de política, proyección y HTTP aprobados |
| PATCH | Cambio permitido, omisiones, null explícito, eliminación explícita existente, campos inválidos | Pendiente de DTO, política y HTTP aprobados |
| PATCH seguridad | No autenticado, sin permiso, ajeno, UUID inválido, caso inexistente | Pendiente de política y HTTP aprobados |
| PATCH concurrencia | Huella vigente/obsoleta/malformada, un ganador concurrente, rollback y F3-03 | Cobertura interna existente; frontera HTTP pendiente |

La colección Postman conserva carpetas GET/PATCH sin solicitudes ejecutables.
Sus descripciones ahora reconocen las rutas aprobadas e identifican los bloqueos
restantes. No se inventan HTTP esperados ni respuestas guardadas.

No se implementó código productivo nuevo ni se modificó app/M1. Este registro no
declara completados GET/PATCH ni ejecutadas pruebas nuevas. Publicación y DD-03
son pendientes independientes y no se amplían en esta continuación REST.

## Conciliación posterior con las fuentes solicitadas

Se revisaron nuevamente DC-M3-REST-02, las referencias GET/PATCH y de seguridad
de DC-M3-REST-03, los DTO de presentación existentes y los contratos/fuentes M1.
La respuesta posterior de Dirección Técnica mantiene las tres rutas aprobadas,
la identidad M1 y la prohibición de acceso ajeno sin autorización explícita;
no proporciona nuevos códigos de permiso, cuerpos ni respuestas HTTP.

| Fuente real | Qué define | Qué no permite concluir |
| --- | --- | --- |
| DC-M3-REST-02 | Antecedente histórico de creación y CREAR_CASO | Sus referencias antiguas a canModifyCase para crear no son el contrato actual ni una política de edición |
| DC-M3-REST-03 | DTO y errores del POST; proveedor de creación; denegación explícita de modificación/publicación | Documenta GET y la política 404/visibilidad como bloqueados; no ratifica JSON PATCH ni permiso de consulta |
| CrearCasoRequest / CrearCasoResponse | Entrada de creación y salida idCaso/estado | No son DTO de consulta ni de actualización parcial; reutilizarlos como PATCH alteraría el contrato |
| IdentityAccessPort | currentUser, requireUser, hasPermission y hasRole | Aceptar un String como permissionCode no aprueba un nombre ni una política de permisos |
| PersonalAuthorizationController | Constante real CREAR_CASO y comprobación de ese permiso | No declara capacidades de consulta o modificación de casos |
| CasoUpdatePort / CasoUpdateCommand | Actualización interna con siete comandos y precondición | No define JSON HTTP, If-Match ni correspondencia de errores de dominio a HTTP |

Conclusión: con estas fuentes no hay una parte productiva nueva de GET/PATCH
que pueda habilitarse de forma completa y segura. La comprobación de propiedad
es necesaria, pero no sustituye el permiso institucional que falta definir.
No se usa canModifyCase como autorización de lectura ni CREAR_CASO como permiso
de actualización. No se amplía ningún contrato M1.

Para desbloquear GET se requiere ratificar política de lectura, proyección y
tratamiento de inexistencia/no visibilidad. Para PATCH se requiere ratificar la
política de modificación ya representada por canModifyCase, el JSON de comandos,
el transporte de huella y la respuesta de éxito/error. No necesariamente hacen
falta métodos nuevos en M1: podría bastar su hasPermission existente, una vez
aprobados los códigos y su semántica, más el ensamblaje autorizado en app.
No se elige por cuenta propia esa solución ni se modifica el proveedor actual.

No se añaden controladores que fallen por un bean ausente, solicitudes que
interpreten un 404 del router como autorización ni casos positivos artificiales.
Las trece solicitudes de creación, su ejecución Newman y las evidencias previas
permanecen sin cambios. Esta conciliación no ejecuta pruebas nuevas.
