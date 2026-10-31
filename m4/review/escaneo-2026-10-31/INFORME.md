# PRISALY M4 — Escaneo de errores y pendientes

Fecha: 31 de octubre de 2026. Revisión del árbol local, incluidos los cambios aún no publicados.
No se corrigió código productivo, no se modificó M1/M5, no se hicieron commits, push ni cambios en GitHub. Se generó únicamente un diagnóstico temporal bajo target y este informe.

## Resultado

Hay errores reproducibles adicionales a los bloqueos conocidos. La última regresión guardada registra 93 pruebas aprobadas; no se reejecutó completa en este escaneo. Se ejecutó un diagnóstico adicional con PostgreSQL real y MockMvc, usando los dobles M1/M3/M5/M7 existentes exclusivamente en el contexto de pruebas. No equivale a integración oficial entre módulos.

## Hallazgos ordenados por prioridad

### Crítica — Escrituras y consultas trabajan con dos modelos de datos distintos

SesionAcademicaEntity:24 y ConvocatoriaEntity:15 siguen apuntando a sesiones_academicas/convocatorias. El puerto de consulta para M5 lee "Sesion"/"Convocatoria" oficiales. Por tanto, una sesión creada por el servicio M4 actual no es el mismo registro que ve el nuevo puerto M5.

La evidencia anterior de Bd0052BaselineIT confirmó 21 tablas con V001–V009 y 23 al añadir V010. Las tablas propuestas no activan los triggers oficiales RF-09/F3-03. Este escaneo revisó que los mapeos incompatibles siguen presentes; no volvió a aplicar la línea base durante el diagnóstico.

Falta: confirmar historial de V010 y remapear las escrituras al esquema aprobado con el Integrante 1, incluyendo campos obligatorios, estados, timestamptz, relaciones y estrategia de concurrencia. No resolver renombrando V010 sin conocer su aplicación compartida.

### Alta — Reasignar caso no solicita F3-03 para el destino

SesionAcademicaService:142–162 comprueba publicación e historial y guarda el nuevo caso, pero no llama solicitarBloqueoAcademico para el destino. La solicitud aparece únicamente al crear sesión, en la línea 90. Las tablas propuestas tampoco tienen el trigger oficial de reasignación.

Reproducción: crear sesión con caso A y reasignarla al caso B publicado. Resultado del doble M3: bloqueo solicitado para A=true, para B=false, con reasignación persistida.

Esto demuestra ausencia de llamada al contrato, no una edición real de M3. Falta implementar la coordinación transaccional con el mecanismo F3-03 aprobado y agregar la regresión de reasignación. Repetir una llamada fuera de transacción no basta.

### Alta — Falta vincular identidad autenticada y autorización por recurso

SesionController recibe X-Docente-Id en las escrituras. No comprueba que ese UUID corresponda al principal autenticado. Las consultas/listados y consulta de convocatorias no verifican rol, propiedad ni estudiante en el servicio.

HttpApiConfig:40 exige autenticación global; eso no implementa permisos por sesión. No se afirma que las rutas sean anónimas. Al conectar un proveedor M1 real, hay que impedir que un usuario elija la identidad de otro docente o consulte recursos ajenos solo por conocer un UUID.

Los fallbacks actuales sí deniegan escrituras sin M1; no deben retirarse antes de conectar un proveedor que use identidad confiable. Falta seguridad HTTP por recurso y pruebas de dos usuarios/roles, sin implementar el acceso estudiantil M1 dentro de M4.

### Alta — La ruta HTTP de acceso continúa usando el booleano legado

SesionController:147–153 sigue publicando el método @Deprecated verificarAccesoSesion. Reproducción: sesión abierta con inicio dentro de dos días y estudiante convocado devuelve true hoy.

Ese método no comprueba tiempo, retiro, publicación actual ni repetición. El nuevo puerto es una consulta factual y no reemplaza automáticamente la ruta. Falta retirar o cerrar explícitamente el uso autorizante de ese booleano y aprobar la política real de acceso con M5/Dirección Central.

### Media — Entradas HTTP inválidas se convierten en 500

M4ExceptionHandler:55 captura Exception de forma general y responde 500. No distingue errores de conversión, cabeceras obligatorias ni JSON ilegible.

Reproducción MockMvc: GET /m4/sesiones/no-es-uuid → 500; POST /m4/sesiones sin X-Docente-Id → 500. Son errores de petición que deben tener respuesta 4xx apropiada. La prueba es del controlador/advice aislado, no del filtro de seguridad de app.

Falta manejar excepciones de petición de Spring y agregar cobertura HTTP para UUID, cabeceras, JSON inválido y enums desconocidos.

### Media — Duplicados en actualización terminan como error interno

crearSesion comprueba duplicación, pero actualizarSesion no la comprueba excluyendo la propia sesión. El índice único evita corromper la BD, pero DataIntegrityViolationException queda sin traducción específica y llega al manejador 500.

Reproducción: crear dos sesiones del mismo caso/docente en rangos diferentes y cambiar la segunda al rango de la primera. PostgreSQL rechaza con la excepción de integridad y el advice produce 500.

Falta responder 409 para el conflicto conocido, manteniendo la restricción para proteger concurrencia. No convertir indiscriminadamente toda excepción SQL en duplicado. Revisar también creación concurrente, convocatorias y conflictos optimistas en la API.

### Media — El servicio devuelve una versión previa al commit

SesionRepositoryAdapter:34–35 usa save y convierte inmediatamente la entidad en un objeto de dominio. Hibernate incrementa @Version al hacer flush; la copia devuelta ya fue construida.

Reproducción: tras cambiarCaso, versión devuelta=0 y versión leída después del commit=1. La prueba existente de actualización verifica version en SQL, pero no su correspondencia con el resultado del servicio.

SesionResponse actualmente NO expone version, por lo que no se afirma que la API REST esté devolviendo ese campo incorrecto. Sí afecta al contrato de dominio devuelto a consumidores internos. Además, no existe versión esperada/ETag de cliente para detectar formularios desactualizados; el bloqueo JPA protege transacciones simultáneas, no toda edición sobre datos antiguos.

Falta definir el contrato de concurrencia y devolver datos consistentes tras persistencia. La decisión final debe considerar que "Sesion" oficial no contiene la columna version propuesta.

### Media — CORS no permite la cabecera exigida por M4

HttpApiConfig:49 permite Authorization, Content-Type y Accept, pero las escrituras M4 exigen X-Docente-Id. En un frontend de otro origen, el preflight no autorizará esa cabecera con esta configuración.

Hallazgo por inspección de configuración; no se ejecutó un navegador ni un preflight de la app completa. La solución preferible debe acompañar la vinculación con el principal autenticado, evitando perpetuar un encabezado de identidad controlado por el cliente.

## Funcionalidad pendiente, distinta de los errores anteriores

| Pendiente | Situación actual | Qué falta |
| --- | --- | --- |
| Proveedor M1 | Deniega por defecto, correctamente | Adaptador real y principal autenticado |
| M3 | Ausencia genera error seguro | Publicación persistida y F3-03 atómico |
| Historial M5 | Puerto requerido; ausencia bloquea cambios | Proveedor público real y coordinación de locks |
| M7 | Sin proveedor falla la convocatoria | Notificación real compatible con commit/rollback, sin efectos externos previos a commit |
| Inicio/reanudación M5 | Puerto factual, sin permiso académico definitivo | Ratificar estados, ventana, fuente de tiempo, vencimiento y repetición |
| Activación de consulta oficial | Apagada por defecto, correctamente | Activación controlada solo con esquema/contratos válidos |
| Concurrencia entre módulos | Existen pruebas de duplicación y JPA local | Carreras convocatoria/cierre, alta participación/modificación, publicación/programación |
| Flujo de convocatorias | Creación y consulta; confirmar existe solo en dominio | Acordar si M4 debe exponer confirmación, retiro y cambios de estadoAutorizacion oficial |
| Validación oficial | PostgreSQL local desechable | Base aislada oficial y flujo real M1–M3–M4–M5 |
| Revisión y publicación | Cambios locales sin publicar | Autorización posterior del usuario, revisiones y PR en borrador |

## Orden recomendado de trabajo

1. Corregir los errores locales de HTTP, traducción de conflictos y coherencia de resultados, con pruebas dirigidas.
2. Resolver identidad confiable y autorización por recurso; cerrar el uso autorizante del acceso legado.
3. Acordar con Integrante 1 el esquema y tratamiento de V010; unificar escritura y consulta en las tablas aprobadas.
4. Conectar M3/M5/M7 y demostrar atomicidad y concurrencia sin dobles.
5. Ratificar decisiones académicas y ejecutar la validación oficial antes de solicitar integración.

La corrección concreta de versión y F3-03 debe alinearse con la estrategia de persistencia aprobada, evitando consolidar las tablas propuestas como solución definitiva.

## Evidencia

evidence/probe-results.txt conserva las líneas del diagnóstico ejecutado en este escaneo. El programa temporal se generó en m4/target/scan/M4ScanProbe.java y su salida completa en m4-scan-probe.log (archivos de trabajo ignorados por Git). No se añadieron pruebas a src/test ni se alteraron los resultados históricos de 93 pruebas.
